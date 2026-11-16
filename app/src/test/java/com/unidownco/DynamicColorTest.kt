package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.engine.hdr.ColorScience
import com.unidownco.engine.hdr.HdrEnhancementConfig
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2

class DynamicColorTest {

    @Test
    fun testDynamicColorDisabled_ReturnsExactIdentity() {
        val testColors = listOf(
            ColorScience.RgbTriplet(0.2, 0.4, 0.6),
            ColorScience.RgbTriplet(0.8, 0.3, 0.2),
            ColorScience.RgbTriplet(0.1, 0.7, 0.2),
            ColorScience.RgbTriplet(0.0, 0.0, 0.0),
            ColorScience.RgbTriplet(1.0, 1.0, 1.0)
        )

        for (color in testColors) {
            val result = ColorScience.applyDynamicColor(color, enabled = false, intensity = 1.0f)
            assertThat(result.r).isEqualTo(color.r)
            assertThat(result.g).isEqualTo(color.g)
            assertThat(result.b).isEqualTo(color.b)
        }
    }

    @Test
    fun testDynamicColorZeroIntensity_ReturnsExactIdentity() {
        val color = ColorScience.RgbTriplet(0.65, 0.40, 0.25)
        val result = ColorScience.applyDynamicColor(color, enabled = true, intensity = 0.0f)
        assertThat(result.r).isEqualTo(color.r)
        assertThat(result.g).isEqualTo(color.g)
        assertThat(result.b).isEqualTo(color.b)
    }

    @Test
    fun testStrictHuePreservation_ChromaticAngleInvariant() {
        val testColors = listOf(
            ColorScience.RgbTriplet(0.80, 0.20, 0.15), // Crimson Red
            ColorScience.RgbTriplet(0.15, 0.70, 0.25), // Forest Green
            ColorScience.RgbTriplet(0.10, 0.40, 0.85), // Azure Blue
            ColorScience.RgbTriplet(0.70, 0.65, 0.15)  // Golden Yellow
        )

        for (color in testColors) {
            val yIn = ColorScience.luminanceRec2020(color)
            val cbBefore = (color.b - yIn) / 1.8814
            val crBefore = (color.r - yIn) / 1.4746
            val angleBefore = atan2(crBefore, cbBefore)

            val boosted = ColorScience.applyDynamicColor(color, enabled = true, intensity = 0.8f)
            val yOut = ColorScience.luminanceRec2020(boosted)
            val cbAfter = (boosted.b - yOut) / 1.8814
            val crAfter = (boosted.r - yOut) / 1.4746
            val angleAfter = atan2(crAfter, cbAfter)

            // Strict hue angle invariance: tolerance < 1e-4 radians
            assertThat(abs(angleBefore - angleAfter)).isLessThan(0.0001)
        }
    }

    @Test
    fun testSkinToneProtection_DampensSaturationBoost() {
        // Classic natural skin tone fixture in linear Rec.2020 (Cr > 0, Cb < 0, Cr / -Cb ~ 1.4)
        val skinTone = ColorScience.RgbTriplet(0.65, 0.48, 0.38)
        val ySkin = ColorScience.luminanceRec2020(skinTone)
        val skinDetectionConfidence = ColorScience.detectSkinTone(skinTone.r, skinTone.g, skinTone.b, ySkin)
        assertThat(skinDetectionConfidence).isGreaterThan(0.50) // Successfully identified as skin

        // Foliage / Nature fixture with similar luminance
        val foliage = ColorScience.RgbTriplet(0.25, 0.60, 0.22)
        val yFoliage = ColorScience.luminanceRec2020(foliage)
        val foliageDetectionConfidence = ColorScience.detectSkinTone(foliage.r, foliage.g, foliage.b, yFoliage)
        assertThat(foliageDetectionConfidence).isLessThan(0.01) // Not skin

        // Apply Dynamic Color at 80% intensity
        val boostedSkin = ColorScience.applyDynamicColor(skinTone, enabled = true, intensity = 0.8f)
        val boostedFoliage = ColorScience.applyDynamicColor(foliage, enabled = true, intensity = 0.8f)

        // Calculate relative saturation increase: delta / original
        val skinDeltaR = abs(boostedSkin.r - skinTone.r)
        val foliageDeltaG = abs(boostedFoliage.g - foliage.g)

        // Skin saturation boost must be significantly damped compared to foliage
        assertThat(skinDeltaR).isLessThan(foliageDeltaG * 0.5)
    }

    @Test
    fun testShadowProtection_TapersBoostInLowLuma() {
        val shadowColor = ColorScience.RgbTriplet(0.015, 0.025, 0.010) // Low light shadow
        val boosted = ColorScience.applyDynamicColor(shadowColor, enabled = true, intensity = 1.0f)

        // Should have negligible change to avoid amplifying shadow sensor noise
        assertThat(abs(boosted.g - shadowColor.g)).isLessThan(0.005)
    }

    @Test
    fun testHighlightProtection_TapersBoostNearSpecularPeak() {
        val highlightColor = ColorScience.RgbTriplet(0.95, 0.92, 0.88) // High specular shoulder
        val boosted = ColorScience.applyDynamicColor(highlightColor, enabled = true, intensity = 1.0f)

        // Highlight colors must not explode or clip past 1.0
        assertThat(boosted.r).isAtMost(1.0)
        assertThat(boosted.g).isAtMost(1.0)
        assertThat(boosted.b).isAtMost(1.0)
        assertThat(abs(boosted.r - highlightColor.r)).isLessThan(0.02)
    }

    @Test
    fun testEndToEndSdrToHdr10_WithConfigAndDynamicColor() {
        val config = HdrEnhancementConfig(
            targetPeakNits = 1500.0,
            maxCll = 1500,
            maxFall = 600,
            masteringDisplayPeakNits = 1500.0,
            dynamicColorEnabled = true,
            dynamicColorIntensity = 0.6f
        )

        // Test with standard 8-bit SDR input (sRGB 200, 150, 100)
        val (hdr10R, hdr10G, hdr10B) = ColorScience.convertSdrToHdr10(200, 150, 100, config)

        // Valid 10-bit quantized range [0, 1023]
        assertThat(hdr10R).isIn(0..1023)
        assertThat(hdr10G).isIn(0..1023)
        assertThat(hdr10B).isIn(0..1023)

        // Midtones are well within legal HDR10 range
        assertThat(hdr10R).isGreaterThan(100)
        assertThat(hdr10G).isGreaterThan(100)
        assertThat(hdr10B).isGreaterThan(50)
    }
}
