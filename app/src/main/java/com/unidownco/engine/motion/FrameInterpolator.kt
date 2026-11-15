package com.unidownco.engine.motion

import java.io.File

enum class MotionMode {
    NATIVE_FPS,
    FRAME_DUPLICATION,
    MOTION_INTERPOLATION
}

data class MotionResult(
    val processedFile: File,
    val outputFps: Int,
    val modeUsed: MotionMode
)

/**
 * Interface prepared for Step 3 60 FPS motion engine
 */
interface FrameInterpolator {
    suspend fun processMotion(
        sourceFile: File,
        targetFps: Int = 60,
        mode: MotionMode
    ): Result<MotionResult>
}
