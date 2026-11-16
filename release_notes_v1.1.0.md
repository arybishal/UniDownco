# UniDownco v1.1.0 — Media Inspector UI, Bitstream Tooling, HDR Enhancement & Dynamic Color

UniDownco v1.1.0 represents a major milestone in the evolution of the application from a high-performance downloader into a complete media downloading, inspection, and cinematic HDR processing studio.

---

## 🌟 What's New in v1.1.0

### 🔬 1. Media Inspector & Deep Bitstream Inspection Tooling (Phase 2)
- **Dedicated 5-Tab Navigation:** Added a permanent `INSPECTOR` tab to the primary bottom navigation bar, adhering strictly to `DESIGN.md`.
- **System SAF Document Picker:** Native Storage Access Framework file chooser (`ActivityResultContracts.OpenDocument`) allowing users to inspect any video from external storage, SD cards, or downloads.
- **Comprehensive 8-Card Inspection UI:**
  - **Overview & Identity Card:** File size, duration, container format, and deep video/audio stream technical summary.
  - **HDR Classification Card:** 7-state bitstream classification badge (`GENUINE_HDR10`, `GENUINE_HDR10_PLUS`, `HLG`, `DOLBY_VISION`, `SDR_BT709`, `FAKE_HDR_TRANSCODE`, `MALFORMED_BITSTREAM`) with color transfer, primaries, matrix, bit depth, and transfer characteristics.
  - **Optical Mastering Display Volume Card:** Visual SMPTE ST 2086 chromaticity triangle primaries (G, B, R), D65 white point, max/min luminance, and optical gamut coverage.
  - **Content Light Level Card (CLLI):** MaxCLL and MaxFALL readouts with explicit origin provenance (`SOURCE_DECLARED`, `USER_CONFIGURED`, `MATHEMATICALLY_ESTIMATED`, `MEASURED`, `UNAVAILABLE`) and strict standards compliance indicators.
  - **Device Hardware Compatibility Card:** Probes device decoder/encoder pipelines and honestly reports physical display/SoC capabilities without fabrication.
  - **Timing & Frame Cadence Card:** Real-time PTS analysis reporting average FPS, cadence regularity, and VFR detection ($CV \le 0.08$ threshold).
  - **Audio Multi-Track Inspection Card:** Sampling rate, channel configuration, codec MIME, and bit depth across all embedded audio streams.
  - **Interactive ISOBMFF MP4 Box Tree:** Visual hierarchical browser of container atoms (`moov`, `trak`, `mdia`, `minf`, `stbl`, `stsd`, `hvc1`, `colr`, `mdcv`, `clli`) with raw byte hex previews and payload decoders.
- **HEVC Bitstream Engine:** Raw Annex B NAL unit parser extracting SPS, PPS, and Prefix SEI Payload Type 137 (MDCV) and Payload Type 144 (CLLI).

### 🎨 2. HDR Enhancement, Tunable Brightness & Dynamic Color (Stage 2.3.1.2)
- **Configurable HDR Output Peak Brightness:** User-tunable target display peak from 100 to 4000 nits (default: 1000 nits). Directly shapes the ITU-R BT.2408 inverse tone mapping shoulder in the GPU fragment shader while locking diffuse white at 203 nits.
- **One-Touch HDR Mastering Presets:** Instant selection between `Standard HDR10 (1000n)`, `HDR10 Bright (1500n)`, `HDR10 High Brightness (2000n)`, and `HDR10 Ultra (4000n)` plus quick-select peak chips (1000n, 1200n, 1500n, 2000n, 2500n, 3000n, 4000n).
- **MaxCLL (Content Light Level) Metadata Control:** Configurable 0 to 4000 nits slider and direct numeric readout; tracks metadata origin as `MetadataProvenance.USER_CONFIGURED` without fabrication.
- **MaxFALL Compliance Enforcement:** Configurable 100 to 1468 nits slider with real-time compliance validation. Enforces the mandatory standard rule: `MaxFALL <= MaxCLL` when MaxCLL is defined. Displays instant crimson alert banner when violated, preventing illegal bitstream authoring.
- **Mastering Display Peak:** Configurable 0 to 4000 nits slider (0 = unspecified), decoupling SMPTE ST 2086 optical volume boundaries from rendering tone curves.
- **Dynamic Color Vibrance Engine:**
  - **Strict Mathematical Hue Invariance:** Saturation is scaled as an affine expansion of chromatic differences $(R - Y, G - Y, B - Y)$ around luminance $Y$, guaranteeing zero hue rotation ($\Delta\theta < 0.0001$ rad).
  - **Adaptive Skin-Tone Protection:** Evaluates $C_b = (B - Y) / 1.8814$ and $C_r = (R - Y) / 1.4746$. When human complexion is detected ($C_r > 0, C_b < 0, C_r / -C_b \approx 1.4$), the saturation boost is damped by up to 80% to prevent sunburned or unnatural facial complexions.
  - **Shadow & Highlight Tapering:** Smoothstep roll-off in deep shadows ($Y < 0.10$) and specular highlights ($Y > 0.80$) prevents noise amplification and highlight clipping.
  - **Soft-Knee Gamut Headroom:** Compresses boost on already-vibrant pixels to prevent out-of-gamut clipping.
  - **Hardware OpenGL ES 3.0 Fragment Shader:** Integrated directly into `SdrToHdr10Shader.kt` and `HdrRenderCore.kt` for zero-copy GPU acceleration during transcoding.
  - **Live Chromatic Preview Simulation:** 4-swatch simulation card demonstrating protected skin complexion vs expanded foliage and azure sky.
- **Settings Persistence:** Completely decoupled from Room database schema via Android `SharedPreferences` (Room database remains strictly on version 3 with zero migrations).

### ⚙️ 3. Silicon Hardware Honesty & Diagnostics
- Full diagnostic transparency for Qualcomm SM6225 (`bengal`) hardware: explicitly reports 8-bit Main hardware encoding capability and gates Main10 hardware encoding to prevent fake or corrupt HDR files.

---

## 🛠️ Verification & Test Summary

- **Automated Host Unit Tests:** **161 / 161 PASSED (100%)** across 26 test classes:
  - `HdrEnhancementConfigTest`: 7/7 PASSED
  - `DynamicColorTest`: 6/6 PASSED
  - `SettingsViewModelHdrTest`: 4/4 PASSED
  - `HdrClassificationEngineTest`: 10/10 PASSED
  - `MediaInspectorEngineTest`: 6/6 PASSED
  - `MediaInspectionDomainModelTest`: 8/8 PASSED
  - `Mp4HierarchyAndExtendedParserTest`: 7/7 PASSED
  - `MediaInspectorViewModelTest`: 12/12 PASSED
  - `DeviceCompatibilityEvaluatorTest`: 7/7 PASSED
- **Physical Device Instrumentation Tests:** **PASSED (100%)** on Xiaomi Redmi Note 14 4G (`25062RN2DA`, Qualcomm SM6225 / bengal, Android 16 / API 36).
- **Target OS Compatibility:** Android 8.0 (API 26) through Android 16 (API 36).

---

## 📦 Release Assets & SHA-256 Checksums

All release APKs are compiled with R8 minification, resource optimization, and signed with the **APK Signature Scheme v2**.

| Architecture | Package File | File Size | SHA-256 Checksum |
|---|---|---|---|
| **ARM64 (Recommended)** | `app-arm64-v8a-release.apk` | 53.19 MB (55,769,521 B) | `45B4E7EC4F043FBB2FAAA6CDD4660F3218D7F1476E98A6368968A67B4567C1BD` |
| **Universal (All CPUs)** | `app-universal-release.apk` | 145.36 MB (152,418,201 B) | `0F2A0C973DE01F0B9A337D4D305053F44181ECC573268518C2EC84698AC28057` |
| **ARMv7 (32-bit)** | `app-armeabi-v7a-release.apk` | 46.84 MB (49,115,793 B) | `7025DBC986EC6A8E2179231C5099944766EBD3EE2BFC3B1B774C0C4C07F6B7C5` |
| **x86_64 (Emulators/PC)** | `app-x86_64-release.apk` | 56.04 MB (58,761,205 B) | `787178F8BAB41D0AE52FDD651CB971A41A2AEC66B17ED49F93A53F9CA5E8E50F` |
