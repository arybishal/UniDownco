package com.unidownco

import android.content.Context
import android.media.MediaFormat
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unidownco.engine.hardware.HardwareCapabilityRegistry
import com.unidownco.engine.hdr.ColorScience
import com.unidownco.engine.hdr.HdrEnhancementConfig
import com.unidownco.engine.hdr.HdrPreset
import com.unidownco.engine.hdr.MetadataProvenance
import com.unidownco.feature.settings.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HdrEnhancementDeviceTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testDevice_HardwareCapabilityProbeAndHonestLimitation() {
        val caps = HardwareCapabilityRegistry.getCapabilities()
        val model = Build.MODEL

        // On Xiaomi Redmi Note 14 4G (SM6225 / bengal)
        if (model.contains("25062RN2DA")) {
            assertTrue(caps.deviceModel.contains("25062RN2DA"))
            assertFalse(caps.supportsHardwareMain10) // Truthful declaration: Main 8-bit only
        }

        assertTrue(caps.preferredEncoderName.isNotEmpty())
    }

    @Test
    fun testDevice_HdrEnhancementConfigValidationAndStaticMetadata() {
        val config = HdrEnhancementConfig(
            targetPeakNits = 1500.0,
            maxCll = 1500,
            maxFall = 600,
            masteringDisplayPeakNits = 1500.0,
            dynamicColorEnabled = true,
            dynamicColorIntensity = 0.7f,
            provenance = MetadataProvenance.USER_CONFIGURED
        )

        assertTrue(config.isValid)

        val staticMeta = config.toHdrStaticMetadata()
        val ctaBuffer = staticMeta.toByteBuffer()
        assertEquals(25, ctaBuffer.remaining())

        // Verify Android MediaFormat compatibility on device ART runtime
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080)
        format.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, ctaBuffer)
        val retrievedBuffer = format.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO)
        assertNotNull(retrievedBuffer)
        assertEquals(25, retrievedBuffer!!.remaining())
    }

    @Test
    fun testDevice_DynamicColorReferenceInAndroidRuntime() {
        val sdrR = 210
        val sdrG = 160
        val sdrB = 110

        val config = HdrPreset.HDR10_BRIGHT.toConfig(
            dynamicColorEnabled = true,
            dynamicColorIntensity = 0.5f
        )

        val (r10, g10, b10) = ColorScience.convertSdrToHdr10(sdrR, sdrG, sdrB, config)

        assertTrue(r10 in 0..1023)
        assertTrue(g10 in 0..1023)
        assertTrue(b10 in 0..1023)
        assertTrue(r10 > g10)
        assertTrue(g10 > b10)
    }

    @Test
    fun testDevice_SharedPreferencesPersistence() {
        val prefs = context.getSharedPreferences(SettingsViewModel.PREFS_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putFloat(SettingsViewModel.KEY_HDR_TARGET_PEAK_NITS, 2000f)
            .putInt(SettingsViewModel.KEY_HDR_MAX_CLL, 2000)
            .putInt(SettingsViewModel.KEY_HDR_MAX_FALL, 800)
            .putFloat(SettingsViewModel.KEY_HDR_MASTERING_PEAK_NITS, 2000f)
            .putBoolean(SettingsViewModel.KEY_HDR_DYNAMIC_COLOR_ENABLED, true)
            .putFloat(SettingsViewModel.KEY_HDR_DYNAMIC_COLOR_INTENSITY, 0.75f)
            .commit()

        val targetPeak = prefs.getFloat(SettingsViewModel.KEY_HDR_TARGET_PEAK_NITS, 1000f)
        val maxCll = prefs.getInt(SettingsViewModel.KEY_HDR_MAX_CLL, 1000)
        val maxFall = prefs.getInt(SettingsViewModel.KEY_HDR_MAX_FALL, 400)
        val masteringPeak = prefs.getFloat(SettingsViewModel.KEY_HDR_MASTERING_PEAK_NITS, 1000f)
        val dynamicColorEnabled = prefs.getBoolean(SettingsViewModel.KEY_HDR_DYNAMIC_COLOR_ENABLED, false)
        val dynamicColorIntensity = prefs.getFloat(SettingsViewModel.KEY_HDR_DYNAMIC_COLOR_INTENSITY, 0.5f)

        assertEquals(2000f, targetPeak, 0.001f)
        assertEquals(2000, maxCll)
        assertEquals(800, maxFall)
        assertEquals(2000f, masteringPeak, 0.001f)
        assertTrue(dynamicColorEnabled)
        assertEquals(0.75f, dynamicColorIntensity, 0.001f)

        val restoredConfig = HdrEnhancementConfig(
            targetPeakNits = targetPeak.toDouble(),
            maxCll = maxCll,
            maxFall = maxFall,
            masteringDisplayPeakNits = masteringPeak.toDouble(),
            dynamicColorEnabled = dynamicColorEnabled,
            dynamicColorIntensity = dynamicColorIntensity
        )
        assertTrue(restoredConfig.isValid)
    }
}
