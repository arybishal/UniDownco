# UNIDOWNCO PHASE 2 IMPLEMENTATION PLAN
# MEDIA INSPECTOR UI & BITSTREAM INSPECTION TOOLING

**Project:** UniDownco  
**Workspace:** `D:\Webapp\UniDownco`  
**GitHub Repository:** `https://github.com/arybishal/UniDownco`  
**Branch:** `master`  
**Package:** `com.unidownco` (Release) / `com.unidownco.debug` (Debug)  
**Baseline Commit:** `69428dd796b01ae77875b52c6d9638dd232bd84b`  
**Status:** **PLANNING AND ARCHITECTURE ONLY (APPROVAL-GATED)**

---

## 1. EXECUTIVE SUMMARY & REPOSITORY AUDIT FINDINGS

### 1.1 Objective of Phase 2
Phase 2 establishes a professional, frame-accurate, engineering-grade **Media Inspector UI & Bitstream Inspection Tooling** for the UniDownco Android application. It transitions the application from a basic video downloader with background HEVC processing into an analytical media workstation capable of deeply interrogating local media files, downloaded streams, and external files picked via Android's Storage Access Framework (SAF).

Users and media engineers will be able to answer with technical precision:
1. What video and audio codecs does the container hold?
2. What are the exact pixel dimensions, display aspect ratio, and pixel aspect ratio?
3. What is the nominal vs. average frame rate, and is the stream Variable Frame Rate (VFR) or Constant Frame Rate (CFR)?
4. What are the stream bitrates and total container bitrates?
5. Is the video stream genuine 10-bit or legacy 8-bit?
6. Is the video Standard Dynamic Range (SDR) or High Dynamic Range (HDR)?
7. Does the stream contain genuine HDR10 metadata (Mastering Display Colour Volume SEI 137, Content Light Level SEI 144, and MP4 `colr`/`mdcv`/`clli` atoms)?
8. Does the file contain Dolby Vision signaling, and what profile is declared?
9. What audio channels, channel masks, sample rates, bit depths, and languages are present across all audio tracks?
10. Can the host physical device decode the source video/audio, and can it hardware-encode HEVC Main (8-bit) or HEVC Main10 (10-bit)?

### 1.2 Baseline Repository Audit
An exhaustive inspection of the working tree at commit `69428dd` reveals the following architectural foundation:

| Component / Subsystem | Current State in Repository | Phase 2 Reuse Strategy |
|---|---|---|
| **Git Working Tree** | Clean, on branch `master`, synchronized with `origin/master`. | Maintain isolated planning documents; zero unapproved code edits. |
| **Android Architecture** | Kotlin 2.0.21, AGP 8.7.2, Gradle 8.10.2, Java 17, Target SDK 35, Min SDK 24. | Full compatibility with Android 7.0 through Android 16 (API 36). |
| **Jetpack Compose UI** | `DESIGN.md` tokens implemented: `UniDownTheme`, `UniColors`, `UniTypography`, `UniMonoTypography`, `UniShapes`, `UniSpacing`. | Build inspector UI strictly adhering to editorial white canvas, black framing, yellow `#FFD429` accent, and JetBrains Mono specs. |
| **Room Database** | Version 3 (`downloads`, `video_metadata`, `processing_jobs`). `VideoMetadataDao` exists. | Leverage existing `VideoMetadataEntity` where cached data exists; avoid unnecessary schema migrations. |
| **Bitstream Parsing** | `HevcBitstreamParser.kt` (696 lines): Annex B start-code extraction, SPS VUI parsing, Prefix SEI 137 (MDCV), Prefix SEI 144 (CLLI), emulation prevention byte handling. | Direct reuse for deep HEVC stream inspection. |
| **Container Parsing** | `Mp4MetadataParser.kt` (487 lines): ISOBMFF parser for `moov`, `trak`, `mdia`, `minf`, `stbl`, `stsd`, `hvcC`, `colr`, `mdcv`, `clli`. | Extend to extract atom hierarchy tree (`BoxNode`) and H.264 `avcC` records. |
| **Output Validator** | `ProcessingOutputValidator.kt` (385 lines): Strict 10-bit HDR10 validator with MediaExtractor and Mp4MetadataParser fallback. | Direct reuse of validation rules for HDR classification. |
| **Video Analyzer** | `VideoAnalyzerImpl.kt` (232 lines): Analyzes single video track and single audio track; writes to Room. | Refactor to delegate to new modular `MediaInspectorEngine`. |
| **Hardware Capabilities** | `HardwareCapabilityRegistry.kt` (227 lines): Comprehensive MediaCodec probing (hardware vs software, Main vs Main10, surface input, P010, resolution limits). | Direct integration to populate Device Compatibility cards. |
| **Color Science & Provenance** | `HdrMasteringDisplay.kt` (191 lines), `MetadataProvenance.kt` (46 lines). | Direct reuse to ensure zero metadata fabrication and clear provenance reporting. |
| **Test Baseline** | **96 unit tests** passing (100%), **18 connected device tests** passing on physical Xiaomi Redmi 25062RN2DA (SM6225). | Protect 100% of existing tests; add dedicated Phase 2 suites. |

---

## 2. REUSABLE ASSETS & ARCHITECTURAL GAPS

### 2.1 Working Assets to Preserve
1. **`HevcBitstreamParser.kt`**: Provides bit-accurate parsing of SPS (profile IDC, tier, level IDC, bit depth luma/chroma, pic width/height, VUI color primaries, transfer curve, matrix coefficients) and SEI messages (MDCV, CLLI).
2. **`Mp4MetadataParser.kt`**: Provides memory-efficient random access parsing of MP4 containers without reading entire files into RAM.
3. **`HardwareCapabilityRegistry.kt`**: Accurately reflects device capabilities, including the known hardware limitation of the physical Xiaomi SM6225 (`c2.qti.hevc.encoder` supports Main 8-bit only; Main10 is hardware unsupported).
4. **`HdrMasteringDisplay.kt` & `MetadataProvenance.kt`**: Formalized models for mastering display metadata and provenance tracking (`MEASURED`, `SOURCE_DECLARED`, `MATHEMATICALLY_ESTIMATED`, `UNAVAILABLE`).
5. **Standard Fixtures Inventory**:
   - `fixture_a_hdr10_main10_true2020.mp4` (Genuine Main 10, True BT.2020 primaries, ST 2084 PQ, MDCV, CLLI)
   - `fixture_b_hdr10_main10_dcip3_omitted_clli.mp4` (Genuine Main 10, DCI-P3 D65 primaries, ST 2084 PQ, MDCV, uncalculated CLLI)
   - `fixture_c_sdr_main_8bit.mp4` (SDR Main 8-bit baseline)
   - `fixture_d_fake_hdr_8bit.mp4` (Fake HDR: 8-bit Main with BT.2020/PQ signaling)

### 2.2 Architectural Gaps to Address in Phase 2
1. **Multi-Track Limitations**: Existing `VideoAnalyzerImpl` and `ProcessingOutputValidator` only inspect the first video track and first audio track. Real-world videos (e.g. dual-audio movies, commentaries, multi-language streams, embedded SRT/VTT/tx3g subtitles) require full multi-stream enumeration.
2. **External File Ingestion**: The existing UI only inspects items that exist in the `downloads` database table. There is no capability to inspect arbitrary video files stored on device storage or selected via the Android Storage Access Framework (SAF).
3. **Container Hierarchy Visibility**: `Mp4MetadataParser` extracts metadata into flat data classes but does not expose the hierarchical atom structure (`ftyp`, `moov`, `trak`, `stsd`, etc.) to the user.
4. **UI Presentation Gap**: `VideoDetailsScreen.kt` currently displays only a basic 7-row summary with a static text placeholder for encoding readiness. There is no dedicated Media Inspector screen with expandable technical sections, copy/share capabilities, or device compatibility matrices.
5. **File Access Abstraction**: Media analysis currently takes `java.io.File`, which fails for content URIs (`content://...`) granted via SAF file pickers on Android 10+.

---

## 3. PROPOSED ARCHITECTURE & COMPONENT TOPOLOGY

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   PRESENTATION LAYER                                   │
│                                                                                        │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                              MediaInspectorScreen                              │   │
│   │  ┌───────────────────────┐ ┌───────────────────────┐ ┌──────────────────────┐  │   │
│   │  │   MediaOverviewCard   │ │    VideoStreamCard    │ │   AudioStreamCard    │  │   │
│   │  └───────────────────────┘ └───────────────────────┘ └──────────────────────┘  │   │
│   │  ┌───────────────────────┐ ┌───────────────────────┐ ┌──────────────────────┐  │   │
│   │  │  HdrColorScienceCard  │ │ContainerHierarchyCard │ │DeviceCompatibility   │  │   │
│   │  └───────────────────────┘ └───────────────────────┘ └──────────────────────┘  │   │
│   │  ┌──────────────────────────────────────────────────────────────────────────┐  │   │
│   │  │                  InspectionReportDialog (Share / Copy / Export)          │  │   │
│   │  └──────────────────────────────────────────────────────────────────────────┘  │   │
│   └───────────────────────────────────────┬────────────────────────────────────────┘   │
│                                           │                                            │
│                                           ▼                                            │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                             MediaInspectorViewModel                            │   │
│   │           StateFlow<InspectorUiState> (Idle, Analyzing, Success, Error)        │   │
│   └───────────────────────────────────────┬────────────────────────────────────────┘   │
└───────────────────────────────────────────┼────────────────────────────────────────────┘
                                            │
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│                                     DOMAIN LAYER                                       │
│                                                                                        │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                             MediaInspectorEngine                               │   │
│   │  suspend fun inspect(source: MediaSource): Result<MediaInspectionReport>        │   │
│   └──────┬────────────────────┬────────────────────┬────────────────────┬──────────┘   │
│          │                    │                    │                    │              │
│          ▼                    ▼                    ▼                    ▼              │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐      │
│   │  Container   │     │    Stream    │     │  Bitstream   │     │     HDR      │      │
│   │  Inspector   │     │  Inspector   │     │  Inspector   │     │  Classifier  │      │
│   │ (Mp4Parser)  │     │(MediaExtract)│     │ (HevcParser) │     │ (Phase 1)    │      │
│   └──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘      │
│                               │                                         │              │
│                               ▼                                         ▼              │
│                        ┌──────────────┐                          ┌──────────────┐      │
│                        │    Device    │                          │   Metadata   │      │
│                        │Compatibility │                          │  Provenance  │      │
│                        │  (Registry)  │                          │  (Tracking)  │      │
│                        └──────────────┘                          └──────────────┘      │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│                                   DATA / I/O LAYER                                     │
│                                                                                        │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                             MediaSourceResolver                                │   │
│   │  ┌───────────────────────────────┐        ┌──────────────────────────────────┐ │   │
│   │  │        LocalFileSource        │        │        ContentUriSource          │ │   │
│   │  │  (Direct File / RandomAccess) │        │ (SAF / MediaStore ContentResolver│ │   │
│   │  └───────────────────────────────┘        └──────────────────────────────────┘ │   │
│   └────────────────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. DATA MODELS AND DOMAIN INTERFACES

### 4.1 Media Source Abstraction
To seamlessly support app-internal downloads, exported Gallery videos, and arbitrary files selected through the system file picker without duplicating file-reading logic:

```kotlin
sealed interface MediaSource {
    val displayName: String
    val length: Long
    fun openInputStream(): InputStream
    fun openSeekable(): SeekableInputSource

    data class FromFile(val file: File) : MediaSource {
        override val displayName: String get() = file.name
        override val length: Long get() = file.length()
        override fun openInputStream(): InputStream = FileInputStream(file)
        override fun openSeekable(): SeekableInputSource = SeekableFileSource(file)
    }

    data class FromUri(val uri: Uri, val context: Context) : MediaSource {
        override val displayName: String get() = resolveDisplayName(context, uri)
        override val length: Long get() = resolveSize(context, uri)
        override fun openInputStream(): InputStream =
            context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        override fun openSeekable(): SeekableInputSource = SeekableUriSource(context, uri)
    }
}

interface SeekableInputSource : AutoCloseable {
    val length: Long
    fun seek(position: Long)
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
    fun readFully(buffer: ByteArray)
}
```

### 4.2 Comprehensive Inspection Domain Model

```kotlin
/**
 * Root immutable report produced by the Media Inspector Engine.
 */
data class MediaInspectionReport(
    val overview: MediaOverview,
    val videoStreams: List<VideoStreamDetails>,
    val audioStreams: List<AudioStreamDetails>,
    val subtitleStreams: List<SubtitleStreamDetails>,
    val hdrDetails: HdrColorDetails,
    val containerDetails: ContainerDetails,
    val deviceCompatibility: DeviceCompatibilityDetails,
    val diagnosticWarnings: List<InspectorWarning>,
    val metadataProvenance: Map<String, MetadataProvenance>,
    val inspectionTimestamp: Long = System.currentTimeMillis(),
    val appVersion: String = BuildConfig.VERSION_NAME
)

data class MediaOverview(
    val filename: String,
    val fileExtension: String,
    val containerFormat: String,
    val fileSizeBytes: Long,
    val durationSeconds: Long,
    val overallBitrateKbps: Long,
    val primaryResolution: String,
    val primaryFrameRate: Double,
    val primaryVideoCodec: String,
    val primaryAudioCodec: String,
    val hdrClassification: HdrClassification,
    val sha256Digest: String? = null // Bounded asynchronous computation
)

enum class HdrClassification(val label: String, val isHdr: Boolean) {
    GENUINE_HDR10("Genuine HDR10 (Main10 + BT.2020 + PQ + Metadata)", true),
    HDR10_PLUS("HDR10+ (Dynamic Metadata Present)", true),
    DOLBY_VISION("Dolby Vision", true),
    HLG("HLG (Hybrid Log-Gamma)", true),
    PARTIAL_HDR("Partial HDR (Signaling Incomplete / Non-Standard)", true),
    FAKE_HDR_REJECTED("Fake HDR Rejected (8-bit with HDR Signaling)", false),
    SDR_BT709("SDR (BT.709 Standard Dynamic Range)", false),
    UNKNOWN("Unknown / Unspecified", false)
}

data class VideoStreamDetails(
    val trackIndex: Int,
    val trackId: Int,
    val codecName: String,
    val codecMime: String,
    val codecFourCc: String?,
    val profileName: String,
    val profileIdc: Int?,
    val levelName: String,
    val levelIdc: Int?,
    val width: Int,
    val height: Int,
    val displayAspectRatio: String,
    val pixelAspectRatio: String,
    val nominalFrameRate: Double,
    val averageFrameRate: Double?,
    val isVariableFrameRate: Boolean,
    val bitrateKbps: Long?,
    val bitDepth: Int,
    val chromaSubsampling: String, // e.g. "4:2:0"
    val pixelFormat: String,       // e.g. "YUV420P", "P010"
    val colorPrimaries: String,
    val colorTransfer: String,
    val matrixCoefficients: String,
    val colorRange: String,        // "Limited" or "Full"
    val rotationDegrees: Int,
    val keyframeCount: Int?,
    val isDefaultTrack: Boolean
)

data class AudioStreamDetails(
    val trackIndex: Int,
    val trackId: Int,
    val codecName: String,
    val codecMime: String,
    val codecFourCc: String?,
    val profileName: String?,
    val channelCount: Int,
    val channelLayout: String,     // e.g. "Stereo (L/R)", "5.1 Surround"
    val sampleRateHz: Int,
    val bitrateKbps: Long?,
    val bitDepth: Int?,            // e.g. 16-bit, 24-bit
    val language: String,
    val isDefaultTrack: Boolean,
    val durationSeconds: Long
)

data class SubtitleStreamDetails(
    val trackIndex: Int,
    val trackId: Int,
    val codecMime: String,
    val formatName: String,        // e.g. "Timed Text (tx3g)", "SubRip (SRT)", "WebVTT"
    val language: String,
    val isForced: Boolean,
    val isDefaultTrack: Boolean
)

data class HdrColorDetails(
    val classification: HdrClassification,
    val colorPrimariesName: String,
    val colorTransferName: String,
    val matrixCoefficientsName: String,
    val colorRangeName: String,
    val bitDepth: Int,
    val masteringDisplay: MasteringDisplayReport?,
    val contentLightLevel: ContentLightLevelReport?,
    val containerAtomVerified: Boolean,
    val bitstreamSeiVerified: Boolean,
    val dolbyVisionInfo: DolbyVisionReport?,
    val metadataConflicts: List<MetadataConflictAlert>
)

data class MasteringDisplayReport(
    val greenX: Double,
    val greenY: Double,
    val blueX: Double,
    val blueY: Double,
    val redX: Double,
    val redY: Double,
    val whitePointX: Double,
    val whitePointY: Double,
    val maxLuminanceNits: Double,
    val minLuminanceNits: Double,
    val provenance: MetadataProvenance,
    val sourceLocation: String // "SEI 137", "MP4 mdcv box", "Framework"
)

data class ContentLightLevelReport(
    val maxCllNits: Int,
    val maxFallNits: Int,
    val provenance: MetadataProvenance,
    val sourceLocation: String // "SEI 144", "MP4 clli box", "Framework"
)

data class DolbyVisionReport(
    val profile: Int,
    val level: Int,
    val rpuDetected: Boolean,
    val elType: String?, // "MEL", "FEL", "None"
    val compatibilityId: Int?
)

data class MetadataConflictAlert(
    val fieldName: String,
    val containerValue: String,
    val bitstreamValue: String,
    val frameworkValue: String?,
    val resolutionDescription: String
)

data class ContainerDetails(
    val containerType: String, // "MP4", "MOV", "MKV", "WebM"
    val majorBrand: String?,
    val minorVersion: Long?,
    val compatibleBrands: List<String>,
    val durationMs: Long,
    val totalTracks: Int,
    val boxHierarchy: List<BoxNode> // Hierarchical tree of atoms
)

data class BoxNode(
    val type: String,
    val offset: Long,
    val size: Long,
    val description: String,
    val children: List<BoxNode> = emptyList()
)

data class DeviceCompatibilityDetails(
    val deviceModel: String,
    val socPlatform: String,
    val videoDecodeSupport: CompatibilityStatus,
    val audioDecodeSupport: CompatibilityStatus,
    val hevcMain8HardwareEncode: CompatibilityStatus,
    val hevcMain10HardwareEncode: CompatibilityStatus,
    val surfaceInputPipelineSupport: CompatibilityStatus,
    val tenBitYuvProcessingSupport: CompatibilityStatus,
    val maxResolutionSupported: String,
    val maxFrameRateSupported: Int,
    val explanationNotes: List<String>
)

enum class CompatibilityStatus(val label: String, val badgeColor: String) {
    AVAILABLE("Available (Hardware)", "#107C41"),
    AVAILABLE_WITH_LIMITATIONS("Available (With Limitations)", "#FFD429"),
    SOFTWARE_ONLY("Software Only (CPU Fallback)", "#777777"),
    HARDWARE_UNSUPPORTED("Hardware Unsupported", "#D83B01"),
    NOT_VERIFIED("Not Physically Verified", "#8E8E93"),
    UNKNOWN("Unknown", "#333333")
}

data class InspectorWarning(
    val severity: WarningSeverity,
    val message: String,
    val technicalImpact: String
)

enum class WarningSeverity { INFO, WARNING, ERROR }
```

---

## 5. USER INTERFACE & UX DESIGN SPECIFICATIONS

The Media Inspector UI is designed strictly to uphold the visual identity outlined in `DESIGN.md`:
* **Canvas:** Editorial White (`#FFFFFF` in light mode, `#0E0E10` in dark mode).
* **Structural Framing:** 1dp solid black border (`#000000` / `#333338`) on all cards.
* **Signature Accent:** Yellow `#FFD429` reserved strictly for primary badges and highlights.
* **Typography:**
  - Standard headers/labels: Inter (`AppTypography`).
  - All codec, resolution, FourCC, hex offsets, nits, and optical specs: JetBrains Mono (`UniMonoTypography`).

### 5.1 Screen Hierarchy & Components

```
┌────────────────────────────────────────────────────────┐
│  ← Media Inspector                    [COPY] [SHARE] ⫶ │
│    fixture_a_hdr10_main10_true2020.mp4                 │
├────────────────────────────────────────────────────────┤
│  OVERVIEW CARD                                         │
│  ┌──────────┐  fixture_a_hdr10_main10_true2020.mp4     │
│  │  THUMB   │  3840x2160  •  59.94 FPS  •  9.3 KB      │
│  │ PREVIEW  │  [GENUINE HDR10]  [HEVC MAIN10]  [MP4]   │
│  └──────────┘                                          │
├────────────────────────────────────────────────────────┤
│  ▼ VIDEO STREAM (Track #0)                             │
│    Codec:          HEVC / H.265 (hvc1)                 │
│    Profile / Level:Main 10 @ Level 5.1 (Main Tier)     │
│    Resolution:     3840 x 2160 (16:9 DAR, 1:1 PAR)     │
│    Frame Rate:     59.940 FPS (Constant)               │
│    Bit Depth:      10-bit (P010 / 4:2:0 YUV)           │
│    Color Matrix:   BT.2020 Non-Constant Luminance      │
│    Color Range:    Limited (Studio 64-940)             │
├────────────────────────────────────────────────────────┤
│  ▼ AUDIO STREAM (Track #1)                             │
│    Codec:          AAC-LC (mp4a.40.2)                  │
│    Channels:       2 Channels (Stereo L/R)             │
│    Sample Rate:    48,000 Hz                           │
│    Bitrate:        192 kbps                            │
│    Language:       und (Undetermined)                  │
├────────────────────────────────────────────────────────┤
│  ▼ HDR & COLOR SCIENCE (VERIFIED)                      │
│    Mastering Display (MDCV SEI 137 / mdcv atom):       │
│      Primaries:    G(0.1700, 0.7970) B(0.1310, 0.0460) │
│                    R(0.7080, 0.2920) WP(0.3127, 0.3290)│
│      Luminance:    Min 0.0001 nits  •  Max 1000 nits   │
│      Provenance:   SOURCE_DECLARED                     │
│    Light Levels (CLLI SEI 144 / clli atom):            │
│      MaxCLL / FALL:1000 nits / 400 nits                │
│      Provenance:   SOURCE_DECLARED                     │
├────────────────────────────────────────────────────────┤
│  ▼ CONTAINER & TRACK HIERARCHY                         │
│    [+] ftyp  (Offset: 0, Size: 32 bytes)               │
│    [-] moov  (Offset: 32, Size: 4120 bytes)            │
│        ├── mvhd (Size: 108)                            │
│        ├── trak [Video] (Size: 2450)                   │
│        │   └── mdia -> minf -> stbl -> hvcC / colr...  │
│        └── trak [Audio] (Size: 1562)                   │
├────────────────────────────────────────────────────────┤
│  ▼ DEVICE COMPATIBILITY (Xiaomi Redmi 25062RN2DA)      │
│    SoC:            Snapdragon 680 (SM6225 / bengal)    │
│    Video Decode:   [AVAILABLE (Hardware)]              │
│    Audio Decode:   [AVAILABLE (Hardware)]              │
│    HEVC Main 8-bit:[AVAILABLE (Hardware)]              │
│    HEVC Main 10:   [HARDWARE UNSUPPORTED]              │
│    Notice: Hardware Main10 encoding is unsupported.    │
│    Processing HDR10 requires compatible flagship SoC.  │
├────────────────────────────────────────────────────────┤
│  [COPY TECHNICAL SUMMARY]   [EXPORT DETAILED REPORT]   │
└────────────────────────────────────────────────────────┘
```

### 5.2 Responsive & Interaction States
1. **Loading State:** Editorial spinner with animated progress description (`Analyzing container atoms...`, `Reading SPS & SEI payloads...`, `Probing MediaCodec hardware...`).
2. **Empty State:** High-contrast placeholder with prominent `OPEN FILE TO INSPECT` button triggering SAF file picker.
3. **Corrupted File State:** Clean diagnostic card showing exactly where the parser encountered EOF or malformed headers; never crashes the app.
4. **Export Options:**
   - **Copy to Clipboard:** Copies formatted Markdown summary for pasting into bug reports or messaging apps.
   - **Android Sharesheet:** Shares `.txt` report directly to Slack, Gmail, Google Drive, or messaging apps.
   - **Save File:** Prompts user via `ActivityResultContracts.CreateDocument("text/plain")` to save `UniDownco_MediaReport_<filename>.txt`.

---

## 6. ENTRY POINTS & NAVIGATION BEHAVIOR

The Media Inspector will feature three clean, intentional entry points without cluttering the main navigation tabs:

```
                  ┌───────────────────────────────┐
                  │          HomeScreen           │
                  │   (Action: Inspect File)      │
                  └───────────────┬───────────────┘
                                  │ (Launches SAF Picker)
                                  ▼
┌──────────────────┐    ┌───────────────────┐    ┌────────────────────┐
│VideoDetailsScreen│───►│MediaInspectorScreen───►│InspectionReport    │
│(Inspect Bitstream│    │(Full Inspector UI)│    │(Share / TXT Export)│
└──────────────────┘    └───────────────────┘    └────────────────────┘
          ▲                       ▲
          │                       │ (Launches SAF Picker)
┌─────────┴────────┐    ┌─────────┴─────────┐
│  LibraryScreen   │    │  SettingsScreen   │
│(Card Action/Menu)│    │(Inspect Media Tool│
└──────────────────┘    └───────────────────┘
```

1. **Primary Entry: `VideoDetailsScreen`**:
   - In `VideoDetailsScreen`, the placeholder card will be replaced with an interactive `UniPrimaryButton`:
     `"INSPECT BITSTREAM & HDR METADATA"`
   - Navigates directly to `MediaInspectorScreen` passing `downloadId`.
2. **Library Action: `LibraryScreen`**:
   - Each completed video item in `LibraryScreen` has an overflow / context menu item: `"Inspect Technical Details"`.
   - In addition, the `LibraryScreen` top app bar includes an `"INSPECT LOCAL FILE"` icon button that opens the system SAF file picker to inspect arbitrary media.
3. **Tool Entry: `SettingsScreen`**:
   - Under `"DIAGNOSTICS & SYSTEM LOGS"`, a new tool row `"Media Bitstream Inspector"` is provided with a `"CHOOSE FILE"` action button.
4. **Back-Stack Contract**:
   - The inspector opens as a full-screen Compose destination with an unambiguous back arrow in `UniTopAppBar`.
   - Pressing back or the system back gesture cleanly returns to the calling screen (`VideoDetailsScreen`, `LibraryScreen`, or `SettingsScreen`) without losing state or resetting scroll position.

---

## 7. PARSER INTEGRATION & EXPANSION STRATEGY

### 7.1 Container Support Matrix

| Container Format | Extraction Engine | Level of Inspection | Handled Features |
|---|---|---|---|
| **MP4 / M4V (`.mp4`, `.m4v`)** | `Mp4MetadataParser` + `MediaExtractor` | **Deep Atom Level** | Full ISOBMFF tree, `ftyp`, `moov`, `trak`, `stsd`, `colr`, `mdcv`, `clli`, `hvcC`, `avcC`, track IDs, language codes. |
| **QuickTime (`.mov`)** | `Mp4MetadataParser` + `MediaExtractor` | **Deep Atom Level** | QuickTime atom parsing, NCLX color matching. |
| **Matroska / WebM (`.mkv`, `.webm`)** | Android `MediaExtractor` + `MediaMetadataRetriever` | **Stream & Format Level** | Video/audio track extraction, MIME types, nominal FPS, dimensions, basic color standard/transfer. |
| **Audio Containers (`.m4a`, `.mp3`, `.opus`, `.flac`)** | Android `MediaExtractor` + `MediaMetadataRetriever` | **Stream Level** | Sample rate, channels, bitrate, duration, codec profile. |

### 7.2 Codec Support Matrix

| Codec | Video / Audio | Deep Inspection Capabilities |
|---|---|---|
| **HEVC / H.265** | Video | SPS extraction (`profile_idc`, `bit_depth`, dimensions, VUI color primaries/transfer/matrix), Prefix SEI 137 (MDCV), Prefix SEI 144 (CLLI), NAL type inventory. |
| **AVC / H.264** | Video | `avcC` configuration record, SPS profile/level IDC, chroma format, bit depth, VUI parameters. |
| **VP9** | Video | Profile 0 (8-bit) vs Profile 2 (10-bit), color primaries, transfer characteristics from `MediaFormat`. |
| **AV1** | Video | Profile 0 (Main), bit depth (8/10-bit), color space signaling via `MediaFormat`. |
| **Dolby Vision** | Video | Detects `video/dolby-vision`, extracts profile (Profile 5, 7, 8.1, 8.4) and level from track format; verifies RPU availability where accessible. |
| **AAC** | Audio | LC, HE-AAC, ELD profiles; channels (Mono, Stereo, 5.1); sample rate (8kHz - 96kHz). |
| **Opus** | Audio | Opus track format, channels, 48kHz native clock. |
| **MP3 / FLAC / PCM**| Audio | Bitrate, channels, bit depth (16/24-bit). |

### 7.3 Atom Hierarchy Extraction Expansion in `Mp4MetadataParser`
`Mp4MetadataParser` will be enhanced to build a tree of `BoxNode` objects during its seekable traversal:
- When a box is encountered, its 4-character ASCII type, 64-bit/32-bit length, and absolute file offset are recorded.
- For container boxes (`moov`, `trak`, `mdia`, `minf`, `stbl`), child boxes are recursively traversed.
- Leaf payload bytes for `colr`, `mdcv`, `clli`, `hvcC`, `avcC` are parsed into existing structured data classes without loading video payload (`mdat`) into memory.

---

## 8. STRICT HDR CLASSIFICATION & PROVENANCE POLICY

In strict accordance with Phase 1 standards-verification governance, the HDR classification engine implements an uncompromising decision table:

### 8.1 HDR Classification Decision Matrix

| Bit Depth | Profile IDC | Color Primaries | Transfer Curve | Static Metadata (MDCV) | Light Level (CLLI) | Final Classification |
|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| **10** | `2` (Main 10) | `9` (BT.2020) | `16` (ST 2084 PQ) | Verified (`mdcv` / SEI 137) | Present / Omitted per std | **`GENUINE_HDR10`** |
| **10** | `2` (Main 10) | `9` (BT.2020) | `16` (ST 2084 PQ) | Unverified / Missing | Present / Missing | **`PARTIAL_HDR`** |
| **10** | `2` (Main 10) | `9` (BT.2020) | `18` (HLG) | N/A | N/A | **`HLG`** |
| **8** | `1` (Main) | `9` (BT.2020) | `16` (ST 2084 PQ) | Any | Any | **`FAKE_HDR_REJECTED`** |
| **8** | `1` (Main) | `1` (BT.709) | `1` (BT.709) | None | None | **`SDR_BT709`** |
| **8** | Any | Unspecified | Unspecified | None | None | **`SDR_BT709`** (Web standard default) |
| Any | Any | Dolby Vision | Dolby Vision | Dynamic RPU | N/A | **`DOLBY_VISION`** |
| Any | Any | Other | Other | Any | Any | **`UNKNOWN`** |

### 8.2 Strict Rules Enforced
1. **The Anti-Fake-HDR Rule:** Any media claiming HDR or tagged with BT.2020/PQ signaling that only possesses 8-bit depth or Main profile (Profile IDC 1) is strictly classified as `FAKE_HDR_REJECTED`. The UI will display a red warning banner:
   `"Fake HDR Detected: Stream uses 8-bit quantization with HDR signaling, causing severe banding and clipping."`
2. **Zero Fabrication of Metadata:** Unavailable fields will be explicitly labeled `"UNAVAILABLE"` and colored gray. MaxCLL or MaxFALL will never be defaulted to `0` or `1000` nits if unmeasured.
3. **Conflict Exposure:** If MP4 container atom `colr` declares BT.709 but the underlying HEVC SPS VUI declares BT.2020, the UI will display a `MetadataConflictAlert` card detailing the discrepancy instead of picking the most favorable value.

---

## 9. HARDWARE COMPATIBILITY & HONEST LIMITATIONS REPORTING

The Media Inspector will directly query `HardwareCapabilityRegistry.getCapabilities()` and cross-reference the inspected file against the host device's actual silicon:

### 9.1 Compatibility Evaluation Rules
1. **Video Decodability**: Probes `MediaCodecList` for a decoder matching `videoMime`. If an accelerated hardware decoder exists, reports `AVAILABLE (Hardware)`. If only a software decoder exists (`c2.android.*` or `OMX.google.*`), reports `SOFTWARE_ONLY`.
2. **Audio Decodability**: Probes `MediaCodecList` for audio decoder support.
3. **HEVC Main 8-bit Transcoding**: Checks `HardwareCapabilities.supportsHardwareMain`.
4. **HEVC Main 10 Transcoding**: Checks `HardwareCapabilities.supportsHardwareMain10`.

### 9.2 Explicit Xiaomi SM6225 Hardware Profile Enforcement
When running on the project's physical reference test device (Xiaomi Redmi `25062RN2DA`, Qualcomm Snapdragon 680 / `SM6225` / `bengal`):
* `HEVC Main 8-bit Hardware Encode`: **`AVAILABLE`** (`c2.qti.hevc.encoder`).
* `HEVC Main 10 Hardware Encode`: **`HARDWARE_UNSUPPORTED`** (Qualcomm Adreno 610 / SM6225 hardware encoder only supports profile 1).
* `Notice Displayed`:
  `"Device limitation: Snapdragon 680 (SM6225) does not support HEVC Main10 hardware encoding. 10-bit HDR encoding requires Snapdragon 8 Gen 3 or flagship SoC."`
* **Anti-Fabrication:** Snapdragon 8 Gen 3 compatibility will be marked **`NOT_VERIFIED (Hardware unavailable)`**, adhering strictly to the verified physical baseline.

---

## 10. FILE ACCESS, SECURITY & SCOPED STORAGE COMPLIANCE

1. **Scoped Storage Compliance (API 29 - 36):**
   - UniDownco files stored in `context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)` are accessed directly via `File` and `FileInputStream`.
   - MediaStore Gallery exports in `Movies/UniDownco/` are resolved via `MediaStore.Video.Media.EXTERNAL_CONTENT_URI`.
   - External user-picked files are accessed strictly via Android's Storage Access Framework (`ActivityResultContracts.OpenDocument` or `GetContent`).
   - **Zero Permission Elevation:** `MANAGE_EXTERNAL_STORAGE` will **NOT** be introduced.
2. **Bounded Resource Consumption:**
   - Files are never read into memory as giant `ByteArray` buffers.
   - Container atoms are traversed using seekable reads (reading 8-byte headers, seeking across data payloads).
   - Bitstream inspection uses small bounded buffers (64KB chunks) to scan for parameter sets and SEI NALs.
   - SHA-256 calculation is bounded and streamed in 64KB blocks on a background thread.
3. **Privacy Redaction:**
   - When generating exportable reports (`.txt` or clipboard), absolute filesystem paths like `/data/user/0/com.unidownco/files/...` are automatically redacted to `<app_storage>/...` to protect user privacy.

---

## 11. TESTING & VERIFICATION STRATEGY

### 11.1 Host Unit Test Suite (`app/src/test/java/com/unidownco/`)
Add 15+ comprehensive unit tests covering:
1. `MediaInspectorEngineTest`:
   - Inspect Fixture A (Genuine HDR10 Main10 True BT.2020) -> verify exact metadata, primaries, nits, and provenance.
   - Inspect Fixture B (Genuine HDR10 Main10 DCI-P3 D65 uncalculated CLLI) -> verify standards-compliant omission.
   - Inspect Fixture C (SDR Main 8-bit) -> verify SDR classification.
   - Inspect Fixture D (Fake HDR 8-bit) -> verify strict `FAKE_HDR_REJECTED` status.
2. `HdrClassificationTest`:
   - Test all 8 permutation rows of the decision matrix.
   - Verify conflict alert generation when container and bitstream disagree.
3. `ContainerAtomHierarchyTest`:
   - Verify recursive `BoxNode` construction for `moov`, `trak`, `mdia`, `stbl`, `stsd`.
4. `ReportExportFormattingTest`:
   - Verify Markdown, TXT, and JSON formatting with privacy redactions.

### 11.2 On-Device Instrumentation Test Suite (`app/src/androidTest/java/com/unidownco/`)
Execute against the connected physical Xiaomi Redmi 25062RN2DA (SM6225):
1. `testInspectRealDownloadedVideo_GeneratesValidReport`: Verifies complete inspection of a file generated by UniDownco's downloader.
2. `testInspectExternalMediaViaUri_HandlesSafPermissions`: Verifies non-crashing inspection of a content URI via `ContentResolver`.
3. `testDeviceCompatibilityCard_ReflectsActualSm6225Hardware`: Confirms `c2.qti.hevc.encoder` is reported as Main 8-bit available, Main10 hardware unsupported.
4. `testInspectCorruptedFile_DoesNotCrashUi`: Injects random corrupted bytes; confirms graceful error report without application crash.
5. `testInspectorPerformance_DoesNotBlockMainThread`: Confirms UI renders at 60 FPS while background analysis runs on `Dispatchers.IO`.

### 11.3 Regression Protection
The existing baseline must be protected without regression:
* **Host Unit Tests:** Must remain **≥ 96 passed** (zero failures, zero skipped).
* **Physical Device Tests:** Must remain **≥ 18 passed** on Xiaomi SM6225.
* **Build Integrity:** `.\gradlew.bat assembleDebug` and `assembleRelease` must succeed with exit code 0.

---

## 12. STEP-BY-STEP IMPLEMENTATION ROADMAP (5 SUBPHASES)

To maintain strict architectural control and approval gating, Phase 2 is broken down into five sequential, independently testable subphases:

```
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 2.1: Domain Models, Box Hierarchy & Extended Parser Core         │
├────────────────────────────────────────────────────────────────────────┤
│ Phase 2.2: Media Inspector Engine & Multi-Source File Resolver         │
├────────────────────────────────────────────────────────────────────────┤
│ Phase 2.3: HDR Classification Engine & Device Compatibility Evaluator  │
├────────────────────────────────────────────────────────────────────────┤
│ Phase 2.4: Jetpack Compose Media Inspector UI & Report Exporters       │
├────────────────────────────────────────────────────────────────────────┤
│ Phase 2.5: Navigation Wiring, SAF Picker & Physical Device Acceptance  │
└────────────────────────────────────────────────────────────────────────┘
```

### Subphase 2.1: Domain Models, Box Hierarchy & Extended Parser Core
* **Objective:** Establish the immutable domain models (`MediaInspectionReport`, `StreamDetails`, `HdrColorDetails`, `BoxNode`) and extend `Mp4MetadataParser` to extract hierarchical atom trees.
* **Files to Create / Modify:**
  - `app/src/main/java/com/unidownco/engine/inspector/model/MediaInspectionReport.kt` (New)
  - `app/src/main/java/com/unidownco/engine/inspector/model/StreamDetails.kt` (New)
  - `app/src/main/java/com/unidownco/engine/inspector/model/HdrColorDetails.kt` (New)
  - `app/src/main/java/com/unidownco/engine/inspector/model/ContainerDetails.kt` (New)
  - `app/src/main/java/com/unidownco/engine/parser/Mp4MetadataParser.kt` (Extend with `parseBoxHierarchy()`)
  - `app/src/test/java/com/unidownco/ContainerAtomHierarchyTest.kt` (New)
* **Unit Tests:** Verify atom hierarchy construction on Fixtures A, B, C, D.

### Subphase 2.2: Media Inspector Engine & Multi-Source File Resolver
* **Objective:** Implement `MediaSourceResolver` (handling `File` and SAF `Uri`) and `MediaInspectorEngine` to orchestrate multi-track framework extraction and deep bitstream analysis.
* **Files to Create / Modify:**
  - `app/src/main/java/com/unidownco/engine/inspector/MediaSourceResolver.kt` (New)
  - `app/src/main/java/com/unidownco/engine/inspector/MediaInspectorEngine.kt` (New interface)
  - `app/src/main/java/com/unidownco/engine/inspector/MediaInspectorEngineImpl.kt` (New implementation)
  - `app/src/test/java/com/unidownco/MediaInspectorEngineTest.kt` (New)
* **Unit Tests:** Test multi-track extraction, VFR detection, audio channel mapping, and corrupted file handling.

### Subphase 2.3: HDR Classification Engine & Device Compatibility Evaluator
* **Objective:** Implement the strict HDR decision matrix, anti-fake-HDR rejection rule, metadata provenance verification, and silicon capability cross-referencing.
* **Files to Create / Modify:**
  - `app/src/main/java/com/unidownco/engine/inspector/HdrClassifier.kt` (New)
  - `app/src/main/java/com/unidownco/engine/inspector/DeviceCompatibilityEvaluator.kt` (New)
  - `app/src/test/java/com/unidownco/HdrClassificationTest.kt` (New)
* **Unit Tests:** Validate rejection of Fixture D (fake HDR), acceptance of Fixture A & B, and accurate SM6225 limitation reporting.

### Subphase 2.4: Jetpack Compose Media Inspector UI & Report Exporters
* **Objective:** Build the complete editorial Jetpack Compose UI following `DESIGN.md`, including overview cards, collapsible track cards, HDR optical details, atom tree viewer, and report exporters (Clipboard, Sharesheet, SAF `.txt`).
* **Files to Create / Modify:**
  - `app/src/main/java/com/unidownco/feature/inspector/MediaInspectorViewModel.kt` (New)
  - `app/src/main/java/com/unidownco/feature/inspector/MediaInspectorScreen.kt` (New)
  - `app/src/main/java/com/unidownco/feature/inspector/components/MediaOverviewCard.kt` (New)
  - `app/src/main/java/com/unidownco/feature/inspector/components/VideoStreamCard.kt` (New)
  - `app/src/main/java/com/unidownco/feature/inspector/components/AudioStreamCard.kt` (New)
  - `app/src/main/java/com/unidownco/feature/inspector/components/HdrColorScienceCard.kt` (New)
  - `app/src/main/java/com/unidownco/feature/inspector/components/ContainerHierarchyCard.kt` (New)
  - `app/src/main/java/com/unidownco/feature/inspector/components/DeviceCompatibilityCard.kt` (New)
  - `app/src/main/java/com/unidownco/feature/inspector/components/InspectionReportDialog.kt` (New)
* **Tests:** Compose screenshot and layout verification; TalkBack accessibility verification.

### Subphase 2.5: Navigation Wiring, SAF Picker & Physical Device Acceptance
* **Objective:** Wire navigation entry points in `VideoDetailsScreen`, `LibraryScreen`, and `SettingsScreen`. Integrate SAF file picker for external media. Execute the full connected test suite on the physical Xiaomi SM6225 device.
* **Files to Create / Modify:**
  - `app/src/main/java/com/unidownco/MainActivity.kt` (Add inspector destination)
  - `app/src/main/java/com/unidownco/feature/details/VideoDetailsScreen.kt` (Add "Inspect Bitstream" button)
  - `app/src/main/java/com/unidownco/feature/library/LibraryScreen.kt` (Add "Inspect File" action)
  - `app/src/main/java/com/unidownco/feature/settings/SettingsScreen.kt` (Add tool card)
  - `app/src/androidTest/java/com/unidownco/MediaInspectorDeviceTest.kt` (New instrumentation test suite)
* **Physical Device Tests:** Execute `.\gradlew.bat connectedDebugAndroidTest` on Xiaomi SM6225; verify 0 crashes, 0 UI freezes, and 100% test pass rate.

---

## 13. RISKS & MITIGATION STRATEGY

| Risk | Severity | Mitigation Strategy |
|---|---|---|
| **OOM on Large 4K/8K Media Files** | High | Never read files into memory buffers. Use strictly seekable `SeekableInputSource` and bounded 64KB chunk readers for atom traversal and NAL scanning. |
| **Crashing on Corrupted / Truncated Streams** | High | All bitstream parsers (`HevcBitstreamParser`, `Mp4MetadataParser`) are wrapped in non-crashing `try/catch` boundaries that log diagnostic warnings and return partial reports. |
| **Android SAF Permission Expiration** | Medium | Check `ContentResolver` read permission before opening stream; display a friendly `PermissionRevoked` card prompting user to re-select file if revoked. |
| **Conflicting Framework vs Bitstream Data** | Medium | Do not silently pick the most favorable value; expose discrepancies via `MetadataConflictAlert` in the HDR details card. |
| **Main Thread Freezing During Bitstream Parse** | Medium | All analysis operations run strictly on `Dispatchers.IO` with cooperative `ensureActive()` cancellation checks. |

---

## 14. ESTIMATED IMPLEMENTATION COMPLEXITY & BUDGET

* **New Kotlin Source Files:** ~15 files (~1,800 lines of code)
* **Modified Source Files:** ~5 files (~150 lines of code)
* **New Test Files:** 4 files (~600 lines of code)
* **Total Estimated LoC Delta:** ~2,550 lines of code
* **External Dependencies Added:** **0 (Zero)**
* **Database Migrations Required:** **0 (None)**
* **Physical Device Testing:** Xiaomi Redmi 25062RN2DA (SM6225 / bengal).

---

## 15. APPROVAL GATE & CURRENT STATUS

```
================================================================================
PHASE 2 IMPLEMENTATION: NOT STARTED
AWAITING USER APPROVAL
================================================================================
```

Per the strict governance of Master Product and Technical Roadmap Revision 1.1:
* **No application source code has been modified.**
* **No Room database schema has been changed.**
* **No dependencies have been added.**
* **No releases, APKs, or Git tags have been generated.**

The implementation of Phase 2 will commence only after the user explicitly reviews and approves this implementation plan.
