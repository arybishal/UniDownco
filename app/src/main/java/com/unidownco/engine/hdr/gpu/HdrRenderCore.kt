package com.unidownco.engine.hdr.gpu

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.view.Surface
import com.unidownco.core.logging.UniLog
import com.unidownco.engine.hdr.TargetHdrType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * GPU Render Core managing OpenGL ES 3.0 / EGL surface pipeline.
 * Sits between [MediaCodecVideoDecoder] and [MediaCodecVideoEncoder] to perform
 * zero-copy hardware-accelerated SDR-to-HDR10 conversion and 10-bit surface presentation.
 */
class HdrRenderCore(
    private val outputSurface: Surface,
    private val width: Int,
    private val height: Int,
    private val targetHdrType: TargetHdrType = TargetHdrType.SDR_TO_HDR10,
    private val targetPeakNits: Double = 1000.0,
    private val kneeThreshold: Double = 0.70,
    private val prefer10Bit: Boolean = true
) {
    private val tag = "HdrRenderCore"

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglConfig: EGLConfig? = null

    var isActually10BitSurface: Boolean = false
        private set

    private var textureId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    var decoderInputSurface: Surface? = null
        private set

    private var programId: Int = 0
    private var uSTMatrixHandle: Int = -1
    private var uHdrModeHandle: Int = -1
    private var uTargetPeakNitsHandle: Int = -1
    private var uKneeThresholdHandle: Int = -1

    private val stMatrix = FloatArray(16)
    private var vertexBuffer: FloatBuffer? = null
    private var isReleased = false

    init {
        initEGL()
        initGL()
    }

    private fun initEGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw IllegalStateException("Unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw IllegalStateException("Unable to initialize EGL14")
        }

        // Try 10-bit EGLConfig if requested
        var selectedConfig: EGLConfig? = null
        if (prefer10Bit) {
            val attribs10 = intArrayOf(
                EGL14.EGL_RED_SIZE, 10,
                EGL14.EGL_GREEN_SIZE, 10,
                EGL14.EGL_BLUE_SIZE, 10,
                EGL14.EGL_ALPHA_SIZE, 2,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGLExt.EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (EGL14.eglChooseConfig(eglDisplay, attribs10, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
                selectedConfig = configs[0]
                isActually10BitSurface = true
                UniLog.i(tag, "Successfully acquired 10-bit EGLConfig (RGBA_1010102)")
            } else {
                UniLog.w(tag, "10-bit EGLConfig unavailable on this GPU driver, falling back to 8-bit RGBA_8888")
            }
        }

        // Fallback to standard 8-bit RGBA_8888 config
        if (selectedConfig == null) {
            val attribs8 = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGLExt.EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribs8, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
                throw IllegalStateException("Unable to find suitable EGLConfig for video encoding")
            }
            selectedConfig = configs[0]
            isActually10BitSurface = false
        }

        eglConfig = selectedConfig

        // Create EGLContext with GLES 3.0 (or 2.0 fallback)
        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, if (isActually10BitSurface) 3 else 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, selectedConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw IllegalStateException("Failed to create EGL context")
        }

        // Create window surface wrapping encoder input surface
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, selectedConfig, outputSurface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("Failed to create EGL window surface from encoder input surface")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw IllegalStateException("eglMakeCurrent failed")
        }
    }

    private fun initGL() {
        // Full screen quad: position (x, y) and texture coordinates (u, v)
        val quadVertices = floatArrayOf(
            // x, y, u, v
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 1.0f, 1.0f
        )
        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quadVertices)
                position(0)
            }

        // Compile Shader Program
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, SdrToHdr10Shader.VERTEX_SHADER)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, SdrToHdr10Shader.FRAGMENT_SHADER)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertexShader)
        GLES30.glAttachShader(programId, fragmentShader)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            throw IllegalStateException("Failed linking HDR shader program: $log")
        }

        uSTMatrixHandle = GLES30.glGetUniformLocation(programId, "uSTMatrix")
        uHdrModeHandle = GLES30.glGetUniformLocation(programId, "uHdrMode")
        uTargetPeakNitsHandle = GLES30.glGetUniformLocation(programId, "uTargetPeakNits")
        uKneeThresholdHandle = GLES30.glGetUniformLocation(programId, "uKneeThreshold")

        // Create OES External Texture
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // Create SurfaceTexture and Decoder Input Surface
        val st = SurfaceTexture(textureId)
        st.setDefaultBufferSize(width, height)
        surfaceTexture = st
        decoderInputSurface = Surface(st)

        GLES30.glViewport(0, 0, width, height)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("Could not compile shader $type: $log")
        }
        return shader
    }

    /**
     * Renders decoded frame from [surfaceTexture] with HDR conversion,
     * tags with [presentationTimeNs], and swaps to the encoder's input surface.
     */
    fun renderFrame(presentationTimeNs: Long) {
        if (isReleased) return

        val st = surfaceTexture ?: return
        st.updateTexImage()
        st.getTransformMatrix(stMatrix)

        GLES30.glUseProgram(programId)

        // Bind OES texture to unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        // Set uniforms
        GLES30.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)

        val hdrMode = when (targetHdrType) {
            TargetHdrType.SDR_TO_HDR10 -> 1
            TargetHdrType.HDR10_PRESERVE -> 2
            else -> 0
        }
        GLES30.glUniform1i(uHdrModeHandle, hdrMode)
        GLES30.glUniform1f(uTargetPeakNitsHandle, targetPeakNits.toFloat())
        GLES30.glUniform1f(uKneeThresholdHandle, kneeThreshold.toFloat())

        // Set vertex attributes
        vertexBuffer?.let { buf ->
            buf.position(0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, buf)

            buf.position(2)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, buf)
        }

        // Draw quad
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)

        // Stamp presentation timestamp and swap buffer to encoder surface
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun release() {
        if (isReleased) return
        isReleased = true

        try { decoderInputSurface?.release() } catch (_: Exception) {}
        try { surfaceTexture?.release() } catch (_: Exception) {}

        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
            }
            EGL14.eglTerminate(eglDisplay)
        }

        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        decoderInputSurface = null
        surfaceTexture = null
    }
}
