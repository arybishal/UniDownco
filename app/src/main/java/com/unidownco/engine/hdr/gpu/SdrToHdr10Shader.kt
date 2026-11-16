package com.unidownco.engine.hdr.gpu

/**
 * GLSL Shaders for GPU-accelerated SDR-to-HDR10 conversion.
 * Implements:
 * 1. OES External Texture Sampling
 * 2. Rec.709 Linearization (Inverse OETF)
 * 3. 3x3 Rec.709 -> Rec.2020 Color Gamut Transformation (D65 White Point)
 * 4. ITU-R BT.2408 Adaptive Knee Inverse Tone Mapping (ITM)
 * 5. SMPTE ST 2084 PQ Transfer Function Encoding
 */
object SdrToHdr10Shader {

    const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec4 aTextureCoord;

uniform mat4 uSTMatrix;
out vec2 vTextureCoord;

void main() {
    gl_Position = aPosition;
    vTextureCoord = (uSTMatrix * aTextureCoord).xy;
}
"""

    const val FRAGMENT_SHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;

in vec2 vTextureCoord;
out vec4 fragColor;

uniform samplerExternalOES sTexture;
uniform int uHdrMode;                 // 0 = SDR_PASSTHROUGH, 1 = SDR_TO_HDR10, 2 = HDR10_PASSTHROUGH
uniform float uTargetPeakNits;        // e.g. 1000.0 nits
uniform float uKneeThreshold;         // e.g. 0.70 (70 nits normalized)
uniform int uDynamicColorEnabled;     // 0 = disabled, 1 = enabled
uniform float uDynamicColorIntensity; // 0.0 to 1.0 (default 0.5)

// Rec.709 Linearization (Inverse OETF)
vec3 sdrToLinear(vec3 c) {
    vec3 low = c / 4.5;
    vec3 high = pow((c + vec3(0.099)) / 1.099, vec3(1.0 / 0.45));
    return mix(low, high, step(vec3(0.081), c));
}

// ITU-R BT.2020 3x3 Transformation Matrix from Rec.709 (CIE XYZ D65)
// Mat3 columns are vectors 0, 1, 2
const mat3 M_709_TO_2020 = mat3(
    0.627404, 0.069097, 0.016391, // Column 0 (Red coefficients)
    0.329282, 0.919540, 0.088013, // Column 1 (Green coefficients)
    0.043314, 0.011362, 0.895595  // Column 2 (Blue coefficients)
);

// SMPTE ST 2084 PQ Constants
const float PQ_M1 = 0.1593017578125;
const float PQ_M2 = 78.84375;
const float PQ_C1 = 0.8359375;
const float PQ_C2 = 18.8515625;
const float PQ_C3 = 18.6875;

vec3 encodePq(vec3 nits) {
    vec3 yNorm = clamp(nits / 10000.0, 0.0, 1.0);
    vec3 ym1 = pow(yNorm, vec3(PQ_M1));
    vec3 num = vec3(PQ_C1) + vec3(PQ_C2) * ym1;
    vec3 den = vec3(1.0) + vec3(PQ_C3) * ym1;
    return pow(num / den, vec3(PQ_M2));
}

void main() {
    vec4 rawColor = texture(sTexture, vTextureCoord);
    vec3 rgb = clamp(rawColor.rgb, 0.0, 1.0);

    if (uHdrMode == 1) {
        // -------------------------------------------------------------
        // MODE 1: GENUINE SDR-TO-HDR10 CONVERSION
        // -------------------------------------------------------------
        // Step 1: Linearize Rec.709
        vec3 lin709 = sdrToLinear(rgb);

        // Step 2: Gamut expansion (Rec.709 -> Rec.2020)
        vec3 lin2020 = M_709_TO_2020 * lin709;

        // Step 3: Dynamic Color Adaptive Vibrance (Hue-preserving, skin-tone protected)
        vec3 colorReady = lin2020;
        if (uDynamicColorEnabled == 1 && uDynamicColorIntensity > 0.0) {
            float yNorm = dot(lin2020, vec3(0.262700, 0.677998, 0.059302));
            if (yNorm > 0.000001) {
                float cb = (lin2020.b - yNorm) / 1.8814;
                float cr = (lin2020.r - yNorm) / 1.4746;
                float chroma = sqrt(cb * cb + cr * cr);
                float skinWeight = 0.0;
                if (chroma > 0.005 && cr > 0.0 && cb < 0.0) {
                    float ratio = cr / max(-cb, 0.0001);
                    float ratioDiff = abs(ratio - 1.4);
                    skinWeight = clamp(1.0 - ratioDiff * 0.8, 0.0, 1.0);
                }

                float lumaWeight = smoothstep(0.01, 0.10, yNorm) * (1.0 - smoothstep(0.80, 0.98, yNorm));
                float maxC = max(lin2020.r, max(lin2020.g, lin2020.b));
                float minC = min(lin2020.r, min(lin2020.g, lin2020.b));
                float currentSat = (maxC - minC) / max(yNorm, 0.01);
                float satHeadroom = clamp(1.0 - currentSat * 0.5, 0.2, 1.0);

                float boostFactor = 1.0 + (clamp(uDynamicColorIntensity, 0.0, 1.0) * 0.5) * lumaWeight * satHeadroom * (1.0 - 0.80 * skinWeight);
                colorReady = max(vec3(0.0), vec3(yNorm) + (lin2020 - vec3(yNorm)) * boostFactor);
            }
        }

        // Step 4: ITU-R BT.2408 Inverse Tone Mapping
        float yIn = dot(colorReady, vec3(0.262700, 0.677998, 0.059302));
        if (yIn > 0.000001) {
            float diffScale = 2.03; // 203 nits / 100 nits
            float peakScale = uTargetPeakNits / 100.0;
            float yExp;
            if (yIn <= uKneeThreshold) {
                yExp = yIn * diffScale;
            } else {
                float kneeLin = uKneeThreshold * diffScale;
                float headroom = peakScale - kneeLin;
                float t = (yIn - uKneeThreshold) / (1.0 - uKneeThreshold);
                // Normalized hyperbolic tangent shoulder
                float curve = tanh(1.8 * t) / tanh(1.8);
                yExp = kneeLin + headroom * curve;
            }

            float targetNits = yExp * 100.0;
            float gain = targetNits / yIn;
            vec3 hdrNits = colorReady * gain;

            // Step 5: SMPTE ST 2084 PQ Non-linear Transfer Encoding
            vec3 pqEncoded = encodePq(hdrNits);
            fragColor = vec4(clamp(pqEncoded, 0.0, 1.0), 1.0);
        } else {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        }
    } else {
        // -------------------------------------------------------------
        // MODE 0 / 2: DIRECT PASSTHROUGH (SDR OR PRE-ENCODED HDR10)
        // -------------------------------------------------------------
        fragColor = vec4(rgb, rawColor.a);
    }
}
"""
}
