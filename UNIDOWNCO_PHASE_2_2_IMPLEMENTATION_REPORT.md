# UNIDOWNCO PHASE 2.2 IMPLEMENTATION REPORT
## Media Inspector Engine & Multi-Source File Resolver

**Project:** UniDownco  
**Application ID:** `com.unidownco` (Release), `com.unidownco.debug` (Debug)  
**Workspace:** `D:\Webapp\UniDownco`  
**GitHub Repository:** `https://github.com/arybishal/UniDownco`  
**Branch:** `master`  
**Hardware Verification Target:** Xiaomi Redmi Note 14 4G (`25062RN2DA`, Qualcomm SM6225 / bengal, Android 16 / API 36)  
**Implementation Date:** November 16, 2026  
**Status:** PHASE 2.2 COMPLETE (Phase 2.3 Awaiting Explicit User Approval)

---

## 1. Executive Summary & Authorization Compliance

Under explicit authorization for **Phase 2.2 ONLY** (`Media Inspector Engine & Multi-Source File Resolver`), the multi-source extraction pipeline, stream resolver, and inspection engine have been implemented, tested, and validated across both host JVM unit tests and the connected physical Xiaomi Redmi SM6225 device.

### Strict Scope Verification:
- **Subphase 2.1:** Completed and verified in prior turn (`264b7bc5da7227e7ca4e3f4384d0b64be12f9b88`).
- **Subphase 2.2:** Completed in this turn:
  1. Multi-source file and content URI resolution (`MediaSourceResolver`, `MediaSource`, `SeekableDataSource`).
  2. Bounded multi-track inspection engine (`MediaInspectorEngine`, `MediaInspectorEngineImpl`).
  3. Frame rate, average FPS, and Variable Frame Rate (VFR) timestamp jitter evaluation.
  4. Bitstream SEI/SPS integration and metadata conflict alerting.
  5. Fake HDR rejection (8-bit with HDR color signaling) with critical diagnostics.
  6. Resource lifecycle management and coroutine cooperative cancellation.
- **Subphase 2.3 (HDR Classification & Device Compatibility Evaluator):** **NOT STARTED** (Scope preserved for next phase).
- **Subphase 2.4 (Compose UI & ViewModel):** **NOT STARTED**.
- **Subphase 2.5 (Navigation & SAF Picker):** **NOT STARTED**.
- **Database Schema:** Room database remains untouched at Version 3.
- **Dependencies:** 0 new external libraries added.

---

## 2. Architecture & Components Implemented

### 2.1 Seekable Data Source Abstraction (`com.unidownco.engine.inspector.source`)
- **`SeekableDataSource`**: Unified random-access abstraction providing `seek(position)` and bounded `read(buffer, offset, length)`.
- **`RandomAccessFileDataSource`**: Seekable wrapper over `RandomAccessFile` for direct disk files.
- **`FileChannelDataSource`**: Seekable wrapper over Android `FileChannel` extracted from `ParcelFileDescriptor`.
- **`ByteArrayDataSource`**: High-performance in-memory seekable buffer for testing and micro-benchmarks without disk I/O.

### 2.2 Multi-Source File Resolver (`com.unidownco.engine.inspector.source`)
- **`MediaSource`**: Sealed resource lifecycle interface implementing `AutoCloseable` with `openSeekable()`, `openInputStream()`, `getFileDescriptor()`, `fileDescriptorOffset`, and `fileDescriptorLength`.
- **`LocalFileMediaSource`**: Manages app-owned downloaded files and local disk storage files.
- **`ContentUriMediaSource`**: Manages Android Storage Access Framework (`content://...`) and MediaStore assets via duplicated `ParcelFileDescriptor` handles, ensuring leak-free descriptor closure.
- **`MediaSourceResolver`**:
  - `resolve(file: File)`: Validates file existence, read permissions, non-directory status, and infers container MIME type.
  - `resolve(uri: Uri, context: Context)`: Resolves `file://` paths and queries `OpenableColumns` (`DISPLAY_NAME`, `SIZE`) for `content://` providers.
  - `resolveAny(source: Any, context: Context?)`: Polymorphic dispatcher supporting `File`, `Uri`, and URI string paths.

### 2.3 Extended Container Parser Integration (`com.unidownco.engine.parser.Mp4MetadataParser`)
- Decoupled `Mp4MetadataParser` from direct `RandomAccessFile` dependencies.
- Updated all recursive container parsers (`parseBoxTree`, `parseStsdEntries`, `parseFtypPayload`, `parseColrPayload`, `parseMdcvPayload`, `parseClliPayload`, `parseHvcCPayload`, `parseAvcCPayload`, `parseTkhdPayload`) to operate on generic `SeekableDataSource`.
- Enables identical, bounded ISO-BMFF box parsing across local files, SAF content URIs, and memory buffers.

### 2.4 Media Inspector Engine Core (`com.unidownco.engine.inspector`)
- **`MediaInspectorEngine`**: High-level contract providing:
  - `suspend fun inspect(source: MediaSource): Result<MediaInspectionReport>`
  - `suspend fun inspect(file: File): Result<MediaInspectionReport>`
  - `suspend fun inspect(uri: Uri, context: Context): Result<MediaInspectionReport>`
- **`MediaInspectorEngineImpl`**:
  - **Coroutine Concurrency:** Dispatched on `Dispatchers.IO` with cooperative cancellation via `coroutineContext.ensureActive()` at safe checkpoints.
  - **Multi-Track Inspection:** Analyzes all video, audio, and subtitle streams using `MediaExtractor` and `MediaMetadataRetriever` on Android, with automatic fallback to container track headers in host JVM test environments.
  - **Timing & VFR Analysis:** Samples up to 90 consecutive presentation timestamps on the primary video track. Evaluates coefficient of variation ($CV = \frac{\sigma}{\mu}$):
    - $CV > 0.10 \implies \text{isVariableFrameRate} = \text{true}$
    - $CV \le 0.10 \implies \text{isVariableFrameRate} = \text{false}$
    - Accurate average frame rate calculation: $\frac{(N-1) \times 1,000,000}{t_{last} - t_{first}}$.
  - **Bitstream Verification:** Integrates with `HevcBitstreamParser` to cross-validate SPS VUI colorimetry, SEI 137 (MDCV), and SEI 144 (CLLI).
  - **Metadata Conflict Detection:** Compares container `colr` atoms with bitstream VUI headers. Mismatches generate structured `MetadataConflictAlert` records and non-fatal `InspectionWarning` advisories.
  - **Fake HDR Rejection:** Explicitly checks for 8-bit video streams declaring HDR signaling (BT.2020 primaries or PQ transfer). Flags them as `HdrClassification.FAKE_HDR_REJECTED` and emits a `CRITICAL` advisory warning.
  - **Resource Cleanup:** Guaranteed cleanup of `MediaExtractor`, `MediaMetadataRetriever`, `ParcelFileDescriptor`, and file streams inside `finally` blocks.

---

## 3. Test Suite Verification Results

### 3.1 Host JVM Unit Tests (`.\gradlew.bat testDebugUnitTest`)
- **Total Tests Executed:** 122 tests.
- **Pass Rate:** 100% (122 passed, 0 failed, 0 skipped).
- **New Test Classes Added:**
  - `com.unidownco.MediaSourceResolverTest`: 5 test cases covering file resolution, missing file handling, directory rejection, seekable buffers, and polymorphic dispatch.
  - `com.unidownco.MediaInspectorEngineTest`: 6 test cases covering Genuine HDR10, SDR BT.709, Fake HDR rejection, 0-byte empty files, non-existent files, and coroutine cancellation.

```
BUILD SUCCESSFUL in 17s
26 actionable tasks: 3 executed, 23 up-to-date
122 tests completed, 0 failed
```

### 3.2 Physical Device Connected Tests (`adb shell am instrument ...`)
- **Target Device:** Xiaomi Redmi Note 14 4G (`25062RN2DA`)
- **Platform:** Qualcomm SM6225 / bengal, Android 16 (API 36)
- **Total Instrumentation Tests Executed:** 22 tests.
- **Pass Rate:** 100% (22 passed, 0 failed, 0 skipped).
- **New Device Tests (`MediaInspectorDeviceTest`):**
  1. `testDeviceInspection_RealMediaExtractor_FixtureA`: PASSED (Genuine HDR10 bitstream + container verified via real hardware framework).
  2. `testDeviceInspection_MultiTrackAndTimingAnalysis_RealMedia`: PASSED (Multi-track video/audio extraction, display resolution, nominal and average FPS on physical hardware).
  3. `testDeviceInspection_FakeHdrRejection_FixtureD`: PASSED (Fake HDR 8-bit stream rejected with critical advisory on device).
  4. `testDeviceInspection_SdrBt709_FixtureC`: PASSED (SDR BT.709 stream accurately classified).
- **Regression Suite:**
  - `MediaCodecEncodingDeviceTest`: 12/12 passed (including Main10 hardware gating, 8-bit baseline, cancellation cleanup).
  - `InstagramDeviceTest`: 4/4 passed.
  - `MediaStreamMuxerDeviceTest`: 2/2 passed.

```
Time: 66.307
OK (22 tests)
INSTRUMENTATION_CODE: -1
```

---

## 4. Hardware Limitations & Ground Truth Integrity

In compliance with project anti-fabrication guidelines:
1. **Qualcomm SM6225 Hardware Baseline:** The physical Xiaomi device possesses hardware encoding support for HEVC Main Profile (8-bit) up to 1080p, but **hardware Main10 encoding remains unsupported**.
2. **Device Compatibility Field:** In `MediaInspectionReport`, `deviceCompatibility` remains `null` for Subphase 2.2 and will be systematically evaluated in Subphase 2.3.
3. **No Fabricated Capabilities:** The engine does not simulate or claim hardware Main10 encoding capabilities where hardware lacks it.

---

## 5. Source Tree Changes Summary

| File | Status | Description |
|---|---|---|
| `app/src/main/java/com/unidownco/engine/inspector/source/SeekableDataSource.kt` | New | Seekable random-access abstraction for file, SAF channel, and memory buffers. |
| `app/src/main/java/com/unidownco/engine/inspector/source/MediaSource.kt` | New | Sealed abstraction for local files and SAF content URIs. |
| `app/src/main/java/com/unidownco/engine/inspector/source/MediaSourceResolver.kt` | New | Scoped storage resolver for `File`, `Uri`, and path strings. |
| `app/src/main/java/com/unidownco/engine/parser/Mp4MetadataParser.kt` | Modified | Updated container parser to operate on `SeekableDataSource`. |
| `app/src/main/java/com/unidownco/engine/inspector/MediaInspectorEngine.kt` | New | Public contract for media inspection. |
| `app/src/main/java/com/unidownco/engine/inspector/MediaInspectorEngineImpl.kt` | New | Complete multi-track, multi-source inspection engine implementation. |
| `app/src/test/java/com/unidownco/MediaSourceResolverTest.kt` | New | Host unit test suite for source resolver and seekable buffers. |
| `app/src/test/java/com/unidownco/MediaInspectorEngineTest.kt` | New | Host unit test suite for media inspection engine. |
| `app/src/androidTest/java/com/unidownco/MediaInspectorDeviceTest.kt` | New | Physical Android device test suite running against real MediaExtractor. |
| `UNIDOWNCO_PHASE_2_2_IMPLEMENTATION_REPORT.md` | New | Technical governance documentation and test verification report. |

---

## 6. Next Authorized Step

- **Current Status:** Subphase 2.2 is **COMPLETE** and verified on device.
- **Next Phase:** Subphase 2.3 (**HDR Classification Engine & Device Compatibility Evaluator**).
- **Gate:** Work is stopped at the authorized Phase 2.2 boundary. Awaiting explicit user approval before proceeding to Subphase 2.3.
