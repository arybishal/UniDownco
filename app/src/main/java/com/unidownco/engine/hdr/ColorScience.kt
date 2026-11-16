package com.unidownco.engine.hdr

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Mathematically rigorous color science foundation for SDR-to-HDR10 conversion,
 * color gamut transformation, inverse tone mapping, and SMPTE ST 2084 PQ encoding.
 * Grounded in ITU-R BT.709-6, ITU-R BT.2020-2, ITU-R BT.2100-2, and ITU-R BT.2408-7.
 */
object ColorScience {

    // -------------------------------------------------------------------------
    // SMPTE ST 2084 / ITU-R BT.2100 PQ Constants
    // -------------------------------------------------------------------------
    const val PQ_M1 = 2610.0 / 16384.0          // 0.1593017578125
    const val PQ_M2 = (2523.0 / 4096.0) * 128.0 // 78.84375
    const val PQ_C1 = 3424.0 / 4096.0           // 0.8359375
    const val PQ_C2 = (2413.0 / 4096.0) * 32.0  // 18.8515625
    const val PQ_C3 = (2392.0 / 4096.0) * 32.0  // 18.6875

    const val REFERENCE_PEAK_LUMINANCE = 10000.0 // 10,000 cd/m2 (nits)
    const val SDR_WHITE_NITS = 100.0             // Standard SDR reference diffuse white
    const val HDR_DIFFUSE_WHITE_NITS = 203.0     // ITU-R BT.2408 HDR diffuse white reference
    const val DEFAULT_HDR_PEAK_NITS = 1000.0     // Target mastering peak luminance

    // -------------------------------------------------------------------------
    // Linearization (Inverse OETF / EOTF)
    // -------------------------------------------------------------------------

    /**
     * Linearizes Rec.709 non-linear gamma-encoded value [0.0, 1.0] to linear light.
     * Conforms to ITU-R BT.709-6 inverse OETF.
     */
    fun linearizeRec709(v: Double): Double {
        val clamped = v.coerceIn(0.0, 1.0)
        return if (clamped < 0.081) {
            clamped / 4.5
        } else {
            ((clamped + 0.099) / 1.099).pow(1.0 / 0.45)
        }
    }

    /**
     * Applies Rec.709 OETF to encode linear light [0.0, 1.0] into non-linear signal.
     */
    fun encodeRec709(l: Double): Double {
        val clamped = l.coerceIn(0.0, 1.0)
        return if (clamped < 0.018) {
            clamped * 4.5
        } else {
            1.099 * clamped.pow(0.45) - 0.099
        }
    }

    /**
     * Linearizes sRGB gamma-encoded value [0.0, 1.0] to linear light (IEC 61966-2-1).
     */
    fun linearizeSrgb(v: Double): Double {
        val clamped = v.coerceIn(0.0, 1.0)
        return if (clamped <= 0.04045) {
            clamped / 12.92
        } else {
            ((clamped + 0.055) / 1.055).pow(2.4)
        }
    }

    /**
     * Applies sRGB EOTF to encode linear light into sRGB non-linear display signal.
     */
    fun encodeSrgb(l: Double): Double {
        val clamped = l.coerceIn(0.0, 1.0)
        return if (clamped <= 0.0031308) {
            clamped * 12.92
        } else {
            1.055 * clamped.pow(1.0 / 2.4) - 0.055
        }
    }

    // -------------------------------------------------------------------------
    // Color Gamut Matrix Transformation (Linear Rec.709 <-> Linear Rec.2020)
    // -------------------------------------------------------------------------

    data class RgbTriplet(val r: Double, val g: Double, val b: Double)

    /**
     * Converts linear Rec.709 RGB to linear Rec.2020 RGB via CIE XYZ D65 alignment.
     * ITU-R BT.2020 Section 1.2 Table 4.
     */
    fun rec709ToRec2020(rgb: RgbTriplet): RgbTriplet {
        val r2020 = 0.627404 * rgb.r + 0.329282 * rgb.g + 0.043314 * rgb.b
        val g2020 = 0.069097 * rgb.r + 0.919540 * rgb.g + 0.011362 * rgb.b
        val b2020 = 0.016391 * rgb.r + 0.088013 * rgb.g + 0.895595 * rgb.b
        return RgbTriplet(r2020, g2020, b2020)
    }

    /**
     * Converts linear Rec.2020 RGB to linear Rec.709 RGB (Inverse transformation).
     */
    fun rec2020ToRec709(rgb: RgbTriplet): RgbTriplet {
        val r709 = 1.660491 * rgb.r - 0.587641 * rgb.g - 0.072850 * rgb.b
        val g709 = -0.124550 * rgb.r + 1.132899 * rgb.g - 0.008349 * rgb.b
        val b709 = -0.018151 * rgb.r - 0.100579 * rgb.g + 1.118730 * rgb.b
        return RgbTriplet(r709, g709, b709)
    }

    // -------------------------------------------------------------------------
    // Luminance Calculation
    // -------------------------------------------------------------------------

    /**
     * Calculates luminance Y in Rec.2020 space (ITU-R BT.2020 / BT.2100).
     */
    fun luminanceRec2020(rgb: RgbTriplet): Double {
        return 0.262700 * rgb.r + 0.677998 * rgb.g + 0.059302 * rgb.b
    }

    /**
     * Calculates luminance Y in Rec.709 space (ITU-R BT.709).
     */
    fun luminanceRec709(rgb: RgbTriplet): Double {
        return 0.212600 * rgb.r + 0.715200 * rgb.g + 0.072200 * rgb.b
    }

    // -------------------------------------------------------------------------
    // Inverse Tone Mapping (ITM) - ITU-R BT.2408 Compliant
    // -------------------------------------------------------------------------

    /**
     * Maps SDR linear light [0.0, 1.0] (where 1.0 = 100 nits) into HDR linear luminance in cd/m2 (nits).
     *
     * Principles:
     * - Preserves shadows and midtones linearly: diffuse white (100 nits SDR) maps to 203 nits (BT.2408 HDR diffuse white).
     * - Applies an adaptive hyperbolic tangent knee curve for values above [kneeThreshold] (default 0.70 / 70 nits),
     *   smoothly expanding specular highlights up to [targetPeakNits] without highlight clipping or hue shifting.
     *
     * @param rgb Linear Rec.2020 color triplet in SDR range [0.0, 1.0]
     * @param targetPeakNits Target peak display mastering luminance (e.g. 1000.0 nits)
     * @param kneeThreshold Normalized luminance knee point where expansion begins (default 0.70)
     * @return HDR linear light color triplet scaled in absolute cd/m2 (nits)
     */
    fun inverseToneMapBt2408(
        rgb: RgbTriplet,
        targetPeakNits: Double = DEFAULT_HDR_PEAK_NITS,
        kneeThreshold: Double = 0.70
    ): RgbTriplet {
        val yIn = luminanceRec2020(rgb).coerceAtLeast(0.0)
        if (yIn <= 1e-6) {
            return RgbTriplet(0.0, 0.0, 0.0)
        }

        val diffuseWhiteScale = HDR_DIFFUSE_WHITE_NITS / SDR_WHITE_NITS // 2.03
        val peakScale = targetPeakNits / SDR_WHITE_NITS                 // e.g. 10.0 for 1000 nits

        val yExpanded = if (yIn <= kneeThreshold) {
            // Linear scaling preserving SDR artistic intent and contrast
            yIn * diffuseWhiteScale
        } else {
            // Smooth hyperbolic tangent highlight shoulder
            val kneeLinear = kneeThreshold * diffuseWhiteScale
            val headroom = peakScale - kneeLinear
            val t = (yIn - kneeThreshold) / (1.0 - kneeThreshold)
            // Normalized smooth roll-off reaching near targetPeak at t = 1.0
            val curve = tanh(1.8 * t) / tanh(1.8)
            kneeLinear + headroom * curve
        }

        // Convert normalized expanded luminance to absolute nits
        val targetLumaNits = yExpanded * SDR_WHITE_NITS
        val gain = targetLumaNits / max(yIn, 1e-6)

        return RgbTriplet(
            r = rgb.r * gain,
            g = rgb.g * gain,
            b = rgb.b * gain
        )
    }

    // -------------------------------------------------------------------------
    // SMPTE ST 2084 PQ Transfer Function (ITU-R BT.2100)
    // -------------------------------------------------------------------------

    /**
     * Encodes absolute luminance L [cd/m2 or nits] into non-linear PQ signal N in [0.0, 1.0].
     * Clamped to [0.0, 10000.0] nits per specification.
     */
    fun encodePq(luminanceNits: Double): Double {
        val yNorm = (luminanceNits / REFERENCE_PEAK_LUMINANCE).coerceIn(0.0, 1.0)
        if (yNorm <= 0.0) return 0.0

        val ym1 = yNorm.pow(PQ_M1)
        val num = PQ_C1 + PQ_C2 * ym1
        val den = 1.0 + PQ_C3 * ym1
        return (num / den).pow(PQ_M2).coerceIn(0.0, 1.0)
    }

    /**
     * Decodes non-linear PQ signal N in [0.0, 1.0] back to absolute luminance in cd/m2 (nits).
     */
    fun decodePq(n: Double): Double {
        val clampedN = n.coerceIn(0.0, 1.0)
        if (clampedN <= 0.0) return 0.0

        val nInvM2 = clampedN.pow(1.0 / PQ_M2)
        val num = max(nInvM2 - PQ_C1, 0.0)
        val den = PQ_C2 - PQ_C3 * nInvM2
        if (den <= 0.0) return REFERENCE_PEAK_LUMINANCE

        val yNorm = (num / den).pow(1.0 / PQ_M1)
        return (yNorm * REFERENCE_PEAK_LUMINANCE).coerceIn(0.0, REFERENCE_PEAK_LUMINANCE)
    }

    /**
     * Encodes absolute HDR linear RGB triplet (in nits) to PQ non-linear [0.0, 1.0] triplet.
     */
    fun encodePqRgb(hdrNitsRgb: RgbTriplet): RgbTriplet {
        return RgbTriplet(
            r = encodePq(hdrNitsRgb.r),
            g = encodePq(hdrNitsRgb.g),
            b = encodePq(hdrNitsRgb.b)
        )
    }

    // -------------------------------------------------------------------------
    // 10-Bit Quantization
    // -------------------------------------------------------------------------

    /**
     * Quantizes non-linear normalized float [0.0, 1.0] to a 10-bit integer [0, 1023].
     */
    fun quantize10Bit(v: Double): Int {
        return (v.coerceIn(0.0, 1.0) * 1023.0).roundToInt().coerceIn(0, 1023)
    }

    /**
     * Dequantizes a 10-bit integer [0, 1023] back to normalized float [0.0, 1.0].
     */
    fun dequantize10Bit(code: Int): Double {
        return code.coerceIn(0, 1023).toDouble() / 1023.0
    }

    // -------------------------------------------------------------------------
    // End-to-End CPU Pipeline Reference (Useful for unit testing & numerical validation)
    // -------------------------------------------------------------------------

    /**
     * Transforms an 8-bit non-linear Rec.709 input RGB triplet ([0, 255])
     * into a genuine 10-bit ST 2084 PQ Rec.2020 quantized triplet ([0, 1023]).
     */
    fun convertSdrToHdr10(
        sdr8R: Int,
        sdr8G: Int,
        sdr8B: Int,
        targetPeakNits: Double = DEFAULT_HDR_PEAK_NITS
    ): Triple<Int, Int, Int> {
        // 1. Normalize [0, 255] -> [0.0, 1.0]
        val normR = sdr8R.coerceIn(0, 255) / 255.0
        val normG = sdr8G.coerceIn(0, 255) / 255.0
        val normB = sdr8B.coerceIn(0, 255) / 255.0

        // 2. Linearize Rec.709
        val lin709 = RgbTriplet(
            linearizeRec709(normR),
            linearizeRec709(normG),
            linearizeRec709(normB)
        )

        // 3. Convert Rec.709 Gamut to Rec.2020 Gamut
        val lin2020 = rec709ToRec2020(lin709)

        // 4. Inverse Tone Mapping (BT.2408 compliant)
        val hdrNits = inverseToneMapBt2408(lin2020, targetPeakNits = targetPeakNits)

        // 5. Apply SMPTE ST 2084 PQ curve
        val pqRgb = encodePqRgb(hdrNits)

        // 6. 10-Bit Quantization
        return Triple(
            quantize10Bit(pqRgb.r),
            quantize10Bit(pqRgb.g),
            quantize10Bit(pqRgb.b)
        )
    }
}
