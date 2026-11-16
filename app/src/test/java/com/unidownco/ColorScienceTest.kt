package com.unidownco

import com.unidownco.engine.hdr.ColorScience
import com.unidownco.engine.hdr.HdrStaticMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class ColorScienceTest {

    private val epsilon = 1e-4

    @Test
    fun testRec709LinearizationAndRoundtrip() {
        // Zero anchor
        assertEquals(0.0, ColorScience.linearizeRec709(0.0), epsilon)
        assertEquals(0.0, ColorScience.encodeRec709(0.0), epsilon)

        // Peak anchor
        assertEquals(1.0, ColorScience.linearizeRec709(1.0), epsilon)
        assertEquals(1.0, ColorScience.encodeRec709(1.0), epsilon)

        // Standard test points
        val testPoints = doubleArrayOf(0.005, 0.018, 0.05, 0.081, 0.18, 0.5, 0.75, 0.95, 1.0)
        for (v in testPoints) {
            val linear = ColorScience.linearizeRec709(v)
            assertTrue("Linear value must be non-negative", linear >= 0.0)
            assertTrue("Linear value must not exceed 1.0", linear <= 1.0)

            val roundtrip = ColorScience.encodeRec709(linear)
            assertEquals("Roundtrip failed for $v", v, roundtrip, 1e-3)
        }
    }

    @Test
    fun testSrgbLinearizationAndRoundtrip() {
        assertEquals(0.0, ColorScience.linearizeSrgb(0.0), epsilon)
        assertEquals(1.0, ColorScience.linearizeSrgb(1.0), epsilon)

        val testPoints = doubleArrayOf(0.01, 0.04045, 0.18, 0.5, 0.8, 1.0)
        for (v in testPoints) {
            val linear = ColorScience.linearizeSrgb(v)
            val roundtrip = ColorScience.encodeSrgb(linear)
            assertEquals("sRGB roundtrip failed for $v", v, roundtrip, 1e-3)
        }
    }

    @Test
    fun testRec709ToRec2020MatrixConversion() {
        // 1. D65 White point invariance: (1, 1, 1) in Rec.709 maps to (1, 1, 1) in Rec.2020
        val white709 = ColorScience.RgbTriplet(1.0, 1.0, 1.0)
        val white2020 = ColorScience.rec709ToRec2020(white709)
        assertEquals(1.0, white2020.r, epsilon)
        assertEquals(1.0, white2020.g, epsilon)
        assertEquals(1.0, white2020.b, epsilon)

        // 2. Black invariance
        val black709 = ColorScience.RgbTriplet(0.0, 0.0, 0.0)
        val black2020 = ColorScience.rec709ToRec2020(black709)
        assertEquals(0.0, black2020.r, epsilon)
        assertEquals(0.0, black2020.g, epsilon)
        assertEquals(0.0, black2020.b, epsilon)

        // 3. Pure Red (1, 0, 0)
        val red709 = ColorScience.RgbTriplet(1.0, 0.0, 0.0)
        val red2020 = ColorScience.rec709ToRec2020(red709)
        assertEquals(0.627404, red2020.r, epsilon)
        assertEquals(0.069097, red2020.g, epsilon)
        assertEquals(0.016391, red2020.b, epsilon)

        // 4. Pure Green (0, 1, 0)
        val green709 = ColorScience.RgbTriplet(0.0, 1.0, 0.0)
        val green2020 = ColorScience.rec709ToRec2020(green709)
        assertEquals(0.329282, green2020.r, epsilon)
        assertEquals(0.919540, green2020.g, epsilon)
        assertEquals(0.088013, green2020.b, epsilon)

        // 5. Pure Blue (0, 0, 1)
        val blue709 = ColorScience.RgbTriplet(0.0, 0.0, 1.0)
        val blue2020 = ColorScience.rec709ToRec2020(blue709)
        assertEquals(0.043314, blue2020.r, epsilon)
        assertEquals(0.011362, blue2020.g, epsilon)
        assertEquals(0.895595, blue2020.b, epsilon)

        // 6. Roundtrip conversion (709 -> 2020 -> 709)
        val testColors = listOf(
            ColorScience.RgbTriplet(0.5, 0.2, 0.8),
            ColorScience.RgbTriplet(0.9, 0.6, 0.1),
            ColorScience.RgbTriplet(0.18, 0.18, 0.18)
        )
        for (c in testColors) {
            val to2020 = ColorScience.rec709ToRec2020(c)
            val backTo709 = ColorScience.rec2020ToRec709(to2020)
            assertEquals("Roundtrip R failed", c.r, backTo709.r, epsilon)
            assertEquals("Roundtrip G failed", c.g, backTo709.g, epsilon)
            assertEquals("Roundtrip B failed", c.b, backTo709.b, epsilon)
        }
    }

    @Test
    fun testInverseToneMappingBt2408() {
        // Black remains black
        val black = ColorScience.RgbTriplet(0.0, 0.0, 0.0)
        val itmBlack = ColorScience.inverseToneMapBt2408(black, targetPeakNits = 1000.0)
        assertEquals(0.0, itmBlack.r, epsilon)
        assertEquals(0.0, itmBlack.g, epsilon)
        assertEquals(0.0, itmBlack.b, epsilon)

        // Midtone below knee (e.g. 0.5 linear) scales linearly by 2.03 (203 / 100)
        val midtone = ColorScience.RgbTriplet(0.5, 0.5, 0.5)
        val itmMidtone = ColorScience.inverseToneMapBt2408(midtone, targetPeakNits = 1000.0, kneeThreshold = 0.70)
        val expectedNits = 0.5 * 2.03 * 100.0 // 101.5 nits
        assertEquals(expectedNits, itmMidtone.r, 0.1)
        assertEquals(expectedNits, itmMidtone.g, 0.1)
        assertEquals(expectedNits, itmMidtone.b, 0.1)

        // Peak 1.0 expands smoothly to target peak luminance (~1000 nits)
        val peak = ColorScience.RgbTriplet(1.0, 1.0, 1.0)
        val itmPeak = ColorScience.inverseToneMapBt2408(peak, targetPeakNits = 1000.0, kneeThreshold = 0.70)
        assertTrue("Peak luminance must be close to target peak (got ${itmPeak.r})", itmPeak.r in 950.0..1050.0)
        assertEquals(itmPeak.r, itmPeak.g, epsilon)
        assertEquals(itmPeak.g, itmPeak.b, epsilon)
    }

    @Test
    fun testSmpteSt2084PqTransferFunction() {
        // Zero anchor
        assertEquals(0.0, ColorScience.encodePq(0.0), epsilon)
        assertEquals(0.0, ColorScience.decodePq(0.0), epsilon)

        // 10,000 nits peak anchor
        assertEquals(1.0, ColorScience.encodePq(10000.0), epsilon)
        assertEquals(10000.0, ColorScience.decodePq(1.0), 0.1)

        // 100 nits reference point (standard PQ reference: ~0.508078)
        val pq100 = ColorScience.encodePq(100.0)
        assertEquals(0.508078, pq100, 1e-3)
        assertEquals(100.0, ColorScience.decodePq(pq100), 0.05)

        // 203 nits HDR diffuse white reference (~0.580689)
        val pq203 = ColorScience.encodePq(203.0)
        assertEquals(0.580689, pq203, 1e-3)
        assertEquals(203.0, ColorScience.decodePq(pq203), 0.05)

        // 1,000 nits mastering peak (~0.751827)
        val pq1000 = ColorScience.encodePq(1000.0)
        assertEquals(0.751827, pq1000, 1e-3)
        assertEquals(1000.0, ColorScience.decodePq(pq1000), 0.1)

        // High-density roundtrip check across full dynamic range
        val luminanceLevels = doubleArrayOf(0.001, 0.01, 0.1, 1.0, 10.0, 50.0, 100.0, 203.0, 500.0, 1000.0, 4000.0, 10000.0)
        for (luma in luminanceLevels) {
            val encoded = ColorScience.encodePq(luma)
            val decoded = ColorScience.decodePq(encoded)
            val relativeError = kotlin.math.abs(decoded - luma) / luma
            assertTrue("Relative error too high for $luma nits (got $decoded, relError: $relativeError)", relativeError < 0.001)
        }
    }

    @Test
    fun test10BitQuantization() {
        assertEquals(0, ColorScience.quantize10Bit(0.0))
        assertEquals(1023, ColorScience.quantize10Bit(1.0))

        // Diffuse white (203 nits, PQ ~0.5807) -> ~594 in 10-bit code value
        val diffuseWhiteCode = ColorScience.quantize10Bit(ColorScience.encodePq(203.0))
        assertEquals(594, diffuseWhiteCode)

        // 1000 nits (PQ ~0.7518) -> ~769 in 10-bit code value
        val peak1000Code = ColorScience.quantize10Bit(ColorScience.encodePq(1000.0))
        assertEquals(769, peak1000Code)

        // Quantize / Dequantize roundtrip
        for (code in 0..1023) {
            val dequant = ColorScience.dequantize10Bit(code)
            val requant = ColorScience.quantize10Bit(dequant)
            assertEquals(code, requant)
        }
    }

    @Test
    fun testEndToEndSdrToHdr10Conversion() {
        // Black: (0, 0, 0) SDR -> (0, 0, 0) 10-bit HDR10
        val (blackR, blackG, blackB) = ColorScience.convertSdrToHdr10(0, 0, 0, targetPeakNits = 1000.0)
        assertEquals(0, blackR)
        assertEquals(0, blackG)
        assertEquals(0, blackB)

        // White: (255, 255, 255) SDR -> (769, 769, 769) 10-bit HDR10 at 1000 nits target
        val (whiteR, whiteG, whiteB) = ColorScience.convertSdrToHdr10(255, 255, 255, targetPeakNits = 1000.0)
        assertEquals(769, whiteR)
        assertEquals(769, whiteG)
        assertEquals(769, whiteB)

        // Pure red: (255, 0, 0)
        val (redR, redG, redB) = ColorScience.convertSdrToHdr10(255, 0, 0, targetPeakNits = 1000.0)
        assertTrue("Red channel must dominate in pure red", redR > redG && redR > redB)
        assertTrue("Red must be in valid 10-bit range", redR in 0..1023)
        assertTrue("Green must be in valid 10-bit range", redG in 0..1023)
        assertTrue("Blue must be in valid 10-bit range", redB in 0..1023)
    }

    @Test
    fun testHdrStaticMetadataSerialization() {
        val meta = HdrStaticMetadata.BT2020_1000NITS
        val buffer = meta.toByteBuffer()

        assertEquals(HdrStaticMetadata.CTA_BUFFER_SIZE, buffer.remaining())
        assertEquals(25, buffer.capacity())

        // Deserialization roundtrip
        val parsed = HdrStaticMetadata.fromByteBuffer(buffer)
        assertNotNull(parsed)
        assertEquals(meta.greenX, parsed!!.greenX)
        assertEquals(meta.greenY, parsed.greenY)
        assertEquals(meta.blueX, parsed.blueX)
        assertEquals(meta.blueY, parsed.blueY)
        assertEquals(meta.redX, parsed.redX)
        assertEquals(meta.redY, parsed.redY)
        assertEquals(meta.whitePointX, parsed.whitePointX)
        assertEquals(meta.whitePointY, parsed.whitePointY)
        assertEquals(meta.maxMasteringLuminanceNits, parsed.maxMasteringLuminanceNits)
        assertEquals(meta.minMasteringLuminanceTenThousandthsNits, parsed.minMasteringLuminanceTenThousandthsNits)
        assertEquals(meta.maxCllNits, parsed.maxCllNits)
        assertEquals(meta.maxFallNits, parsed.maxFallNits)

        // Corrupted buffer with < 25 bytes fails cleanly
        val invalidBuffer = ByteBuffer.allocate(10)
        assertNull(HdrStaticMetadata.fromByteBuffer(invalidBuffer))
    }
}
