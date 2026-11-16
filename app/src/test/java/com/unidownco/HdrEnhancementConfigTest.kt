package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.engine.hdr.HdrEnhancementConfig
import com.unidownco.engine.hdr.HdrPreset
import com.unidownco.engine.hdr.MetadataProvenance
import org.junit.Test
import java.nio.ByteOrder

class HdrEnhancementConfigTest {

    @Test
    fun testDefaultConfig_IsValid() {
        val config = HdrEnhancementConfig.DEFAULT
        val result = config.validate()
        assertThat(result.isValid).isTrue()
        assertThat(result.errorMessage).isNull()
        assertThat(config.targetPeakNits).isEqualTo(1000.0)
        assertThat(config.maxCll).isEqualTo(1000)
        assertThat(config.maxFall).isEqualTo(400)
        assertThat(config.masteringDisplayPeakNits).isEqualTo(1000.0)
        assertThat(config.dynamicColorEnabled).isFalse()
        assertThat(config.dynamicColorIntensity).isEqualTo(0.5f)
        assertThat(config.provenance).isEqualTo(MetadataProvenance.USER_CONFIGURED)
    }

    @Test
    fun testTargetPeakBrightness_Boundaries() {
        // Valid boundaries
        assertThat(HdrEnhancementConfig(targetPeakNits = 100.0).validate().isValid).isTrue()
        assertThat(HdrEnhancementConfig(targetPeakNits = 1000.0).validate().isValid).isTrue()
        assertThat(HdrEnhancementConfig(targetPeakNits = 1500.0).validate().isValid).isTrue()
        assertThat(HdrEnhancementConfig(targetPeakNits = 2000.0).validate().isValid).isTrue()
        assertThat(HdrEnhancementConfig(targetPeakNits = 4000.0).validate().isValid).isTrue()

        // Invalid boundaries
        val lowResult = HdrEnhancementConfig(targetPeakNits = 99.0).validate()
        assertThat(lowResult.isValid).isFalse()
        assertThat(lowResult.errorMessage).contains("between 100 and 4000 nits")

        val highResult = HdrEnhancementConfig(targetPeakNits = 4001.0).validate()
        assertThat(highResult.isValid).isFalse()
        assertThat(highResult.errorMessage).contains("between 100 and 4000 nits")
    }

    @Test
    fun testMaxCll_Boundaries() {
        // 0 = unspecified/unavailable
        assertThat(HdrEnhancementConfig(maxCll = 0, maxFall = 400).validate().isValid).isTrue()
        assertThat(HdrEnhancementConfig(maxCll = 1000, maxFall = 400).validate().isValid).isTrue()
        assertThat(HdrEnhancementConfig(maxCll = 4000, maxFall = 1000).validate().isValid).isTrue()

        val invalidLow = HdrEnhancementConfig(maxCll = -1).validate()
        assertThat(invalidLow.isValid).isFalse()

        val invalidHigh = HdrEnhancementConfig(maxCll = 4001).validate()
        assertThat(invalidHigh.isValid).isFalse()
    }

    @Test
    fun testMaxFall_Boundaries() {
        assertThat(HdrEnhancementConfig(maxCll = 2000, maxFall = 100).validate().isValid).isTrue()
        assertThat(HdrEnhancementConfig(maxCll = 2000, maxFall = 1468).validate().isValid).isTrue()

        val invalidLow = HdrEnhancementConfig(maxCll = 2000, maxFall = 99).validate()
        assertThat(invalidLow.isValid).isFalse()

        val invalidHigh = HdrEnhancementConfig(maxCll = 2000, maxFall = 1469).validate()
        assertThat(invalidHigh.isValid).isFalse()
    }

    @Test
    fun testMaxFallCannotExceedMaxCll_EnforcesCompliance() {
        // When maxFall <= maxCll -> Valid
        val equalResult = HdrEnhancementConfig(maxCll = 1000, maxFall = 1000).validate()
        assertThat(equalResult.isValid).isTrue()

        val lowerResult = HdrEnhancementConfig(maxCll = 1000, maxFall = 400).validate()
        assertThat(lowerResult.isValid).isTrue()

        // When maxFall > maxCll -> INVALID
        val invalidResult = HdrEnhancementConfig(maxCll = 1000, maxFall = 1200).validate()
        assertThat(invalidResult.isValid).isFalse()
        assertThat(invalidResult.errorMessage).contains("MaxFALL (1200 nits) cannot exceed MaxCLL (1000 nits)")
    }

    @Test
    fun testMasteringDisplayPeak_Boundaries() {
        // 0 = unspecified
        assertThat(HdrEnhancementConfig(masteringDisplayPeakNits = 0.0).validate().isValid).isTrue()
        assertThat(HdrEnhancementConfig(masteringDisplayPeakNits = 400.0).validate().isValid).isTrue()
        assertThat(HdrEnhancementConfig(masteringDisplayPeakNits = 1000.0).validate().isValid).isTrue()
        assertThat(HdrEnhancementConfig(masteringDisplayPeakNits = 4000.0).validate().isValid).isTrue()

        val invalidLow = HdrEnhancementConfig(masteringDisplayPeakNits = -1.0).validate()
        assertThat(invalidLow.isValid).isFalse()

        val invalidHigh = HdrEnhancementConfig(masteringDisplayPeakNits = 4001.0).validate()
        assertThat(invalidHigh.isValid).isFalse()
    }

    @Test
    fun testPresets_MapCorrectly() {
        val std = HdrPreset.STANDARD_HDR10.toConfig()
        assertThat(std.targetPeakNits).isEqualTo(1000.0)
        assertThat(std.maxCll).isEqualTo(1000)
        assertThat(std.maxFall).isEqualTo(400)
        assertThat(std.masteringDisplayPeakNits).isEqualTo(1000.0)
        assertThat(std.validate().isValid).isTrue()

        val bright = HdrPreset.HDR10_BRIGHT.toConfig()
        assertThat(bright.targetPeakNits).isEqualTo(1500.0)
        assertThat(bright.maxCll).isEqualTo(1500)
        assertThat(bright.maxFall).isEqualTo(600)
        assertThat(bright.masteringDisplayPeakNits).isEqualTo(1500.0)
        assertThat(bright.validate().isValid).isTrue()

        val highBright = HdrPreset.HDR10_HIGH_BRIGHTNESS.toConfig()
        assertThat(highBright.targetPeakNits).isEqualTo(2000.0)
        assertThat(highBright.maxCll).isEqualTo(2000)
        assertThat(highBright.maxFall).isEqualTo(800)
        assertThat(highBright.masteringDisplayPeakNits).isEqualTo(2000.0)
        assertThat(highBright.validate().isValid).isTrue()

        val ultra = HdrPreset.HDR10_ULTRA.toConfig()
        assertThat(ultra.targetPeakNits).isEqualTo(4000.0)
        assertThat(ultra.maxCll).isEqualTo(4000)
        assertThat(ultra.maxFall).isEqualTo(1000)
        assertThat(ultra.masteringDisplayPeakNits).isEqualTo(4000.0)
        assertThat(ultra.validate().isValid).isTrue()
    }

    @Test
    fun testToHdrStaticMetadata_Cta861BufferSerialization() {
        val config = HdrEnhancementConfig(
            targetPeakNits = 1500.0,
            maxCll = 1500,
            maxFall = 600,
            masteringDisplayPeakNits = 1500.0,
            provenance = MetadataProvenance.USER_CONFIGURED
        )

        val staticMeta = config.toHdrStaticMetadata()
        assertThat(staticMeta.maxMasteringLuminanceNits).isEqualTo(1500)
        assertThat(staticMeta.maxCllNits).isEqualTo(1500)
        assertThat(staticMeta.maxFallNits).isEqualTo(600)

        val buffer = staticMeta.toByteBuffer().order(ByteOrder.LITTLE_ENDIAN)
        assertThat(buffer.remaining()).isEqualTo(25)

        val descId = buffer.get().toInt()
        assertThat(descId).isEqualTo(0) // Type 1

        // Primaries
        val gx = buffer.short.toInt() and 0xFFFF
        val gy = buffer.short.toInt() and 0xFFFF
        val bx = buffer.short.toInt() and 0xFFFF
        val by = buffer.short.toInt() and 0xFFFF
        val rx = buffer.short.toInt() and 0xFFFF
        val ry = buffer.short.toInt() and 0xFFFF

        assertThat(gx).isEqualTo(staticMeta.greenX)
        assertThat(gy).isEqualTo(staticMeta.greenY)
        assertThat(bx).isEqualTo(staticMeta.blueX)
        assertThat(by).isEqualTo(staticMeta.blueY)
        assertThat(rx).isEqualTo(staticMeta.redX)
        assertThat(ry).isEqualTo(staticMeta.redY)

        // White Point
        val wx = buffer.short.toInt() and 0xFFFF
        val wy = buffer.short.toInt() and 0xFFFF
        assertThat(wx).isEqualTo(staticMeta.whitePointX)
        assertThat(wy).isEqualTo(staticMeta.whitePointY)

        // Luminance
        val maxL = buffer.short.toInt() and 0xFFFF
        val minL = buffer.short.toInt() and 0xFFFF
        assertThat(maxL).isEqualTo(staticMeta.maxMasteringLuminanceNits)
        assertThat(minL).isEqualTo(staticMeta.minMasteringLuminanceTenThousandthsNits)

        // Content Light Level
        val maxCll = buffer.short.toInt() and 0xFFFF
        val maxFall = buffer.short.toInt() and 0xFFFF
        assertThat(maxCll).isEqualTo(1500)
        assertThat(maxFall).isEqualTo(600)
    }

    @Test
    fun testToHdrStaticMetadata_UnspecifiedCllOmittedCorrectly() {
        val config = HdrEnhancementConfig(
            targetPeakNits = 1000.0,
            maxCll = 0,
            maxFall = 0,
            masteringDisplayPeakNits = 1000.0
        )

        val staticMeta = config.toHdrStaticMetadata()
        assertThat(staticMeta.contentLightLevel.isAvailable).isFalse()
        assertThat(staticMeta.toMp4ClliAtom()).isNull() // Enforces standard omission of clli box
        assertThat(staticMeta.contentLightLevel.toHevcSeiNalUnit()).isNull() // Enforces standard omission of SEI 144
    }
}
