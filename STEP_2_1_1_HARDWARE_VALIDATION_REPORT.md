# UNIDOWNCO STEP 2.1.1: HARDWARE CAPABILITY ACCURACY & PROCESSING PRESET VALIDATION REPORT

**Project:** UniDownco  
**Application ID:** `com.unidownco.debug`  
**Workspace:** `D:\Webapp\UniDownco`  
**Connected Test Device:** Xiaomi Redmi Note 25062RN2DA (`aab64c2c`), Android 16 (API 36), `SM6225` (bengal)  
**Future Target Device:** OnePlus Ace 5 / OnePlus 13R, Android 15/16, `SM8650-AB` (Snapdragon 8 Gen 3)  
**Status:** Verification Complete & Ready for Step 2.2 Approval  

---

## EXECUTIVE SUMMARY

Step 2.1.1 was conducted to audit, correct, and conclusively validate all hardware capability claims, HDR signaling classifications, processing preset availability indicators, and storage retention mechanisms before beginning the MediaCodec video encoding pipeline in Step 2.2.

All technical discrepancies identified in Step 2.1 have been resolved:
1. **HEVC Main10 Contradiction Resolved:** Conclusively proved via Android 16 Codec2 subsystem inspection (`dumpsys media.player`) that Qualcomm `SM6225` supports only HEVC Profile 1 (Main 8-bit) and Profile 4 (MainStill) in both hardware (`c2.qti.hevc.encoder`) and software (`c2.android.hevc.encoder`). Neither hardware nor software MediaCodec supports Main10 (10-bit) on this SoC. The previous claim of "10-bit software fallback available" has been eliminated and replaced with explicit, honest hardware capability reporting.
2. **Strict HDR Detection Implemented:** `VideoAnalyzerImpl.kt` now strictly adheres to CTA-861.3 and ITU-R BT.2100 specifications. HDR10 is assigned only when BT.2020 color primaries, SMPTE ST 2084 PQ transfer curve, bit depth $\ge 10$, and static mastering metadata (`KEY_HDR_STATIC_INFO`) are all present. Incomplete HDR metadata is classified as `PARTIAL_HDR`, missing color metadata defaults to `SDR` (implicit BT.709), and speculative Dolby Vision Profile 8.1 guesses have been removed.
3. **Transparent Preset Status Model:** `ProcessingPreset.kt` and `HomeScreen.kt` now dynamically reflect real hardware capabilities. On `SM6225`, `DIRECT` is `ACTIVE`, `HDR10_ENHANCED` is badged `8-BIT HW ONLY` with a clear explanation, and `ULTRA_HDR_60FPS` is badged `STEP 2.4`. The action button immediately communicates preset constraints (`DOWNLOAD 2160P • 8-BIT HW ONLY` vs `DOWNLOAD 2160P • ACTIVE`).
4. **Physical Storage Separation:** `StorageManager.kt` and `DownloadRepository.kt` now isolate original downloaded files into `Movies/originals/` while reserving `Movies/downloads/` for processed exports, complete with non-colliding file generation and user purge APIs.
5. **Database Migration Verified:** Room `MIGRATION_2_3` was verified against a real physical database extracted from the test device, confirming schema integrity, foreign key cascading, and non-destructive data retention.
6. **Automated Testing:** 47 unit tests executed with a **100% pass rate** (0 failures, 0 errors, 0 skipped).

---

## 1. CORRECTED HARDWARE CAPABILITY MATRIX

### 1.1 Physical Device vs Target Device Codec Matrix

| Component / Capability | Xiaomi Redmi Note (`SM6225`) [Verified] | OnePlus Ace 5 / 13R (`SM8650-AB`) [Target] |
| :--- | :--- | :--- |
| **SoC / Architecture** | Qualcomm Snapdragon 680/685 (bengal) • 4G | Qualcomm Snapdragon 8 Gen 3 • 5G |
| **GPU** | Adreno 610 | Adreno 750 |
| **Android Version / API** | Android 16 (API 36) | Android 15 / 16 (API 35/36) |
| **HW HEVC Encoder Name** | `c2.qti.hevc.encoder`, `c2.qti.hevc.encoder.cq` | `c2.qti.hevc.encoder` |
| **HW HEVC Profiles Supported**| Profile 1 (`Main`, 8-bit), Profile 4 (`MainStill`) | Profile 1 (`Main`), Profile 2 (`Main10`, 10-bit) |
| **HW HEVC Max Resolution** | 1920x1088 @ 30 FPS | 7680x4320 (8K30) / 3840x2160 (4K120) |
| **HW COLOR_FormatSurface (0x7f000789)** | Supported (Surface input for EGL/OpenGL) | Supported |
| **HW COLOR_FormatYUVP010 (54)** | **NOT SUPPORTED** (0x36 absent from capabilities) | **SUPPORTED** |
| **SW HEVC Encoder Name** | `c2.android.hevc.encoder` (Google Codec2 SW) | `c2.android.hevc.encoder` |
| **SW HEVC Profiles Supported**| Profile 1 (`Main`, 8-bit), Profile 4 (`MainStill`) | Profile 1 (`Main`, 8-bit) |
| **SW Main10 MediaCodec Support**| **NOT SUPPORTED** | **NOT SUPPORTED** (MediaCodec SW is 8-bit only) |
| **HW HEVC Decoder Name** | `c2.qti.hevc.decoder` | `c2.qti.hevc.decoder` |
| **HW HEVC Decoder Profiles** | Profile 1 (`Main`), Profile 4 (`MainStill`) | Profile 1, Profile 2 (`Main10`), Profile 4 |
| **FFmpeg `libx265.so`** | Packaged dynamic shared library (no CLI binary)| Packaged dynamic shared library |

### 1.2 Inspection Methodology & Evidence
The capabilities were verified directly against Android 16's Codec2 player service on the connected device:
```shell
adb -s aab64c2c shell dumpsys media.player
```
**Key Subsystem Findings:**
1. Encoder `c2.qti.hevc.encoder` reports `profileLevels`:
   - `0x1:0x1` (Profile Main, Level 1) up to `0x1:0x80000` (Profile Main, Level 5.1).
   - `0x4:0x1` (Profile MainStill) up to `0x4:0x80000`.
   - **Profile 2 (`HEVCProfileMain10`) is completely absent.**
2. Encoder color formats supported:
   - `0x7f000789` (`COLOR_FormatSurface`)
   - `0x7f420888` (`COLOR_FormatYUV420Flexible`)
   - `0x15` (`COLOR_FormatYUV420SemiPlanar`)
   - `0x7faac000` (QTI proprietary tiled YUV)
   - **`0x36` (`COLOR_FormatYUVP010`) is completely absent.**
3. Decoder `c2.qti.hevc.decoder`:
   - Reports only Profile 1 and Profile 4.
   - Max supported decoding resolution: 1920x1088.

---

## 2. SOFTWARE VS HARDWARE MAIN10 FINDINGS

### 2.1 The Origin of the Contradiction
In the preliminary Step 2.1 feasibility notes, it was hypothesized that if hardware HEVC Main10 was absent, a software encoder might provide a 10-bit fallback. However, detailed inspection of Android's MediaCodec architecture and the installed APK revealed:
1. **Google's built-in software encoder (`c2.android.hevc.encoder`):** Implements only the HEVC Main profile (8-bit YUV420Planar). It does not implement Main10 or accept 10-bit P010 buffers.
2. **Third-party software libraries (`libx265.so`):** While `libx265.so` is bundled in the native library directory (`no_backup/.../ffmpeg/usr/lib/libx265.so`), it is exposed strictly as a dynamic C library for FFmpeg native linking. There is no standalone `x265` CLI binary in `usr/bin/`, nor is there a MediaCodec wrapper around it.
3. Attempting to initialize `MediaCodec.createEncoderByType("video/hevc")` with `HEVCProfileMain10` on `SM6225` causes `MediaCodec.CodecException: ERROR_INVALID_PARAMETER` or silent down-profiling to Main 8-bit.

### 2.2 Corrections Applied to `HardwareCapabilityRegistry.kt`
The capability registry was corrected to present uncompromised technical honesty:
```kotlin
// Updated HardwareCapabilityRegistry.kt
val hwMain10 = checkHardwareMain10Support()
val swMain10 = checkSoftwareMain10Support() // Conclusively false on SM6225

val adaptationPolicy = if (hwMain10) {
    "Full 10-bit HDR hardware encoding available."
} else {
    "10-bit HDR hardware encoding is unsupported on this SoC ($board / $hardware). " +
    "Pipeline will adapt to 8-bit HEVC Main encoding or preserve direct stream pass-through."
}

val formattedReport = buildString {
    appendLine("=== HARDWARE CAPABILITY AUDIT ===")
    appendLine("Device: $manufacturer $model (Board: $board, Hardware: $hardware)")
    appendLine("HW HEVC Main (8-bit): ${if (hwMain) "SUPPORTED" else "UNSUPPORTED"}")
    appendLine("HW HEVC Main10 (10-bit): ${if (hwMain10) "SUPPORTED" else "UNSUPPORTED"}")
    appendLine("SW HEVC Main10 (MediaCodec): ${if (swMain10) "SUPPORTED" else "UNSUPPORTED"}")
    appendLine("Max HW Enc Res: ${maxRes.width}x${maxRes.height}")
    appendLine("Max HW Enc FPS: $maxFps")
    appendLine("Adaptation Policy: $adaptationPolicy")
}
```

---

## 3. HDR DETECTION ACCURACY & COMPLIANCE

### 3.1 Standards Grounding
The HDR detection engine in `VideoAnalyzerImpl.kt` was audited against:
- **CTA-861.3 / CTA-861-G:** Mastering Display Color Volume and Content Light Level metadata signaling.
- **ITU-R BT.2100-2:** Image parameter values for high dynamic range television for use in production and international programme exchange (PQ and HLG).
- **ITU-R BT.709-6:** Parameter values for the HDTV standards for production and international programme exchange.

### 3.2 Elimination of Speculative Classifications
1. **Dolby Vision Non-Speculation:** Dolby Vision cannot be assumed to be Profile 8.1. Profile 5 (IPTPQc2 with no backward compatibility) is the most common streaming profile. In Step 2.1.1, the speculative `dolbyVisionProfile = "Profile 8.1"` was completely eliminated. The analyzer now only reports a profile if explicitly identified in the track metadata (e.g. `dvh1`/`dvhe` fourcc), and sets `hasDolbyVisionRpu = false` unless verified via NAL bitstream inspection.
2. **Strict HDR10 Classification:** HDR10 is assigned if and only if all four conditions are met:
   - Color Primaries = BT.2020 (`COLOR_STANDARD_BT2020`)
   - Transfer Function = SMPTE ST 2084 PQ (`COLOR_TRANSFER_ST2084`)
   - Bit Depth $\ge 10$
   - Static Mastering Metadata present (`KEY_HDR_STATIC_INFO`)
3. **Partial HDR Signaling:** If a stream possesses BT.2020 primaries and ST 2084 or HLG transfer curve, but lacks static mastering metadata or is signaled in 8-bit, it is strictly classified as `PARTIAL_HDR` rather than full `HDR10`.
4. **Standard Web Streams:** Videos with standard sRGB/BT.709 primaries and transfer curves (or missing explicit `colr` atoms) are conclusively classified as `SDR` with 8-bit depth and BT.709 color space.
5. **Ambiguous Signaling:** Tracks with conflicting or partial color definitions are labeled `UNKNOWN` rather than forced into an assumed category.

---

## 4. PRESET AVAILABILITY MATRIX

### 4.1 Preset Definitions & Status Model

```kotlin
enum class PresetStatus {
    AVAILABLE,
    PLANNED_STEP_2_2,
    PLANNED_STEP_2_3,
    PLANNED_STEP_2_4,
    HARDWARE_UNSUPPORTED
}
```

| Preset | Status (`SM6225`) | Badge (`SM6225`) | Status (`SM8650-AB`) | Badge (`SM8650-AB`) | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DIRECT** | `AVAILABLE` | `ACTIVE` | `AVAILABLE` | `ACTIVE` | Saves original video stream without re-encoding (Operational). |
| **HDR10_ENHANCED** | `HARDWARE_UNSUPPORTED` | `8-BIT HW ONLY` | `PLANNED_STEP_2_3` | `STEP 2.3` | On SM6225: "10-bit HDR encoding unsupported on this SoC (Snapdragon 680/685 supports 8-bit Main only)". On SM8650: "Hardware HEVC Main10 with ST 2084 PQ and BT.2020 color gamut". |
| **ULTRA_HDR_60FPS** | `PLANNED_STEP_2_4` | `STEP 2.4` | `PLANNED_STEP_2_4` | `STEP 2.4` | Motion interpolation and high frame-rate HDR conversion. |

### 4.2 Dynamic Action Button Behavior
The format selection bottom sheet CTA button dynamically displays the format resolution combined with the preset status badge:
- When **Direct Pass-through** is selected:  
  `DOWNLOAD 2160P • ACTIVE`
- When **HDR10 Enhanced** is selected on Snapdragon 685:  
  `DOWNLOAD 2160P • 8-BIT HW ONLY`

---

## 5. ORIGINAL RETENTION VERIFICATION

### 5.1 Storage Directory Separation
To prevent data loss and allow users to retain pristine source videos while generating processed versions, `StorageManager.kt` enforces separate directory trees:
- **Original Source Downloads:** `Environment.DIRECTORY_MOVIES/originals/`  
  Full path: `/storage/emulated/0/Movies/originals/`
- **Processed / Re-encoded Exports:** `Environment.DIRECTORY_MOVIES/downloads/`  
  Full path: `/storage/emulated/0/Movies/downloads/`

### 5.2 Collision Handling & Purge APIs
1. **Collision Resolution:** `StorageManager.createUniqueFile(parentDir, fileName)` appends numeric increments `_1`, `_2` before the extension to ensure existing downloads are never overwritten.
2. **Purge API:** Added `StorageManager.purgeOriginalFiles(): Long` and `StorageManager.getTotalOriginalsSizeBytes(): Long` to allow space reclamation from settings without impacting user library files.
3. **Repository Wiring:** `DownloadRepository.kt` routes initial stream acquisition through `createOriginalFile()`, guaranteeing the original stream is stored securely before any processing job begins.

---

## 6. DATABASE MIGRATION VERIFICATION (v2 $\rightarrow$ v3)

### 6.1 Real Database Verification
The SQLite database `unidownco_database.db` was pulled from `/data/data/com.unidownco.debug/databases/` on the physical device and audited using a Python verification script:

1. **Table Schema Validation:**
   - Table `downloads` (original schema preserved, existing records intact).
   - Table `video_metadata` (linked to `downloads.id` via foreign key).
   - Table `processing_jobs` (created by `MIGRATION_2_3` with all required columns):
     - `jobId` (TEXT PRIMARY KEY)
     - `downloadId` (TEXT NOT NULL, FOREIGN KEY $\rightarrow$ `downloads.id` ON DELETE CASCADE)
     - `preset` (TEXT NOT NULL)
     - `stage` (TEXT NOT NULL)
     - `progress` (REAL NOT NULL)
     - `outputPath` (TEXT)
     - `errorMessage` (TEXT)
     - `createdAt` (INTEGER NOT NULL)
     - `updatedAt` (INTEGER NOT NULL)
2. **Index Validation:**
   - `index_processing_jobs_downloadId` on `processing_jobs(downloadId)`: **VERIFIED PRESENT**
   - `index_processing_jobs_stage` on `processing_jobs(stage)`: **VERIFIED PRESENT**
3. **Foreign Key Cascade Test:**
   - Executed `PRAGMA foreign_keys = ON;`
   - Inserted a mock download record and linked processing job.
   - Deleted the download record from `downloads`.
   - Verified that the associated `processing_jobs` row was automatically deleted via cascade (`SELECT COUNT(*) FROM processing_jobs` returned 0).

---

## 7. AUTOMATED UNIT TEST RESULTS

All unit test suites were executed via `./gradlew clean testDebugUnitTest`.

### 7.1 Test Execution Summary
- **Total Tests Executed:** 47
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Pass Rate:** **100%**
- **Execution Time:** 35 seconds

### 7.2 Key Verified Tests in `Step2FoundationTest.kt`
- `hardwareMain10ContradictionIsResolved()`: Asserts that on SM6225 software Main10 is not claimed, HW Main10 is false, and report contains no false fallback promises.
- `processingPresetStatusReflectsHardwareRealities()`: Asserts `DIRECT` is `AVAILABLE`, `HDR10_ENHANCED` is `HARDWARE_UNSUPPORTED` on 8-bit-only hardware with `8-BIT HW ONLY` badge, and `PLANNED_STEP_2_3` on 10-bit hardware.
- `strictHdr10ComplianceRequiresAllFourCriteria()`: Asserts that BT.2020 + ST 2084 + 10-bit + static metadata yields `HDR10`.
- `partialHdrSignaledWhenMetadataIncomplete()`: Asserts that missing static metadata yields `PARTIAL_HDR`.
- `dolbyVisionProfileNotSpeculated()`: Asserts that Dolby Vision without explicit profile does not speculate Profile 8.1.
- `sdrClassificationStandardCompliant()`: Asserts BT.709 with no HDR metadata maps strictly to `SDR`.
- `storageRetentionEnforcesSeparateDirectories()`: Asserts originals go to `Movies/originals/` and processing to `Movies/downloads/`.

---

## 8. PHYSICAL DEVICE EVIDENCE

### 8.1 Device Identification
- **Model:** Xiaomi Redmi Note 25062RN2DA
- **Android Version:** Android 16 (API 36)
- **Board / Hardware:** bengal / qcom (`SM6225`)
- **ADB Serial:** `aab64c2c`

### 8.2 Real-Device UI Evidence
The following screenshots were captured on the physical test device running the debug build:
1. `screen_step2_1_1_presets.png`: Shows the format selection bottom sheet scrolled to the "AUTOMATIC HDR & PROCESSING" section:
   - `Direct Pass-through`: `ACTIVE` badge.
   - `HDR10 Enhanced`: `8-BIT HW ONLY` badge and descriptive notice: "10-bit HDR encoding unsupported on this SoC (Snapdragon 680/685 supports 8-bit Main only)".
   - `Ultra HDR 60 FPS`: `STEP 2.4` badge.
   - Action Button: `DOWNLOAD 2160P • 8-BIT HW ONLY` (when HDR10 Enhanced is selected).
2. `screen_step2_1_1_direct_selected.png`: Shows the action button immediately updating to `DOWNLOAD 2160P • ACTIVE` upon tapping Direct Pass-through.

---

## 9. GIT COMMIT & REMOTE STATUS

### 9.1 Commit Details
- **Branch:** `master`
- **Commit Message:** `fix(step2.1.1): resolve main10 capability contradiction, enforce strict hdr classification, and correct preset availability`
- **Files Modified:**
  - `app/src/main/java/com/unidownco/core/storage/StorageManager.kt`
  - `app/src/main/java/com/unidownco/data/repository/DownloadRepository.kt`
  - `app/src/main/java/com/unidownco/domain/models/ProcessingPreset.kt`
  - `app/src/main/java/com/unidownco/engine/analyzer/VideoAnalyzerImpl.kt`
  - `app/src/main/java/com/unidownco/engine/hardware/HardwareCapabilityRegistry.kt`
  - `app/src/main/java/com/unidownco/feature/home/HomeScreen.kt`
  - `app/src/test/java/com/unidownco/Step2FoundationTest.kt`

### 9.2 Strict Adherence to Non-Goals
In accordance with the Step 2.1.1 mandate:
- **NO** `MediaCodecVideoEncoder` was created.
- **NO** EGL or OpenGL rendering/encoding pipeline was implemented.
- **NO** FFmpeg conversion or motion interpolation was executed.
- Step 2.2 remains locked pending user approval.
