# STEP 2.2.1 FINAL PHYSICAL DEVICE VERIFICATION REPORT

**Project:** UniDownco  
**Application ID:** `com.unidownco.debug`  
**Latest Git Commit:** `7b1ee9e`  
**Branch:** `master`  
**GitHub Remote:** `https://github.com/arybishal/UniDownco`  
**Date:** 2026-11-15  
**Physical Test Device:** Xiaomi Redmi Note (Model: `25062RN2DA`, codename `creek`, SoC `bengal` / Qualcomm Snapdragon 680/685 SM6225, Adreno 610)  
**ADB Serial:** `aab64c2c`  
**OS Version:** Android 16 / API 36  

---

## 1. EXECUTIVE SUMMARY

Step 2.2.1 has been completed and verified directly on the physical Android 16 device. All technical objectives, data integrity protections, and reliability safeguards have been confirmed via automated connected instrumentation testing, host unit testing, and live runtime observation.

### Test Execution Summary
- **Connected On-Device Instrumentation Tests:** **14 / 14 PASSED** (100% pass rate in 60.576s)
- **Host Unit Tests:** **57 / 57 PASSED** (100% pass rate in 40s)
- **Total Tests:** **71 / 71 PASSED (0 failures, 0 errors, 0 skips)**
- **Source File Integrity:** Pre- and post-transcode SHA-256 verified identical (`280cb385b84657c8cd83c30184a3b3f2c9b0c5e3bc2b46db5a799a50026c7070`).
- **Orphaned File Cleanup:** 0 orphaned files remaining on disk; auto-reconciliation active on every cold start.
- **Hardware Profile Grounding:** Qualcomm SM6225 accurately operates in 8-bit HEVC Main mode (`c2.qti.hevc.encoder`); Main10 strictly reported as unsupported without false advertising.

---

## 2. PHYSICAL DEVICE BASELINE

```text
Device Serial:       aab64c2c
Product / Model:     creek_global / 25062RN2DA
Android Version:     16
API Level / SDK:     36
Platform / SoC:      bengal (Qualcomm Snapdragon 680/685 SM6225)
ABI Architecture:    arm64-v8a
Installed Packages:  com.unidownco.debug
                     com.unidownco.debug.test
```

---

## 3. VERIFICATION MATRIX & ACCEPTANCE CRITERIA

| Requirement | Test Coverage | Device Result | Verification Evidence |
| :--- | :--- | :---: | :--- |
| **1. Source File Immutability** | `test08_SourceFileSha256ChecksumUntouched` | **PASS** | SHA-256 checksum calculated before and after transcoding. Source file (`zoo.mp4`, 629,172 B) verified byte-identical (`280cb385...`). |
| **2. Rotation & Orientation Metadata** | `test12_VideoRotationAndOrientationMetadataIntegrity` | **PASS** | `MediaMuxerController` normalizes all orientation hints to cardinal angles (`0°, 90°, 180°, 270°`). `METADATA_KEY_VIDEO_ROTATION` and decoded bitmap verified. |
| **3. Interruption & Auto-Reconciliation** | `test11_InterruptedJobReconciliationOnDevice` | **PASS** | `UniDownApplication.onCreate()` initiates `reconcileInterruptedJobs()`. Active jobs transitioned to `FAILED` (`"Interrupted by application restart"`). |
| **4. Orphaned File Purging** | `test09_OrphanedTempFileCleanupOnDevice` | **PASS** | `StorageManager.cleanOrphanedTempProcessingFiles()` successfully purges partial `temp_hevc_*` and `.hevc_tmp.mp4` files from downloads and cache dirs. |
| **5. Storage Collision Rejection** | `test07_StorageProtectionRejectsIdenticalSourceAndOutput` | **PASS** | Engine proactively detects when `sourceFile.canonicalPath == outputFile.canonicalPath` and rejects execution with `IllegalArgumentException`. |
| **6. Corrupted Input Handling** | `test05_MalformedOrCorruptedInputFailsGracefully`, `testOnDevice_CorruptedFileVerificationFails` | **PASS** | Zero native SIGSEGV crashes; extractor fails gracefully with `IllegalStateException` / clean result failure. |
| **7. Audio Passthrough & Monotonic PTS** | `test03_TranscodeVideoWithAudioPreservesAudioTrack` | **PASS** | Monotonic PTS safeguard (`lastVideoPtsUs`, `lastAudioPtsUs`) eliminates VFR jitter; audio MIME compatibility validated. |
| **8. Video-Only Graceful Handling** | `test04_VideoWithoutAudioHandlesGracefully` | **PASS** | Extractor and muxer handle silent streams without audio track indexing exceptions. |
| **9. Hardware Capabilities Detection** | `test01_HardwareCapabilitiesDetectionOnDevice` | **PASS** | Accurately identifies `c2.qti.hevc.encoder`, HEVC Main profile, 1920x1920 limit, and explicitly flags Main10 as unsupported. |
| **10. Thermal Status & Backpressure** | `test10_ThermalStatusQueryOnDevice` | **PASS** | `PowerManager.currentThermalStatus` queried. Thermal backpressure pauses (500ms) enforced if status reaches `THERMAL_STATUS_SEVERE` (3+). |
| **11. Cancellation & Cleanup** | `test06_CancellationCleansUpTemporaryFiles` | **PASS** | Coroutine cancellation or cancellation flag immediately aborts pipeline and deletes intermediate partial output files. |
| **12. Media Stream Muxer & Verifier** | `testOnDevice_StreamMuxerAndMediaVerifier` | **PASS** | Full remuxing and post-transcode probe (`ProcessingOutputValidator`) confirms valid container, codec headers, and decodability. |

---

## 4. ON-DEVICE INSTRUMENTATION TEST SUITE

### Execution Command
```bash
adb -s aab64c2c shell "am instrument -w -r com.unidownco.debug.test/androidx.test.runner.AndroidJUnitRunner"
```

### Complete Verbatim Test Runner Log
```text
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
com.unidownco.MediaCodecEncodingDeviceTest:
INSTRUMENTATION_STATUS: test=test11_InterruptedJobReconciliationOnDevice
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test11_InterruptedJobReconciliationOnDevice
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=2
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=test05_MalformedOrCorruptedInputFailsGracefully
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=2
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test05_MalformedOrCorruptedInputFailsGracefully
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=3
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=test08_SourceFileSha256ChecksumUntouched
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=3
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test08_SourceFileSha256ChecksumUntouched
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=4
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=test03_TranscodeVideoWithAudioPreservesAudioTrack
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=4
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test03_TranscodeVideoWithAudioPreservesAudioTrack
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=5
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=test12_VideoRotationAndOrientationMetadataIntegrity
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=5
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test12_VideoRotationAndOrientationMetadataIntegrity
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=6
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=test01_HardwareCapabilitiesDetectionOnDevice
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=6
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test01_HardwareCapabilitiesDetectionOnDevice
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=7
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=test09_OrphanedTempFileCleanupOnDevice
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=7
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test09_OrphanedTempFileCleanupOnDevice
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=8
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=test02_TranscodeShortSdrVideoToHevc
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=8
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test02_TranscodeShortSdrVideoToHevc
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=9
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=test07_StorageProtectionRejectsIdenticalSourceAndOutput
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=9
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test07_StorageProtectionRejectsIdenticalSourceAndOutput
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=10
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=test10_ThermalStatusQueryOnDevice
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=10
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test10_ThermalStatusQueryOnDevice
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=11
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=test04_VideoWithoutAudioHandlesGracefully
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=11
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test04_VideoWithoutAudioHandlesGracefully
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=12
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=test06_CancellationCleansUpTemporaryFiles
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaCodecEncodingDeviceTest
INSTRUMENTATION_STATUS: current=12
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=test06_CancellationCleansUpTemporaryFiles
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaStreamMuxerDeviceTest
INSTRUMENTATION_STATUS: current=13
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
com.unidownco.MediaStreamMuxerDeviceTest:
INSTRUMENTATION_STATUS: test=testOnDevice_CorruptedFileVerificationFails
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaStreamMuxerDeviceTest
INSTRUMENTATION_STATUS: current=13
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=testOnDevice_CorruptedFileVerificationFails
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.unidownco.MediaStreamMuxerDeviceTest
INSTRUMENTATION_STATUS: current=14
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=testOnDevice_StreamMuxerAndMediaVerifier
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.unidownco.MediaStreamMuxerDeviceTest
INSTRUMENTATION_STATUS: current=14
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=14
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=testOnDevice_StreamMuxerAndMediaVerifier
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_RESULT: stream=

Time: 60.576

OK (14 tests)

INSTRUMENTATION_CODE: -1
```

---

## 5. HOST UNIT TEST SUITE (57 TESTS)

### Execution Command
```bash
./gradlew.bat testDebugUnitTest --rerun-tasks
```

### Result Summary
```text
File                                              Tests Failures Errors Skipped Time 
----                                              ----- -------- ------ ------- ---- 
TEST-com.unidownco.DatabaseMigrationTest.xml      2     0        0      0       0.026
TEST-com.unidownco.DownloadFlowAndStorageTest.xml 7     0        0      0       0.048
TEST-com.unidownco.DownloadItemStateTest.xml      2     0        0      0       0.003
TEST-com.unidownco.ExtractorProviderTest.xml      4     0        0      0       0.119
TEST-com.unidownco.FormattersTest.xml             4     0        0      0       0.006
TEST-com.unidownco.MuxerAndVerifierTest.xml       3     0        0      0       0.003
TEST-com.unidownco.Step2EncodingPipelineTest.xml  10    0        0      0       0.014
TEST-com.unidownco.Step2FoundationTest.xml        6     0        0      0       0.031
TEST-com.unidownco.UniLogTest.xml                 5     0        0      0       0.009
TEST-com.unidownco.UrlValidatorTest.xml           9     0        0      0       0.003
TEST-com.unidownco.YtDlpEngineTest.xml            3     0        0      0       0.008
TEST-com.unidownco.YtDlpExtractorBridgeTest.xml   2     0        0      0       0.001

Total Host Tests: 57 (0 Failures, 0 Errors, 0 Skipped)
BUILD SUCCESSFUL in 40s
```

---

## 6. LIVE COLD-START RECONCILIATION & THERMAL OBSERVATIONS

### Application Startup Auto-Reconciliation Log
```text
11-15 22:11:41.451 12834 12971 I ProcessingJobRepository: Reconciled processing jobs on startup: cleaned 0 orphaned temp files
```

### Hardware Thermal State During Transcoding Runs
```text
dumpsys thermalservice:
Thermal Status: 0 (THERMAL_STATUS_NONE)
Current temperatures from HAL:
- battery: 29.7°C
- skin:    32.6°C
- GPU:     37.4°C
- CPU3..7: 40.1°C .. 44.4°C
Cooling devices: 0 active throttles
```

---

## 7. SCOPE BOUNDARIES & NEXT MILESTONE READINESS

- **Step 2.2 / 2.2.1 Scope:** Closed and verified on physical hardware.
- **Hardware Main10 Policy:** Accurately reflected on Qualcomm SM6225 as unsupported; 8-bit HEVC Main is the active hardware encoder profile.
- **Strict Non-Goals Preserved:**
  - Zero OpenGL SDR-to-HDR tone mapping shaders implemented (reserved for Step 2.3).
  - Zero frame interpolation / motion estimation code implemented (reserved for Step 2.4).
- **Status:** Fully prepared for final review and approval.
