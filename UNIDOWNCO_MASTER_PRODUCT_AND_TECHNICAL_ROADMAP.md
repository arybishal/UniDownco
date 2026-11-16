# UNIDOWNCO MASTER PRODUCT & TECHNICAL ROADMAP
## Architecture, Hardware Strategy, Phased Implementation & Verification Plan

**Project:** UniDownco  
**Application ID (Release):** `com.unidownco`  
**Application ID (Debug):** `com.unidownco.debug`  
**Application ID (Test):** `com.unidownco.debug.test`  
**Workspace:** `D:\Webapp\UniDownco`  
**Repository:** `https://github.com/arybishal/UniDownco`  
**Base Branch:** `master`  
**Document Status:** MASTER ROADMAP (PLANNING ONLY — STRICTLY ZERO CODE MODIFICATIONS)  
**Target Environments:**
- **Baseline Connected Physical Device:** Xiaomi Redmi Note 25062RN2DA (Qualcomm SM6225 / bengal / Adreno 610, Android 16 / API 36)
- **Flagship Target Platform:** OnePlus Ace 5 / OnePlus 13R class (Qualcomm Snapdragon 8 Gen 3 SM8650-AB / Adreno 750, Android 15 / API 35+)

---

## 1. EXECUTIVE SUMMARY & MISSION

UniDownco is evolving from a single-stream Android media downloader and HEVC encoder into a comprehensive, high-reliability **Media Downloading, Management, and Processing Studio**.

The studio unites three core pillars:
1. **Universal Resilient Media Downloader:** Multi-platform extraction (YouTube, Instagram, Facebook, TikTok, Reddit, X, Direct HTTP) powered by an in-app `yt-dlp` companion runtime, native progressive chunking, adaptive DASH stream remuxing, batch processing, playlist extraction, and background execution.
2. **Built-in Media Toolkit & Smart Library:** Frame-accurate video trimming, CRF/bitrate compression, lossless audio extraction, high-resolution thumbnail generation, container conversion, deep metadata inspection, categorized asset management, duplicate detection, and storage analytics.
3. **Hardware-Accelerated Video Enhancement & Color Studio:** Android `MediaCodec` hardware encoding, GPU-accelerated fused shader pipelines (linearization, 3x3 gamut transforms, inverse tone mapping, ST 2084 PQ curve), 3D LUT grading (`.cube`), parametric film grain, watermark/logo composition, and honest hardware gating across heterogeneous SoC tiers.

### Fundamental Architectural Constraints
- **Zero Hallucinated Hardware Capabilities:** No device without verified silicon capabilities will advertise or attempt genuine 10-bit HEVC Main10 encoding. Devices lacking 10-bit hardware encoders (such as the Qualcomm SM6225) are strictly gated to 8-bit Main SDR with explicit user-facing diagnostic badges.
- **Zero Fake Metadata:** No SDR file will ever be tagged with fake HDR static metadata or labeled as HDR10.
- **Mandatory Stage 2.3.1.1 Gate:** The existing Stage 2.3.1 implementation of the 10-bit HDR pipeline is recognized as code-complete but **unverified on physical Main10 silicon**. Prior to building dependent enhancement features (such as 3D LUTs and grading presets), `STAGE 2.3.1.1` must execute physical verification, metadata byte-layout audits, and bitstream NAL unit inspection.
- **Strict Single Source of Truth:** All user interfaces strictly adhere to [DESIGN.md](file:///D:/Webapp/UniDownco/DESIGN.md) (White Canvas, Black Structure, Yellow `#FFD429` Accent, Inter & JetBrains Mono typography).

---

## 2. CURRENT VERIFIED ARCHITECTURE & TECHNICAL BASELINE

A comprehensive audit of the workspace reveals the following verified baseline:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRESENTATION LAYER                             │
│       Jetpack Compose (BOM 2024.10.01) + Navigation Compose + M3 + MVVM    │
│  [HomeScreen]    [QueueScreen]    [LibraryScreen]   [VideoDetails]  [Settings]│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ StateFlow / Intents
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                                DOMAIN LAYER                                 │
│  UseCases: EnqueueDownload, ExtractVideoInfo, ManageDownloadQueue, Validate │
│  Domain Models: DownloadItem, VideoExtractionInfo, FormatOption, Presets   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                                 DATA LAYER                                  │
│  DownloadRepository ◄───────────────┬────────────────► ProcessingJobRepo    │
│         │                           │                          │            │
│         ▼                           ▼                          ▼            │
│  [Room Database v3]      [UniversalExtractorRegistry]  [StorageManager]     │
│  - downloads             - InstagramMediaExtractor     - Movies/downloads/  │
│  - video_metadata        - YtDlpExtractorProvider      - Movies/originals/  │
│  - processing_jobs       - NativeExtractorProvider     - Scoped Storage     │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                                ENGINE LAYER                                 │
│  ┌─────────────────────────┐                 ┌───────────────────────────┐  │
│  │    DOWNLOAD ENGINES     │                 │     PROCESSING ENGINE     │  │
│  │ - DownloadEngine (HTTP) │                 │ - VideoProcessingEngine   │  │
│  │ - YtDlpEngineManager    │                 │ - MediaCodecVideoDecoder  │  │
│  │ - InstagramDownloadEng  │                 │ - HdrRenderCore (EGL 1.4) │  │
│  │ - MediaStreamMuxer      │                 │ - SdrToHdr10Shader (GLSL) │  │
│  └─────────────────────────┘                 │ - MediaCodecVideoEncoder  │  │
│                                              │ - MediaMuxerController   │  │
│                                              │ - ProcessingOutputValid.  │  │
│                                              └───────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ HARDWARE & VERIFICATION: HardwareCapabilityRegistry, AndroidVerifier │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1. Build & Runtime Environment
- **JDK:** Java 17 (Zulu 17.0.12)
- **Android Gradle Plugin:** 8.7.2
- **Gradle Wrapper:** 8.10.2
- **Kotlin:** 2.0.21 (Compose Compiler Plugin integrated)
- **Compile SDK:** 35 | **Target SDK:** 35 | **Min SDK:** 26 (Android 8.0 Oreo)
- **Package Identifiers:**
  - Release APK: `com.unidownco`
  - Debug APK: `com.unidownco.debug`
  - Instrumentation Test APK: `com.unidownco.debug.test`

### 2. Downloader Architecture
- **Companion Runtime:** `youtubedl-android:0.18.1` wrapping CPython 3.8 and FFmpeg 4.4.
- **Resource Overlay:** Bundled updated `yt-dlp` (`2026.10.26`) in `app/src/main/res/raw/ytdlp` resolving Meta API `doc_id` deprecation and YouTube visitor data requirements.
- **Provider Priority:** `InstagramMediaExtractor` (Priority 105) $\rightarrow$ `YtDlpExtractorProvider` (Priority 100) $\rightarrow$ `NativeExtractorProvider` (Priority 80).
- **Foreground Service:** `DownloadForegroundService` running as type `dataSync` with active notification channel and speed/progress updates.
- **Muxing Engine:** `MediaStreamMuxer` executing dual-stream adaptive DASH pairing without re-encoding.

### 3. Video Processing Pipeline
- **Decoder:** `MediaCodecVideoDecoder` feeding decoded YUV buffers to an OpenGL texture surface.
- **GPU Render Core:** `HdrRenderCore` managing EGL 1.4 display, context, and window surface with 10-bit `RGBA_1010102` (`EGL_RECORDABLE_ANDROID`) configuration and 8-bit `RGBA_8888` fallback.
- **Shader Core:** `SdrToHdr10Shader` executing fused Rec.709 linearization, 3x3 Rec.2020 matrix transform, ITU-R BT.2408 Adaptive Knee Inverse Tone Mapping (anchored at 203 nits diffuse white), and SMPTE ST 2084 PQ non-linear encoding.
- **Encoder:** `MediaCodecVideoEncoder` targeting `video/hevc` (`HEVCProfileMain` for 8-bit, `HEVCProfileMain10` for 10-bit).
- **Container Muxer:** `MediaMuxerController` assembling video elementary stream and extracting/passing audio elementary streams (AAC/MP3/Opus).
- **Validation Engine:** `ProcessingOutputValidator` checking file size, audio/video track presence, sync tolerances, container health, and HDR color metadata (`COLOR_STANDARD_BT2020`, `COLOR_TRANSFER_ST2084`, `bitDepth == 10`).

### 4. Database Schema (Room v3)
- `downloads`: Master download record with URL, title, platform, resolution, status, downloaded bytes, timestamps.
- `video_metadata`: One-to-one foreign key relationship with `downloads`, storing container, codecs, dimensions, frame rate, bit depth, color primaries, transfer curve, and HDR classification.
- `processing_jobs`: One-to-one/many foreign key relationship with `downloads`, tracking transcode presets, stage (`QUEUED`, `ANALYZING`, `ENCODING`, `VERIFYING`, `COMPLETED`, `FAILED`), progress percent, FPS, and atomic output paths.

---

## 3. EXISTING FEATURE CLASSIFICATION & GAP AUDIT

| Feature / Component | Classification | Verification Status & Notes |
|---|---|---|
| **Single-URL Downloader** | **Implemented & Verified** | Verified on YouTube, Facebook, Direct HTTP. |
| **Instagram Reel/Post Downloader** | **Implemented & Verified** | Canonical URL resolver + bundled zipapp verified on physical device. |
| **Download Queue & Lifecycle** | **Implemented & Verified** | Pause, resume, cancel, retry, and startup reconciliation verified. |
| **Room Database v3** | **Implemented & Verified** | Full CRUD, foreign keys with cascading delete, indexes verified. |
| **Scoped Storage & File Protection** | **Implemented & Verified** | Atomic `.part` buffers, SHA-256 source file immutability verified. |
| **MediaCodec 8-Bit HEVC Encoding** | **Implemented & Verified** | Transcode, audio passthrough, rotation preservation verified on device. |
| **Hardware Capability Discovery** | **Implemented & Verified** | Real-time probe of encoders, profiles, and thermal states verified. |
| **Settings & Diagnostics UI** | **Implemented & Verified** | Compliant with DESIGN.md; shows encoder diagnostics and honest gating. |
| **True 10-Bit Pipeline (Stage 2.3.1)** | **Implemented but Unverified** | Math & shaders complete; **physical Main10 silicon execution NOT verified**. |
| **CTA-861.3 Static Metadata** | **Implemented but Unverified** | 25-byte buffer implemented; needs bitstream SEI NAL unit verification. |
| **Batch Downloads (Phase A1)** | **Not Implemented** | Only single-URL input supported in current `HomeScreen`. |
| **Playlist Downloads (Phase A2)** | **Not Implemented** | Multi-item extraction and bulk playlist queueing absent. |
| **Download Scheduling (Phase A3)** | **Not Implemented** | No `WorkManager` or `AlarmManager` time/charging triggers. |
| **Intelligent Error Classifier (A4)** | **Partially Implemented** | Basic retry exists; lacks HTTP 429 backoff and transient classifier. |
| **Format/Quality Picker Modal (A5)**| **Partially Implemented** | Bottom sheet shows formats; lacks granular codec/bitrate control. |
| **Subtitle Management (A6)** | **Not Implemented** | Discovery, `.srt`/`.vtt` extraction, and MP4 embedding absent. |
| **Secure Session Store (A7)** | **Not Implemented** | No Android Keystore credential or cookie vault. |
| **Video Trimmer (Phase B1)** | **Not Implemented** | No trimming UI or stream-copy engine. |
| **Video Compressor (Phase B2)** | **Not Implemented** | Encoding exists; lacks dedicated CRF/target-size compressor UI. |
| **Audio Extractor (Phase B3)** | **Not Implemented** | Muxer extracts AAC during transcode; lacks dedicated audio export tool. |
| **Thumbnail Extractor (Phase B4)** | **Not Implemented** | Lacks timestamp-based high-res image export. |
| **Media Format Converter (Phase B5)** | **Not Implemented** | Lacks multi-format remux/transcode selection (MKV/WebM/MP4). |
| **Deep Media Inspector (Phase B6)** | **Partially Implemented** | `VideoDetailsScreen` shows basic specs; lacks deep NAL/SEI inspection. |
| **Smart Library Categorization (C1)**| **Partially Implemented** | Library lists all downloads; lacks Audio/Video/Subtitles tabs. |
| **Advanced Filtering & Search (C2)** | **Partially Implemented** | Search by title exists; lacks codec, resolution, and date filtering. |
| **Duplicate Detection (Phase C3)** | **Not Implemented** | Lacks hash-based duplicate scanner. |
| **Storage Dashboard (Phase C4)** | **Not Implemented** | Lacks visual storage breakdown and cache cleaner. |
| **History Backup/Export (Phase C5)** | **Not Implemented** | Lacks JSON database export/import. |
| **Custom Collections (Phase C6)** | **Not Implemented** | Lacks user-created playlists and tags. |
| **Frame Rate Conversion (Phase D1)** | **Not Implemented** | Video processing preserves source FPS; lacks 25/30/50/60 conversion. |
| **Cinematic Color Grading (Phase D2)**| **Not Implemented** | Lacks exposure, contrast, saturation, tint shader uniforms. |
| **3D LUT Support (Phase D3)** | **Not Implemented** | Lacks `.cube` parser and 3D OpenGL texture interpolation. |
| **Film Grain Simulation (Phase D4)** | **Not Implemented** | Lacks parametric noise shader and HDR-aware luminance mask. |
| **Text/Logo Watermarks (Phase D5)** | **Not Implemented** | Lacks overlay compositor and subtitle burn-in. |
| **HDR10+ Dynamic Metadata (Phase D7)**| **Not Implemented** | Requires SMPTE ST 2094-40 dynamic tone mapping SEI generation. |
| **Dolby Vision Authoring (Phase D8)** | **Blocked / Excluded** | Proprietary Dolby licensing and encoder SDK preclude open authoring. |
| **AI Motion Interpolation (60 FPS)** | **Blocked / Research** | Real-time optical flow on mobile SoC requires dedicated NPU pipeline. |

---

## 4. MANDATORY STAGE 2.3.1.1: HDR10 METADATA CORRECTION & END-TO-END BITSTREAM VERIFICATION

Before any Stage 2.3.2 feature (Color Grading, LUTs, Grain) is developed, **Stage 2.3.1.1 is mandatory** to resolve the 5 outstanding technical concerns.

```
                    STAGE 2.3.1.1 VERIFICATION WORKFLOW
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. Dynamic Content Light Level Analysis (Replace Hardcoded 1000/400)        │
│    - Compute real MaxCLL (maximum pixel value) during decoder pass          │
│    - Compute real MaxFALL (frame average luminance) over all frames         │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│ 2. Binary Layout & Container Atom Alignment                                 │
│    - Verify CTA-861.3 25-byte Little-Endian buffer structure                │
│    - Verify MP4 'mdcv' (Mastering Display) atom byte order (Big-Endian)     │
│    - Verify MP4 'clli' (Content Light Level) atom byte order (Big-Endian)    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│ 3. Bitstream SEI NAL Unit Inspection                                        │
│    - Inspect HEVC SPS: profile_idc == 2 (Main10), bit_depth_luma == 10      │
│    - Inspect Prefix SEI payloadType 137 (Mastering Display Colour Volume)   │
│    - Inspect Prefix SEI payloadType 144 (Content Light Level Info)          │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│ 4. Adreno EGL 10-Bit Surface Delivery Proof                                 │
│    - Verify EGL_RECORDABLE_ANDROID + RGBA_1010102 delivers unclipped data  │
│    - Confirm absence of 8-bit intermediate downsampling in SurfaceFlinger   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│ 5. Physical Silicon Execution on Main10 Hardware (SM8650 Target)            │
│    - Execute full transcode on Snapdragon 8 Gen 3 hardware                  │
│    - Independent bitstream validation via MediaExtractor and FFprobe        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Specific Technical Tasks for Stage 2.3.1.1
1. **Dynamic Content Light Level Computation:**
   - In `VideoProcessingEngine`, replace static `HdrStaticMetadata.BT2020_1000NITS` with a two-pass or real-time analyzer that computes:
     - $\text{MaxCLL} = \max_{x,y,t}(L(x,y,t))$
     - $\text{MaxFALL} = \frac{1}{T}\sum_{t=1}^T \left(\frac{1}{N}\sum_{x,y} L(x,y,t)\right)$
   - If single-pass real-time encoding is used, calibrate metadata against the known mathematical limits of the BT.2408 expansion curve ($1,000\text{ nits peak}$, $203\text{ nits diffuse}$) and inject measured source statistics.
2. **Container Atom & SEI NAL Verification:**
   - Create a bitstream parser verifying that:
     - The output MP4 file contains the `colr` atom with `colour_type = 'nclx'`, `colour_primaries = 9` (BT.2020), `transfer_characteristics = 16` (SMPTE ST 2084), `matrix_coefficients = 9` (BT.2020 non-constant luminance), and `full_range_flag = 0`.
     - The MP4 contains valid `mdcv` and `clli` box structures.
     - The HEVC elementary stream contains valid SEI NAL units (`nal_unit_type = 39` for `PREFIX_SEI_NUT`).
3. **Hardware Execution Requirement:**
   - Until a physical device with HEVC Main10 hardware encoding is connected and verified, the status must remain strictly marked: `UNVERIFIED ON PHYSICAL MAIN10 HARDWARE`.

---

## 5. COMPLETE FEATURE INVENTORY ACROSS PHASES

### Phase A: Advanced Downloader Studio
- **A1. Batch Downloads:** Multi-URL text input dialog, clipboard URL parser, duplicate filtering, aggregate queue progress notification, per-item status cards, bulk cancel/pause.
- **A2. Playlist Downloads:** YouTube playlist metadata parser (`--flat-playlist`), individual track selection modal, sequential enqueueing, resilient handling of private/deleted entries.
- **A3. Download Scheduling:** `WorkManager` background scheduler, exact/windowed start time, Wi-Fi-only restriction, charging-only restriction, battery optimization exemption handling.
- **A4. Intelligent Error Classifier & Recovery:** Distinct failure categorization (`NETWORK_TIMEOUT`, `HTTP_429_RATE_LIMITED`, `AUTHENTICATION_REQUIRED`, `STORAGE_INSUFFICIENT`, `PARSER_DEPRECATED`), exponential backoff with jitter ($2^n \cdot 1000\text{ms}$), permanent error non-retry gating.
- **A5. Granular Format & Quality Selection:** Pre-download stream probe dialog displaying available resolutions (4K, 1440p, 1080p, 720p, 480p, 360p), container formats (MP4, WebM), audio bitrates (128k, 256k, 320k), codecs (HEVC, AVC, VP9, AV1), and estimated file sizes.
- **A6. Subtitle Management:** Multi-language subtitle discovery (`yt-dlp --list-subs`), manual vs automatic caption distinction, `.vtt` to `.srt` conversion, standalone file export, optional soft subtitle track muxing into MP4.
- **A7. Secure Session Management:** Encrypted credential/cookie vault backed by `AndroidKeyStore` (`AES-256-GCM`), masked user inputs, strict zero-logging policy, exclusion from Android Auto Backup.
- **A8. Multi-Platform Reliability:** Dedicated routing for Instagram Reels, YouTube DASH, Facebook HD, TikTok watermark-free streams, Reddit v.redd.it audio pairing, and Twitter/X syndication variants.

### Phase B: Built-in Media Toolkit
- **B1. Video Trimmer:** Interactive frame-accurate scrubber with thumbnail filmstrip, start/end marker handles, fast-path lossless stream-copy trimming (cutting on I-frames via `MediaExtractor` + `MediaMuxer`), fallback transcode trimming for sub-GOP accuracy.
- **B2. Video Compressor:** Target file size mode (calculates required video bitrate $R_v = \frac{\text{TargetSize} - \text{AudioSize}}{\text{Duration}}$), CRF visual quality presets (Fast 720p, Balanced 1080p, Archive HEVC), real-time compression ratio display.
- **B3. Audio Extractor:** Lossless stream extraction (demuxing AAC/Opus into `.m4a` / `.opus`), transcode extraction to MP3 / FLAC via in-app engine, ID3/Vorbis metadata tag preservation.
- **B4. Thumbnail & Frame Extractor:** High-precision timestamp seeker using `MediaMetadataRetriever.OPTION_CLOSEST`, PNG / JPEG format export, selectable output resolution.
- **B5. Media Format Converter:** Container remuxing (MKV $\rightarrow$ MP4, WebM $\rightarrow$ MP4) without re-encoding when streams are compatible, full transcode fallback when target container requires codec conversion.
- **B6. Deep Media Inspector:** Technical modal displaying JetBrains Mono specs: container atoms, video codec profile/level, exact frame rate, variable frame rate (VFR) timestamps, bit depth, color primaries, transfer curve, matrix, HDR format, audio channels, sample rate, bitrates.

### Phase C: Smart Download Library
- **C1. Multi-Type Organization:** Navigation tabs: All, Videos, Audio, Subtitles, Processed Studio Outputs.
- **C2. Advanced Filtering & Multi-Sort:** Platform filters (YouTube, Instagram, etc.), resolution filters (4K, 1080p, etc.), codec filters (HEVC, AVC), HDR filters (HDR10, SDR), multi-criteria sort (Date, Size, Name, Duration).
- **C3. Duplicate Scanner:** Fast hash comparison (SHA-256) of downloaded files, warning prompts before downloading identical media, safe deletion confirmation ensuring original file is never deleted without confirmation.
- **C4. Storage Analytics Dashboard:** Visual disk space breakdown (UniDownco media vs total storage), temporary cache cleaner (orphaned `.part` and processing cache), largest media file highlights.
- **C5. History Backup & Migration:** Database export to encrypted or clean JSON, historical queue restoration, cross-device migration compatibility.
- **C6. Custom Collections:** User-created tags and playlists (e.g. "Favorites", "Travel", "Tutorials"), batch export to Android Gallery.

### Phase D: Advanced Video Enhancement
- **D1. Frame Rate Conversion:** Configurable output frame rate targets (25, 30, 50, 60 FPS, Original). PTS retiming, drop-frame / duplicate-frame cadence generation. Explicit technical distinction between cadence conversion and optical-flow motion interpolation.
- **D2. Cinematic Color Grading:** Shader-based real-time parameter uniforms: Exposure ($\pm 2.0\text{ EV}$), Contrast ($0.5 - 1.5$), Saturation ($0.0 - 2.0$), White Balance (Temperature / Tint), Highlights / Shadows lift/gamma/gain.
- **D3. 3D LUT Support:** Full `.cube` parser supporting standard $17\times 17\times 17$ and $33\times 33\times 33$ 3D color lookup tables, uploaded as 3D OpenGL textures (`GL_TEXTURE_3D`) with hardware trilinear interpolation.
- **D4. Film Grain Simulation:** High-performance parametric procedural pseudo-random grain generator in GLSL, dynamic temporal seed per frame, luminance-weighted masking (preventing grain clumping in deep blacks and blown-out highlights).
- **D5. Text & Logo Overlays:** 2D alpha texture compositor, custom text rendering (typography, scale, color, opacity), logo PNG watermark positioning (Top-Left, Top-Right, Bottom-Left, Bottom-Right, Center), time-bounded display windows ($t_{start} \rightarrow t_{end}$).
- **D6. Genuine HDR10 Implementation:** End-to-end 10-bit pipeline, Stage 2.3.1.1 verification, verified BT.2020 primaries, ST 2084 transfer curve, and valid CTA-861.3 static metadata.
- **D7. HDR10+ Evaluation:** Dynamic metadata investigation (SMPTE ST 2094-40). Evaluation of Android 13+ `KEY_HDR10_PLUS_INFO` support.
- **D8. Dolby Vision Evaluation:** Documenting proprietary profile boundaries (Profile 5 proprietary IPTPQc2, Profile 8.4 cross-compatibility). Explicitly gating authoring as excluded.
- **D9. Fused Multi-Stage GPU Pipeline:** Dynamic shader stage builder chaining active operations into minimal render passes:
  $$\text{Decode} \rightarrow \text{Linearize} \rightarrow \text{Gamut Transform} \rightarrow \text{Color Grade} \rightarrow \text{3D LUT} \rightarrow \text{Tone Map / PQ} \rightarrow \text{Grain} \rightarrow \text{Watermark} \rightarrow \text{Encode}$$

### Phase E: Premium User Experience
- **E1. Picture-in-Picture (PiP) Player:** Native Android PiP mode for downloaded video playback.
- **E2. Background Processing Controls:** Notification controls allowing users to pause, resume, or throttle video processing jobs.
- **E3. Queue Management & Reordering:** Drag-and-drop queue reordering, priority promotion.
- **E4. Battery & Network Controls:** Global toggles for Wi-Fi-only downloads, charging-only encoding.
- **E5. Custom Storage Directory:** User-selected download destination via Storage Access Framework (SAF) tree URI.
- **E6. Accessibility & Localization:** 100% TalkBack content descriptions, dynamic type scale support, high-contrast dark theme mode.

---

## 6. PHASED IMPLEMENTATION ROADMAP & PHASING STRATEGY

The implementation is structured into **10 granular, independently testable phases**. Each phase is an atomic unit requiring explicit user approval before execution.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       RECOMMENDED DEVELOPMENT SEQUENCE                      │
├─────────────────────────────────────────────────────────────────────────────┤
│ Phase 1: Stage 2.3.1.1 HDR10 Metadata Correction & Bitstream Verification   │
│ Phase 2: Built-in Media Inspector & Deep Analysis Dialog (Phase B6)         │
│ Phase 3: Built-in Video Trimmer & Fast Stream-Copy Engine (Phase B1)        │
│ Phase 4: Audio Extractor & Media Format Converter (Phases B3 & B5)          │
│ Phase 5: Advanced Video Compressor with CRF & Target Size Controls (B2)    │
│ Phase 6: Advanced Downloader: Batch, Playlists & Format Picker (A1, A2, A5) │
│ Phase 7: Downloader Reliability: Scheduling, Error Classifier, Subs (A3,A4,A6)│
│ Phase 8: Smart Library: Categories, Filtering, Storage Dashboard (C1-C6)    │
│ Phase 9: Video Enhancement: Cinematic Color Grading & 3D LUT (D1, D2, D3)   │
│ Phase 10: Video Enhancement: Film Grain, Watermarks & Fused Pipeline (D4,D5)│
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### PHASE 1: STAGE 2.3.1.1 HDR10 METADATA CORRECTION & BITSTREAM VERIFICATION
- **Scope:**
  - Audit and fix CTA-861.3 static metadata byte layout, units, and endianness.
  - Implement dynamic/calibrated Content Light Level computation (MaxCLL / MaxFALL) in `VideoProcessingEngine`.
  - Validate MP4 container atoms (`colr`, `mdcv`, `clli`) and HEVC NAL units (`PREFIX_SEI_NUT` payload 137 and 144).
  - Verify EGL 10-bit surface transfer fidelity on Adreno drivers.
  - Formalize physical verification requirements for OnePlus Snapdragon 8 Gen 3 hardware.
- **Out of Scope:**
  - 3D LUTs, color grading UI, film grain, frame rate conversion.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/engine/hdr/HdrStaticMetadata.kt`
  - `app/src/main/java/com/unidownco/engine/processor/VideoProcessingEngine.kt`
  - `app/src/main/java/com/unidownco/engine/verifier/ProcessingOutputValidator.kt`
  - `app/src/test/java/com/unidownco/HdrPipelineValidationTest.kt`
- **Database Impact:** None.
- **Exit Criteria:**
  - Unit tests verify dynamic MaxCLL/MaxFALL calculation without hardcoded values.
  - Bitstream validator confirms `mdcv` and `clli` atoms conform to CTA-861.3 and SMPTE ST 2084.
  - Physical Xiaomi SM6225 tests continue passing 18/18 with honest 8-bit gating.

---

### PHASE 2: BUILT-IN MEDIA INSPECTOR & TECHNICAL ANALYSIS (PHASE B6)
- **Scope:**
  - Create a dedicated technical inspector component inside `feature/details`.
  - Extract and display deep container and stream metadata: video codec profile/level, exact resolution, aspect ratio, frame rate, VFR status, bit depth, color primaries, transfer characteristics, matrix coefficients, audio sample rate, channels, and bitrate.
  - Format all technical specs using JetBrains Mono typography in compliance with `DESIGN.md`.
- **Out of Scope:**
  - Editing, trimming, or transcode operations.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/feature/details/VideoDetailsScreen.kt`
  - `app/src/main/java/com/unidownco/engine/analyzer/VideoAnalyzerImpl.kt`
  - `app/src/main/java/com/unidownco/core/database/VideoMetadataEntity.kt`
- **Database Impact:** Schema version remains v3.
- **Exit Criteria:**
  - Verified on physical device with standard MP4, HEVC, and HDR test files.
  - Accurate display of technical metadata without misleading approximations.

---

### PHASE 3: BUILT-IN VIDEO TRIMMER & FAST STREAM-COPY ENGINE (PHASE B1)
- **Scope:**
  - Build interactive video trimming UI with start/end markers and preview playback.
  - Implement fast-path lossless stream-copy trimmer using `MediaExtractor` and `MediaMuxer` (cutting on keyframes/IDR frames without re-encoding).
  - Implement fallback re-encoding trimmer using `VideoProcessingEngine` for frame-accurate sub-GOP trimming.
  - Safe output generation in temporary directory with atomic promotion to `Movies/downloads/`.
- **Out of Scope:**
  - Color grading, audio extraction, batch trimming.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/feature/toolkit/TrimmerScreen.kt` (New)
  - `app/src/main/java/com/unidownco/engine/toolkit/VideoTrimmerEngine.kt` (New)
  - `app/src/main/java/com/unidownco/core/design/components/FilmstripScrubber.kt` (New)
- **Database Impact:** Add `isTrimmed` or link new entry to `downloads` table.
- **Exit Criteria:**
  - Lossless trim completes in $< 2$ seconds for a 5-minute video.
  - Frame-accurate trim maintains audio/video sync within 50ms.
  - Original source file SHA-256 remains untouched.

---

### PHASE 4: AUDIO EXTRACTOR & MEDIA FORMAT CONVERTER (PHASES B3 & B5)
- **Scope:**
  - Dedicated Audio Extraction engine: demuxes existing AAC/Opus tracks without loss into `.m4a` / `.opus`, or transcodes to MP3 using in-app FFmpeg runtime.
  - Container Format Converter: remuxes MKV/WebM to MP4 where streams are compatible; falls back to transcode when incompatible.
  - Clean UI entry points in `LibraryScreen` and `VideoDetailsScreen`.
- **Out of Scope:**
  - Audio multi-track mixing or equalization.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/engine/toolkit/AudioExtractorEngine.kt` (New)
  - `app/src/main/java/com/unidownco/engine/toolkit/FormatConverterEngine.kt` (New)
  - `app/src/main/java/com/unidownco/feature/toolkit/AudioExtractorDialog.kt` (New)
- **Database Impact:** Room database migration from v3 to v4 (adding `mediaType: String` to distinguish Video vs Audio).
- **Exit Criteria:**
  - Extracted audio plays back cleanly with verified sample rate and channel layout.
  - Remuxed MP4 files open in standard Android Media Player without container errors.

---

### PHASE 5: ADVANCED VIDEO COMPRESSOR (PHASE B2)
- **Scope:**
  - Implement compression studio UI with target file size slider and visual quality presets (Fast 720p, Balanced 1080p, High-Efficiency HEVC).
  - Bitrate calculator computing target video bitrate based on target size and audio stream requirements.
  - Progress reporting with real-time compression ratio display (e.g. `120 MB -> 45 MB (-62%)`).
- **Out of Scope:**
  - Multi-pass VBR (Android MediaCodec does not reliably support 2-pass hardware encoding on all chipsets).
- **Files Affected:**
  - `app/src/main/java/com/unidownco/feature/toolkit/CompressorScreen.kt` (New)
  - `app/src/main/java/com/unidownco/engine/toolkit/VideoCompressorEngine.kt` (New)
- **Database Impact:** Linked to `processing_jobs` table.
- **Exit Criteria:**
  - Output file size matches target within $\pm 10\%$.
  - Output quality verified without severe macroblocking or audio desync.

---

### PHASE 6: ADVANCED DOWNLOADER: BATCH, PLAYLISTS & FORMAT PICKER (PHASES A1, A2, A5)
- **Scope:**
  - Batch URL input: text area supporting multi-line paste, clipboard auto-detection, duplicate stripping.
  - Playlist parsing: YouTube playlist detection, individual entry selection modal, bulk enqueueing.
  - Pre-download format inspection bottom sheet: resolution, codec, audio bitrate, estimated file size.
- **Out of Scope:**
  - Scheduled downloads (Phase 7), credential vault (Phase 7).
- **Files Affected:**
  - `app/src/main/java/com/unidownco/feature/home/BatchDownloadDialog.kt` (New)
  - `app/src/main/java/com/unidownco/feature/home/PlaylistSelectionDialog.kt` (New)
  - `app/src/main/java/com/unidownco/data/extractor/PlaylistExtractor.kt` (New)
  - `app/src/main/java/com/unidownco/data/repository/DownloadRepository.kt`
- **Database Impact:** Add `batchId: String?` and `playlistId: String?` to `DownloadEntity`.
- **Exit Criteria:**
  - 10-item batch queue enqueues and downloads reliably without memory exhaustion.
  - 50-item YouTube playlist loads, allows selective checkmarks, and enqueues selected items.

---

### PHASE 7: DOWNLOADER RELIABILITY: SCHEDULING, ERROR CLASSIFIER & SUBTITLES (PHASES A3, A4, A6, A7)
- **Scope:**
  - `WorkManager` download scheduling: time of day, Wi-Fi-only, Charging-only constraints.
  - Intelligent Error Classifier: distinct handling of HTTP 429 (rate limit backoff), HTTP 403 (cookie expired), network drops.
  - Subtitle discovery, download, and optional MP4 soft-muxing.
  - Android Keystore session vault for authenticated downloads.
- **Out of Scope:**
  - CAPTCHA bypass or rate-limit evasion (strictly disallowed).
- **Files Affected:**
  - `app/src/main/java/com/unidownco/core/network/DownloadSchedulerWorker.kt` (New)
  - `app/src/main/java/com/unidownco/core/common/ErrorClassifier.kt` (New)
  - `app/src/main/java/com/unidownco/engine/subtitles/SubtitleManager.kt` (New)
  - `app/src/main/java/com/unidownco/core/security/SessionVault.kt` (New)
- **Database Impact:** Add scheduling fields to `DownloadEntity`.
- **Exit Criteria:**
  - Scheduled download fires accurately when Wi-Fi and charging conditions are met.
  - Rate-limited downloads back off without crashing or entering infinite loops.
  - Subtitle files download with valid UTF-8 timestamps.

---

### PHASE 8: SMART LIBRARY: CATEGORIES, FILTERING & STORAGE DASHBOARD (PHASES C1 - C6)
- **Scope:**
  - Library categorization tabs (All, Videos, Audio, Subtitles, Processed).
  - Search and multi-filter drawer (Date, Platform, Resolution, Codec, HDR).
  - Storage analytics dashboard: disk usage visualizer, orphaned file cleanup.
  - History backup and restore (JSON export/import).
  - Custom Collections and tagging.
- **Out of Scope:**
  - Cloud backup or remote synchronization.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/feature/library/LibraryScreen.kt`
  - `app/src/main/java/com/unidownco/feature/library/LibraryViewModel.kt`
  - `app/src/main/java/com/unidownco/feature/library/StorageDashboardScreen.kt` (New)
  - `app/src/main/java/com/unidownco/data/repository/BackupRepository.kt` (New)
- **Database Impact:** Room database migration adding `collections` table and `collection_items` cross-reference table.
- **Exit Criteria:**
  - Filtering 500+ library items operates smoothly at 60 FPS.
  - Storage cleanup safely deletes orphaned `.part` files without touching active downloads.
  - Backup JSON export and restore produces an exact database replica.

---

### PHASE 9: VIDEO ENHANCEMENT: CINEMATIC COLOR GRADING & 3D LUT (PHASES D1, D2, D3)
- **Scope:**
  - Frame rate cadence conversion (25, 30, 50, 60 FPS, Original).
  - Cinematic Color Grading shader: Exposure, Contrast, Saturation, Temperature, Tint, Highlights/Shadows.
  - 3D LUT parser for standard `.cube` files (17x17x17 and 33x33x33).
  - Upload LUT as `GL_TEXTURE_3D` and apply via trilinear sampling in fragment shader.
  - Real-time preview composable before transcode commitment.
- **Out of Scope:**
  - Optical-flow motion interpolation (Phase 10 / Step 2.4).
- **Files Affected:**
  - `app/src/main/java/com/unidownco/engine/hdr/gpu/CinematicColorShader.kt` (New)
  - `app/src/main/java/com/unidownco/engine/lut/CubeLutParser.kt` (New)
  - `app/src/main/java/com/unidownco/feature/enhancement/ColorGradingScreen.kt` (New)
  - `app/src/main/java/com/unidownco/engine/hdr/gpu/HdrRenderCore.kt`
- **Database Impact:** Add color grading and LUT parameters to `ProcessingJobEntity`.
- **Exit Criteria:**
  - `.cube` LUT renders accurately without color banding or distortion.
  - Color grading shader runs in $< 5\text{ms}$ per 1080p frame on Adreno 610.
  - Output HEVC bitstream validates cleanly.

---

### PHASE 10: VIDEO ENHANCEMENT: FILM GRAIN, WATERMARKS & FUSED PIPELINE (PHASES D4, D5, D9)
- **Scope:**
  - Parametric film grain simulation in GLSL with dynamic temporal noise seed and luminance mask.
  - Text and logo watermark compositor with alpha blending, positioning, and timing.
  - Fused multi-stage GPU pipeline minimizing texture read/write passes.
  - Comprehensive stress testing, thermal validation, and memory leak audit.
- **Out of Scope:**
  - Dolby Vision authoring (formally excluded).
- **Files Affected:**
  - `app/src/main/java/com/unidownco/engine/hdr/gpu/FilmGrainShader.kt` (New)
  - `app/src/main/java/com/unidownco/engine/overlay/WatermarkCompositor.kt` (New)
  - `app/src/main/java/com/unidownco/engine/hdr/gpu/FusedEnhancementPipeline.kt` (New)
- **Database Impact:** Add watermark and grain metadata to `ProcessingJobEntity`.
- **Exit Criteria:**
  - Complete fused pipeline executes on 1080p 30 FPS video without dropping frames or triggering thermal shutdown.
  - Clean video output with organic film grain and sharp, unclipped watermarks.

---

## 7. DEPENDENCY GRAPH & CRITICAL PATH

```
[Phase 1: Stage 2.3.1.1 HDR10 Verification] ──► MUST BE COMPLETED FIRST
                     │
                     ▼
[Phase 2: Media Inspector & Analysis] ────────► Required for inspecting outputs
       │                         │
       ▼                         ▼
[Phase 3: Video Trimmer]   [Phase 4: Audio Extractor & Converter]
       │                         │
       ▼                         ▼
[Phase 5: Advanced Video Compressor]
       │
       ▼
[Phase 6: Downloader Batch & Playlists] ──► Downloader Enhancement Branch
       │
       ▼
[Phase 7: Downloader Reliability & Subs]
       │
       ▼
[Phase 8: Smart Library & Storage Dashboard] ──► Library & Data Hub
       │
       ▼
[Phase 9: Enhancement: Color Grading & 3D LUT] ──► Enhancement Branch
       │
       ▼
[Phase 10: Enhancement: Grain, Watermarks & Fused Graph]
```

---

## 8. DATABASE ARCHITECTURE & MIGRATION STRATEGY

### Migration from v3 to v4 (Media Toolkit & Library Categorization)
- **Changes:**
  - `downloads`: Add `mediaType` (`VIDEO`, `AUDIO`, `SUBTITLE`), `batchId` (TEXT), `playlistId` (TEXT).
  - `downloads`: Add `isFavorite` (INTEGER DEFAULT 0), `isArchived` (INTEGER DEFAULT 0).
  - Create table `collections` (`id`, `name`, `createdAt`).
  - Create table `collection_items` (`collectionId`, `downloadId`, PRIMARY KEY(`collectionId`, `downloadId`)).
- **Safety Policy:**
  - Explicit `Migration(3, 4)` implemented with automated schema migration tests.
  - `fallbackToDestructiveMigration()` is **strictly prohibited**.

### Migration from v4 to v5 (Enhancement Studio Presets & Jobs)
- **Changes:**
  - `processing_jobs`: Add `lutPath` (TEXT), `exposure` (REAL), `contrast` (REAL), `saturation` (REAL), `grainIntensity` (REAL), `watermarkJson` (TEXT).
- **Safety Policy:**
  - Add migrations with foreign key constraints intact.

---

## 9. PERFORMANCE, MEMORY & THERMAL BUDGET

| Metric / Resource | Xiaomi SM6225 (Baseline) | OnePlus Snapdragon 8 Gen 3 (Target) | Failure Threshold / Safety Policy |
|---|---|---|---|
| **Max Frame Queue** | $\le 4$ frames in memory | $\le 8$ frames in memory | $> 12$ frames triggers decoder backpressure |
| **Max Native Heap** | $< 120\text{ MB}$ | $< 250\text{ MB}$ | $> 350\text{ MB}$ triggers GC / buffer flush |
| **GPU Texture Budget** | 1 intermediate FBO ($1080\text{p}$) | 2 intermediate FBOs ($4\text{K}$) | FBO reuse mandatory; no allocations per frame |
| **Peak Transcode FPS** | $45 - 60\text{ FPS}$ ($1080\text{p}$) | $180 - 240\text{ FPS}$ ($1080\text{p}$) | $< 15\text{ FPS}$ triggers complexity warning |
| **Thermal Mitigation** | Throttle at `THERMAL_STATUS_SEVERE` | Throttle at `THERMAL_STATUS_SEVERE` | Pause job at `CRITICAL`; resume on `MODERATE` |

---

## 10. HARDWARE COMPATIBILITY MATRIX

| Feature | Xiaomi Redmi Note (SM6225 / Adreno 610) | OnePlus Ace 5 / 13R (SM8650 / Adreno 750) | Software Fallback Strategy |
|---|---|---|---|
| **HEVC 8-Bit Hardware Encoding** | **VERIFIED ON DEVICE** (`c2.qti.hevc.encoder`) | **ARCHITECTURALLY READY** | Software C2 fallback if HW unavailable |
| **HEVC Main10 10-Bit HW Encoding** | **UNSUPPORTED BY SILICON** | **ARCHITECTURALLY READY (Pending Physical)** | Gated to 8-Bit HEVC SDR ("8-Bit HW Only") |
| **EGL RGBA_1010102 Surface** | **DRIVER EXPOSED / HW GATED** | **ARCHITECTURALLY READY (Pending Physical)** | Fallback to EGL RGBA_8888 |
| **Fused GLSL ES 3.0 HDR Shader** | **VERIFIED ON DEVICE** | **ARCHITECTURALLY READY** | Bypass shader when SDR passthrough |
| **3D LUT (`.cube`) GL_TEXTURE_3D** | Supported (ES 3.0) | Supported (ES 3.2) | 2D sliced texture fallback for ES 2.0 |
| **Lossless Video Trimming** | **SUPPORTED** (Extractor/Muxer) | **SUPPORTED** | Transcode fallback for sub-GOP accuracy |
| **Audio Extraction (AAC/Opus)** | **SUPPORTED** (Demuxer) | **SUPPORTED** | Transcode to MP3 via in-app FFmpeg |
| **yt-dlp Companion Runtime** | **VERIFIED ON DEVICE** (v2026.10.26) | **ARCHITECTURALLY READY** | Native Kotlin extractor fallback |

---

## 11. SECURITY, PRIVACY & COMPLIANCE

1. **Android Keystore Integration:**
   - All session tokens, cookies, or user credentials must be encrypted using `AndroidKeyStore` with `KeyGenParameterSpec` (`AES_256_GCM`).
   - Plaintext credentials must never be written to Room database, Shared Preferences, or log outputs.
2. **Zero Log Policy:**
   - URLs containing sensitive session parameters (`access_token`, `auth`, `signature`) must be sanitized by `UniLog` prior to console output.
3. **No DRM or Access Control Bypasses:**
   - UniDownco strictly processes unencrypted, publicly accessible media.
   - No attempts will ever be made to bypass Widevine DRM, defeat Cloudflare Turnstile, solve CAPTCHAs, or rotate proxies.
4. **Scoped Storage Compliance:**
   - Media output is isolated to standard `Movies/` and `Music/` MediaStore collections.
   - No broad external storage permissions (`MANAGE_EXTERNAL_STORAGE`) will be requested.

---

## 12. DEPENDENCY & LICENSING POLICY

| Component / Library | License | Usage Boundary & Policy |
|---|---|---|
| **AndroidX & Jetpack Compose** | Apache 2.0 | Core application UI, lifecycle, Room persistence. Compliant. |
| **OkHttp & Kotlin Coroutines** | Apache 2.0 | Networking, HTTP chunking, async tasks. Compliant. |
| **youtubedl-android** | Apache 2.0 | Wrapper library for companion binary execution. Compliant. |
| **yt-dlp Companion Binary** | Unlicense | Executed as standalone companion process. No license contamination. |
| **FFmpeg Binary (bundled)** | LGPL v2.1+ | Dynamically executed via companion wrapper. No proprietary linking. |
| **Google Truth / JUnit** | Apache 2.0 | Test assertions and execution only. Excluded from production APK. |
| **Dolby Vision SDK** | Proprietary | **STRICTLY EXCLUDED.** No unlicensed reverse-engineered authoring. |

---

## 13. RISK REGISTER & MITIGATION STRATEGY

| Risk Description | Severity | Probability | Mitigation Strategy |
|---|---|---|---|
| **1. Upstream Social Platform API Breaking Changes** | HIGH | HIGH | In-app dynamic yt-dlp updating via `YtDlpEngineManager`, multi-provider fallback registry. |
| **2. Lack of Physical SM8650 Device for Main10 Verification** | HIGH | MEDIUM | Formal gating: Mark Main10 as "Architecturally Ready (Physical Test Pending)"; test on connected hardware when available. |
| **3. Thermal Throttling during Long Transcode Jobs** | MEDIUM | HIGH | Enforce backpressure using `PowerManager.currentThermalStatus`; sleep 10ms on SEVERE; pause on CRITICAL. |
| **4. Storage Exhaustion during High-Bitrate Transcodes** | HIGH | LOW | Pre-flight disk space check requiring $\ge 2.5\times$ estimated output size before transcode begins. |
| **5. Large 3D LUT Memory Overhead** | MEDIUM | LOW | Restrict imported `.cube` LUT size to maximum $33\times 33\times 33$ ($143\text{ KB}$ raw texture memory). |

---

## 14. EXCLUDED & POSTPONED CAPABILITIES

The following features are **explicitly excluded** from the active roadmap:
1. **Dolby Vision Authoring (Profile 5 / 8.4):** Requires proprietary Dolby licensing and encoder patents. Only passthrough of existing streams will ever be evaluated.
2. **Fake HDR Tagging:** Injecting HDR static metadata into 8-bit SDR files without genuine mathematical color expansion and Main10 encoding is strictly prohibited.
3. **Real-Time 4K 60 FPS Optical Flow Motion Interpolation:** Exceeds mobile thermal and GPU shader capabilities. Postponed to future research with dedicated NPU backends.
4. **Cloud Media Storage / Remote Syncing:** UniDownco is strictly an offline-first, on-device media studio.
5. **DRM / CAPTCHA Bypass Mechanisms:** Strictly prohibited by project safety guidelines.

---

## 15. EXPLICIT APPROVAL GATES & RECOMMENDED FIRST STAGE

### Approval Gates Policy
Every implementation phase in Section 6 is an **independent milestone**. Completing one phase **does NOT authorize beginning the next phase**. The agent must stop, report verified test results, and wait for explicit user approval before proceeding.

### Recommended First Implementation Stage
**Recommendation:** **PHASE 1 (STAGE 2.3.1.1: HDR10 METADATA CORRECTION & END-TO-END BITSTREAM VERIFICATION)**

#### Justification:
1. **Closes the Open Architectural Foundation:** Stage 2.3.1 established the mathematical and shader foundation, but the outstanding concerns (hardcoded 1000/400 nits, binary layout verification, and bitstream NAL unit inspection) must be closed to ensure a mathematically unshakeable video enhancement engine.
2. **Zero Regression Risk:** Phase 1 focuses on verification and mathematical refinement without introducing risky UI alterations or database migrations.
3. **Essential Prerequisite for Color Studio:** Developing 3D LUTs (Phase 9) or Film Grain (Phase 10) on top of an unverified color pipeline creates technical debt. Verifying the bitstream first ensures every subsequent video enhancement feature is built upon verified silicon reality.
