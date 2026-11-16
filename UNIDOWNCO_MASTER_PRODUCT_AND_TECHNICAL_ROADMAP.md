# UNIDOWNCO MASTER PRODUCT & TECHNICAL ROADMAP
## Revision 1.1: Architecture, Hardware Strategy, 12-Phase Implementation & Verification Governance

**Project:** UniDownco  
**Application ID (Release):** `com.unidownco`  
**Application ID (Debug):** `com.unidownco.debug`  
**Application ID (Test):** `com.unidownco.debug.test`  
**Workspace:** `D:\Webapp\UniDownco`  
**Repository:** `https://github.com/arybishal/UniDownco`  
**Base Branch:** `master`  
**Document Status:** MASTER ROADMAP REVISION 1.1 (PLANNING ONLY — STRICTLY ZERO CODE MODIFICATIONS)  
**Target Environments:**
- **Baseline Connected Physical Device:** Xiaomi Redmi Note 25062RN2DA (Qualcomm SM6225 / bengal / Adreno 610, Android 16 / API 36)
- **Flagship Target Platform:** OnePlus Ace 5 / OnePlus 13R class (Qualcomm Snapdragon 8 Gen 3 SM8650-AB / Adreno 750, Android 15 / API 35+)

---

## REVISION HISTORY

| Version | Date | Author / Context | Key Changes & Additions |
|---|---|---|---|
| **v1.0** | Nov 16, 2026 | Initial Master Roadmap | Initial architectural baseline, 10-phase sequence, gap audit, performance budget, licensing policy, and initial hardware compatibility matrix. |
| **v1.1** | Nov 16, 2026 | Master Roadmap Revision 1.1 | **Four Mandatory Corrections:**<br>1. **Correction A:** Added **Phase 11 (Premium UX & Background Experience)** covering background service compliance, queue priority/reordering, PiP lifecycle, accessibility, and responsive layouts.<br>2. **Correction B:** Added **Phase 12 (Final Quality, Security, Compatibility & Release Readiness)** establishing comprehensive audit areas, immutable GitHub release policies, and release readiness checklists.<br>3. **Correction C:** Substantially strengthened **HDR10 metadata and bitstream governance**, eliminating fabricated default metadata, detailing independent serialization specifications (Android `KEY_HDR_STATIC_INFO`, HEVC SEI payloads 137/144, MP4 `mdcv`/`clli` atoms), mandating independent bitstream verification, color science tests, and explicit physical device status (`MAIN10 PHYSICAL DEVICE VERIFICATION: NOT EXECUTED`).<br>4. **Correction D:** Expanded hardware capability classification into a **20-dimension capability matrix** with 10 explicit status classifications and granular device evaluations for Xiaomi SM6225 and OnePlus Snapdragon 8 Gen 3.<br>5. **Mandatory Regression Baseline:** Added comprehensive regression protection covering downloader, media processing, database, and UI suites across all phases.<br>6. **Updated Phasing:** Expanded roadmap from 10 to 12 fully articulated, approval-gated phases. |

---

## 1. EXECUTIVE SUMMARY & MISSION

UniDownco is evolving from a single-stream Android media downloader and HEVC encoder into a comprehensive, high-reliability **Media Downloading, Management, and Processing Studio**.

The studio unites three core pillars:
1. **Universal Resilient Media Downloader:** Multi-platform extraction (YouTube, Instagram, Facebook, TikTok, Reddit, X, Direct HTTP) powered by an in-app `yt-dlp` companion runtime, native progressive chunking, adaptive DASH stream remuxing, batch processing, playlist extraction, and background execution.
2. **Built-in Media Toolkit & Smart Library:** Frame-accurate video trimming, CRF/bitrate compression, lossless audio extraction, high-resolution thumbnail generation, container conversion, deep metadata inspection, categorized asset management, duplicate detection, and storage analytics.
3. **Hardware-Accelerated Video Enhancement & Color Studio:** Android `MediaCodec` hardware encoding, GPU-accelerated fused shader pipelines (linearization, 3x3 gamut transforms, inverse tone mapping, ST 2084 PQ curve), 3D LUT grading (`.cube`), parametric film grain, watermark/logo composition, and honest hardware gating across heterogeneous SoC tiers.

### Fundamental Architectural Constraints
- **Zero Hallucinated Hardware Capabilities:** No device without verified silicon capabilities will advertise or attempt genuine 10-bit HEVC Main10 encoding. Devices lacking 10-bit hardware encoders (such as the Qualcomm SM6225) are strictly gated to 8-bit Main SDR with explicit user-facing diagnostic badges.
- **Zero Fabricated Metadata:** No arbitrary or fabricated static metadata (e.g. universal hardcoded 1000/400 nits) will ever be assigned to videos without documented source derivation or pixel measurement. No SDR file will ever be tagged with fake HDR static metadata or labeled as HDR10.
- **Mandatory Stage 2.3.1.1 Gate:** The existing Stage 2.3.1 implementation of the 10-bit HDR pipeline is recognized as code-complete but **unverified on physical Main10 silicon**. Prior to building dependent enhancement features (such as 3D LUTs and grading presets), `STAGE 2.3.1.1` must execute physical verification, metadata byte-layout audits, and bitstream NAL unit inspection.
- **Strict Single Source of Truth:** All user interfaces strictly adhere to [DESIGN.md](file:///D:/Webapp/UniDownco/DESIGN.md) (White Canvas, Black Structure, Yellow `#FFD429` Accent, Inter & JetBrains Mono typography).

---

## 2. CURRENT VERIFIED ARCHITECTURE & TECHNICAL BASELINE

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
- **Android Gradle Plugin:** 8.7.2 | **Gradle Wrapper:** 8.10.2
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
| **Premium UX & Background Exp (Phase 11)**| **Partially Implemented** | Service exists; lacks queue reordering, PiP, and advanced controls. |
| **Release Readiness Governance (Phase 12)**| **Not Implemented** | Requires formal release audit, immutable tag policy, and signoff. |

---

## 4. HDR10 METADATA AND BITSTREAM GOVERNANCE (CORRECTION C)

To eliminate any ambiguity between metadata domains, the roadmap strictly differentiates five independent representations:
1. **Mastering Display Colour Volume (MDCV):** The physical optical properties (chromaticity primaries, white point, minimum and maximum luminance) of the reference monitor on which the video was mastered.
2. **Content Light Level (CLL):** The statistical light levels of the actual video essence, specifically Maximum Content Light Level ($\text{MaxCLL}$) and Maximum Frame-Average Light Level ($\text{MaxFALL}$).
3. **HEVC Bitstream SEI Representation:** In-band Supplemental Enhancement Information (SEI) NAL units multiplexed directly into the elementary video stream (`nal_unit_type = 39`, `PREFIX_SEI_NUT`).
4. **MP4 Container Metadata Atoms:** ISO Base Media File Format (ISOBMFF) box structures placed inside the video sample description box (`stsd` / `hev1` / `hvc1`).
5. **Android MediaCodec Metadata Buffer:** The in-memory 25-byte Little-Endian binary byte array delivered to Android's `MediaFormat.KEY_HDR_STATIC_INFO`.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       METADATA REPRESENTATION DOMAINS                       │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. Android MediaCodec:   KEY_HDR_STATIC_INFO (25-byte Little-Endian CTA-861)│
│ 2. HEVC Elementary SEI:  Payload 137 (MDCV) & Payload 144 (CLLI)            │
│ 3. MP4 Container Atoms:  'mdcv' Box (24 bytes) & 'clli' Box (4 bytes)       │
│ 4. MP4 Colour Info:      'colr' Box ('nclx' tuple: primaries, transfer, mtx)│
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.1 Strict Policy: Elimination of Fabricated Metadata
- **Forbidden Practice:** No implementation will universally hardcode static $\text{MaxCLL} = 1000\text{ nits}$ or $\text{MaxFALL} = 400\text{ nits}$ across arbitrary source media.
- **Four Metadata Classifications:**
  1. **Measured Metadata:** Explicitly calculated across all decoded frames by evaluating pixel luminances in the linear domain:
     $$\text{MaxCLL} = \max_{x,y,t} \left(Y_{\text{nits}}(x,y,t)\right)$$
     $$\text{MaxFALL} = \frac{1}{T}\sum_{t=1}^T \left(\frac{1}{W \cdot H}\sum_{x,y} Y_{\text{nits}}(x,y,t)\right)$$
  2. **Source-Declared Metadata:** Preserved directly from parsed source stream SEI NAL units or source container atoms without modification (`HDR10_PRESERVE` mode).
  3. **Mathematically Estimated Metadata:** For real-time SDR-to-HDR10 expansion where full two-pass pre-measurement is computationally prohibitive, metadata must be bounded strictly by the analytical mathematical limits of the applied inverse tone mapping curve (e.g. BT.2408 expansion capped at $1,000\text{ nits}$, diffuse white at $203\text{ nits}$, and estimated scene average).
  4. **Unavailable Metadata:** When content light levels cannot be measured, declared, or mathematically guaranteed.
- **Standards-Compliant Fallback Policy:** When $\text{MaxCLL}$ and $\text{MaxFALL}$ are unavailable, the application must omit the Content Light Level SEI (payload 144) and the MP4 `clli` atom entirely, or signal them as $0$ (indicating unknown content light levels per CTA-861.3 and ITU-T H.265 Annex D).

### 4.2 Independent Serialization & Representation Specifications

Every representation emitted by UniDownco has distinct field ordering, units, and endianness:

| Domain | Specification | Layout & Byte Order | Units / Scaling |
|---|---|---|---|
| **Android `KEY_HDR_STATIC_INFO`** | CTA-861.3 / CTA-861-G Type 1 | 25 Bytes, **Little-Endian**<br>Byte 0: ID (0x00)<br>Bytes 1-12: Primaries (G, B, R)<br>Bytes 13-16: White Point D65<br>Bytes 17-18: Max Mastering Lum<br>Bytes 19-20: Min Mastering Lum<br>Bytes 21-22: MaxCLL<br>Bytes 23-24: MaxFALL | Primaries/White: $0.00002$ ($50,000$)<br>Max Lum: $1\text{ cd/m}^2$<br>Min Lum: $0.0001\text{ cd/m}^2$<br>MaxCLL: $1\text{ cd/m}^2$<br>MaxFALL: $1\text{ cd/m}^2$ |
| **HEVC MDCV SEI (Payload 137)** | ITU-T H.265 D.2.27 | Elementary Stream NAL, **Big-Endian**<br>Order: $c=0$ (Green), $c=1$ (Blue), $c=2$ (Red) | Primaries/White: $0.00002$ ($50,000$)<br>**Max Lum: $0.0001\text{ cd/m}^2$ (32-bit uint)**<br>Min Lum: $0.0001\text{ cd/m}^2$ (32-bit uint) |
| **HEVC CLLI SEI (Payload 144)** | ITU-T H.265 D.2.35 | Elementary Stream NAL, **Big-Endian**<br>16-bit uint `max_content_light_level`<br>16-bit uint `max_pic_average_light_level` | MaxCLL: $1\text{ cd/m}^2$<br>MaxFALL: $1\text{ cd/m}^2$ |
| **MP4 Container `mdcv` Atom** | ISO/IEC 14496-12 / 23008-12 | ISOBMFF Box, **Big-Endian** (24 data bytes)<br>Order: Green(x,y), Blue(x,y), Red(x,y), White(x,y), MaxLum (32-bit), MinLum (32-bit) | Primaries/White: $0.00002$ ($50,000$)<br>**Max Lum: $0.0001\text{ cd/m}^2$ (32-bit)**<br>Min Lum: $0.0001\text{ cd/m}^2$ (32-bit) |
| **MP4 Container `clli` Atom** | ISO/IEC 14496-12 / 23008-12 | ISOBMFF Box, **Big-Endian** (4 data bytes)<br>16-bit uint `max_content_light_level`<br>16-bit uint `max_pic_average_light_level` | MaxCLL: $1\text{ cd/m}^2$<br>MaxFALL: $1\text{ cd/m}^2$ |

*Critical Difference Highlight:* In CTA-861.3 (`KEY_HDR_STATIC_INFO`), Max Mastering Luminance is a 16-bit integer in units of $1\text{ cd/m}^2$. In HEVC SEI payload 137 and the MP4 `mdcv` atom, Max Mastering Luminance is a 32-bit integer in units of $0.0001\text{ cd/m}^2$ ($10,000\times$ difference). These representations are not interchangeable.

### 4.3 HEVC Bitstream Verification Requirements
Phase 1 must require automated and independent verification of the final output file:
- **HEVC Sequence Parameter Set (SPS):**
  - `profile_tier_level`: `general_profile_idc == 2` (HEVC Profile Main10).
  - `bit_depth_luma_minus8 == 2` (10-bit luma depth).
  - `bit_depth_chroma_minus8 == 2` (10-bit chroma depth).
- **VUI Parameters inside SPS:**
  - `colour_description_present_flag == 1`.
  - `colour_primaries == 9` (ITU-R BT.2020).
  - `transfer_characteristics == 16` (SMPTE ST 2084 / PQ).
  - `matrix_coeffs == 9` (ITU-R BT.2020 non-constant luminance).
  - `video_full_range_flag == 0` (Studio/Limited video range $[64, 940]$ for 10-bit).
- **Supplemental Enhancement Information (SEI):**
  - Presence of `PREFIX_SEI_NUT` (`nal_unit_type = 39`).
  - Validation of payloadType 137 (Mastering Display Colour Volume).
  - Validation of payloadType 144 (Content Light Level Info) when applicable.
- **Independent Tool Requirement:** Verification must use an independent tool (such as FFprobe 4.4+ or a custom bitstream parser written in Kotlin) that inspects raw NAL bytes rather than trusting MediaCodec configuration return codes.

### 4.4 Color-Science & Shader Validation
The mathematical pipeline must undergo independent unit and shader validation against analytical test vectors:
- **BT.709 Linearization:** Tolerance $\epsilon < 10^{-6}$ across normalized domain $[0.0, 1.0]$.
- **Rec.709 to Rec.2020 Matrix Transform:** Verification that D65 white $(1, 1, 1)$ maps exactly to $(1, 1, 1)$ without chromaticity drift.
- **BT.2408 Inverse Tone Mapping:**
  - Anchor point verification: SDR reference white ($100\text{ nits}$) expands to HDR diffuse white ($203\text{ nits}$).
  - Specular highlight verification: Peak code value expands smoothly to $1,000\text{ nits}$ without slope discontinuity or highlight inversion.
- **SMPTE ST 2084 PQ Encoding:**
  - Analytical round-trip test: $\text{DecodePQ}(\text{EncodePQ}(L)) \equiv L$ with relative error $< 0.01\%$.
  - Anchor values: $0\text{ nits} \rightarrow 0.0$; $203\text{ nits} \rightarrow \approx 0.5807$; $1,000\text{ nits} \rightarrow \approx 0.7518$; $10,000\text{ nits} \rightarrow 1.0$.
- **Quantization & Dithering:** Verification that 10-bit quantization $[0, 1023]$ does not introduce false contouring or banding; evaluation of blue-noise or spatial triangular dithering where necessary.
- **GLSL Precision:** Shaders must explicitly declare `precision highp float;` and verify that the target GPU driver supports genuine IEEE 754 32-bit floating point.

### 4.5 Physical Device Status Statement
- **Xiaomi Redmi Note 25062RN2DA (SM6225):** Hardware encoder `c2.qti.hevc.encoder` strictly lacks `HEVCProfileMain10`. It is verified for 8-bit Main SDR only.
- **OnePlus Snapdragon 8 Gen 3 Target:** Architecturally ready but physically unconnected.
- **Formal Status Statement:**  
  `MAIN10 PHYSICAL DEVICE VERIFICATION: NOT EXECUTED (Compatible 10-bit hardware not physically connected).`

---

## 5. EXPANDED HARDWARE CAPABILITY CLASSIFICATION MATRIX (CORRECTION D)

The roadmap replaces all general compatibility labels with a **20-dimension capability matrix** evaluated across five discovery levels:
1. **Theoretical API / Silicon Capability:** Documented chipset specification or Android CDD requirement.
2. **Runtime-Discovered Capability:** Interrogated dynamically via `MediaCodecList`, `EGL14`, and `GLES30`.
3. **Implemented Capability:** Feature code written and integrated into the UniDownco codebase.
4. **Automated-Test Verified Capability:** Covered and passing in Robolectric host unit tests.
5. **Physically Verified Capability:** Executed and validated on physical Android hardware via ADB instrumentation.

### Standardized Status Classifications
- `PHYSICALLY VERIFIED`: Executed on physical device with validated bitstream and media output.
- `IMPLEMENTED AND TESTED`: Feature implemented in code and verified by host unit test suite.
- `ARCHITECTURALLY READY`: Designed and implemented to standard; awaiting physical test execution.
- `THEORETICALLY SUPPORTED`: Permitted by hardware specifications but not yet implemented or tested.
- `NOT PHYSICALLY VERIFIED`: Code exists but hardware execution has not occurred.
- `HARDWARE UNSUPPORTED`: Hardware silicon explicitly lacks the required encoder/decoder block.
- `SOFTWARE FALLBACK AVAILABLE`: CPU-based fallback pipeline exists when hardware block is absent.
- `PLANNED`: Slated for implementation in a future phase.
- `BLOCKED`: Prevented by licensing, hardware, or external platform constraints.
- `NOT EXECUTED`: Specific test scenario not performed due to environmental constraints.

### 20-Dimension Hardware Capability Matrix

| Dimension # | Hardware Dimension | Xiaomi Redmi Note (SM6225 / Adreno 610) | OnePlus Ace 5 / 13R (SM8650 / Adreno 750) | Verification & Governance Notes |
|---|---|---|---|---|
| **1** | Hardware Video Decoder Profiles | **PHYSICALLY VERIFIED** (AVC, HEVC Main, VP9) | **THEORETICALLY SUPPORTED** (AVC, HEVC Main10, VP9, AV1) | Probed via `MediaCodecList` |
| **2** | Hardware Video Encoder Profiles | **PHYSICALLY VERIFIED** (HEVC Main 8-bit Profile 1) | **THEORETICALLY SUPPORTED** (HEVC Main10, AVC High, AV1) | Probed via `MediaCodecList` |
| **3** | Software Video Decoder Profiles | **PHYSICALLY VERIFIED** (`c2.android.*` decoders) | **THEORETICALLY SUPPORTED** (`c2.android.*` decoders) | Android platform standard |
| **4** | Software Video Encoder Profiles | **PHYSICALLY VERIFIED** (`c2.android.hevc.encoder` 8-bit) | **THEORETICALLY SUPPORTED** (8-bit / 10-bit SW) | C2 software fallback |
| **5** | HEVC Main Profile (8-Bit) | **PHYSICALLY VERIFIED** (`c2.qti.hevc.encoder`) | **ARCHITECTURALLY READY** | Baseline for all transcode tasks |
| **6** | HEVC Main10 Profile (10-Bit) | **HARDWARE UNSUPPORTED** (Honest gating active) | **ARCHITECTURALLY READY** (`NOT EXECUTED`) | Physical test mandatory on SM8650 |
| **7** | Resolutions & Max Frame Rates | **PHYSICALLY VERIFIED** (1080p @ 30/60 FPS) | **THEORETICALLY SUPPORTED** (4K @ 120 FPS, 8K @ 30 FPS) | Verified in `test02_Transcode` |
| **8** | Encoder Surface-Input Support | **PHYSICALLY VERIFIED** (`createInputSurface()`) | **THEORETICALLY SUPPORTED** (`createInputSurface()`) | Zero-copy GPU-to-codec pipeline |
| **9** | EGL Configuration Availability | **PHYSICALLY VERIFIED** (EGL 1.4, RGBA_8888) | **THEORETICALLY SUPPORTED** (EGL 1.5, RGBA_1010102) | Driver configuration query |
| **10**| EGL 10-Bit Precision Delivery | **NOT PHYSICALLY VERIFIED** (EGL config present, codec 8b) | **ARCHITECTURALLY READY** (`NOT EXECUTED`) | Requires bitstream depth proof |
| **11**| OpenGL ES Version | **PHYSICALLY VERIFIED** (OpenGL ES 3.2) | **THEORETICALLY SUPPORTED** (OpenGL ES 3.2) | Verified via GL strings |
| **12**| Fragment Shader Precision | **PHYSICALLY VERIFIED** (IEEE 754 `highp` supported) | **THEORETICALLY SUPPORTED** (Full precision `highp`) | Checked via shader compile |
| **13**| 3D Texture Support (`GL_TEXTURE_3D`)| **PHYSICALLY VERIFIED** (Supported in GLES 3.0+) | **THEORETICALLY SUPPORTED** (Supported in GLES 3.2) | Required for `.cube` 3D LUTs |
| **14**| Max Texture Dimensions | **PHYSICALLY VERIFIED** (4096 x 4096 on Adreno 610) | **THEORETICALLY SUPPORTED** (16384 x 16384 on Adreno 750) | Probed via `GL_MAX_TEXTURE_SIZE` |
| **15**| MediaCodec HDR Metadata Transport | **NOT PHYSICALLY VERIFIED** (SM6225 rejects Main10) | **ARCHITECTURALLY READY** (`NOT EXECUTED`) | Passes `KEY_HDR_STATIC_INFO` |
| **16**| Bitstream Metadata Emission | **NOT PHYSICALLY VERIFIED** (Requires Main10 silicon) | **ARCHITECTURALLY READY** (`NOT EXECUTED`) | SEI payloads 137 and 144 |
| **17**| Audio Codec Compatibility | **PHYSICALLY VERIFIED** (AAC-LC, MP3, Opus demux) | **THEORETICALLY SUPPORTED** (AAC, Opus, FLAC, AC3) | Audio passthrough verified |
| **18**| Thermal Behavior & Throttling | **PHYSICALLY VERIFIED** (PowerManager thermal queries) | **THEORETICALLY SUPPORTED** (Advanced thermal HAL) | Monitored in transcode loop |
| **19**| Sustained Transcode Throughput | **PHYSICALLY VERIFIED** (45-60 FPS on 1080p HEVC) | **THEORETICALLY SUPPORTED** (180-240 FPS on 1080p HEVC) | Logged via `EncodingProgress` |
| **20**| Output Validation Compliance | **PHYSICALLY VERIFIED** (Track count, duration, sync) | **ARCHITECTURALLY READY** | Checked by `ProcessingOutputValidator`|

---

## 6. MANDATORY REGRESSION BASELINE

Before and after every implementation phase, the existing automated test suite must run and pass with **zero regressions**. Any regression immediately halts the phase.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          REGRESSION TEST BASELINE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. Host Unit Tests (82/82 Passing):                                         │
│    - ColorScienceTest (8 tests): Linearization, BT.2020 matrix, ITM, PQ     │
│    - HdrPipelineValidationTest (6 tests): CTA-861 buffer, validator checks  │
│    - Step2EncodingPipelineTest & Step2FoundationTest                        │
│    - InstagramEngineTest (Unit tests for canonical routing)                 │
│                                                                             │
│ 2. Physical Device Tests (18/18 Passing on Xiaomi SM6225):                  │
│    - InstagramDeviceTest (4 tests): Runtime init, canonical URL, extract   │
│    - MediaCodecEncodingDeviceTest (12 tests): 8-bit transcode, audio pass,  │
│      source SHA-256 immutability, rotation preservation, thermal query,     │
│      cancellation cleanup, startup reconciliation, silent video handling    │
│    - MediaStreamMuxerDeviceTest (2 tests): Remuxing & corrupted file failure│
└─────────────────────────────────────────────────────────────────────────────┘
```

### Specific Regression Domains
1. **Downloader Regression Suite:**
   - URL normalization and tracking parameter stripping (`InstagramUrlResolver`).
   - Bundled yt-dlp runtime execution and dynamic update handling (`YtDlpEngineManager`).
   - Adaptive DASH audio/video stream pairing without re-encoding (`MediaStreamMuxer`).
   - Resumable progressive HTTP download chunking with `.part` buffers (`DownloadEngine`).
   - Startup reconciliation of interrupted jobs in Room database.
2. **Media-Processing Regression Suite:**
   - Asynchronous MediaCodec decode-to-render-to-encode frame pump.
   - Bitstream rotation metadata preservation across 90°, 180°, and 270° orientations.
   - Monotonic presentation timestamp (PTS) delivery via `eglPresentationTimeANDROID`.
   - Audio elementary stream extraction and lossless muxing (`AudioTrackProcessor`).
   - Source file SHA-256 immutability (verifying source is never modified or overwritten).
   - Cancellation token cleanup (verifying all temporary files are purged on cancel).
   - Thermal backpressure loop honoring `PowerManager.THERMAL_STATUS_SEVERE`.
3. **Storage & Database Regression Suite:**
   - Scoped storage isolation: original files in `Movies/originals/`, output in `Movies/downloads/`.
   - Room migration integrity with cascading delete on foreign keys.
   - MediaStore registration and gallery indexing.
4. **UI & Theme Regression Suite:**
   - Strict adherence to [DESIGN.md](file:///D:/Webapp/UniDownco/DESIGN.md) design tokens (`UniColors`, `UniSpacing`, `UniTypography`).
   - Zero hardcoded colors, arbitrary padding, or non-conforming shapes.
   - Full state handling: Loading, Empty, Error, Active Progress, and Success states.

---

## 7. COMPLETE 12-PHASE DEVELOPMENT ROADMAP

The development sequence is organized into **12 granular, independently testable phases**. Each phase is an atomic unit requiring explicit user approval before execution.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       REVISED 12-PHASE DEVELOPMENT PLAN                     │
├─────────────────────────────────────────────────────────────────────────────┤
│ Phase 1: Stage 2.3.1.1 HDR10 Metadata Correction & Bitstream Verification   │
│ Phase 2: Built-in Media Inspector & Deep Analysis Dialog (Phase B6)         │
│ Phase 3: Built-in Video Trimmer & Fast Stream-Copy Engine (Phase B1)        │
│ Phase 4: Audio Extractor & Media Format Converter (Phases B3 & B5)          │
│ Phase 5: Advanced Video Compressor with CRF & Target Size Controls (B2)    │
│ Phase 6: Advanced Downloader: Batch, Playlists & Format Picker (A1, A2, A5) │
│ Phase 7: Downloader Reliability: Scheduling, Error Classifier & Subs (A3-A7)│
│ Phase 8: Smart Library: Categories, Filtering, Storage & Backup (C1 - C6)   │
│ Phase 9: Video Enhancement: Cinematic Color Grading & 3D LUT (D1, D2, D3)   │
│ Phase 10: Video Enhancement: Film Grain, Watermarks & Fused Pipeline (D4,D5)│
│ Phase 11: Premium UX & Background Experience (Correction A)                 │
│ Phase 12: Final Quality, Security, Compatibility & Release Readiness (Corr B│
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### PHASE 1: STAGE 2.3.1.1 HDR10 METADATA CORRECTION & END-TO-END BITSTREAM VERIFICATION
- **Purpose:** Eliminate unverified metadata assumptions, correct byte layouts across all 5 metadata representations, and establish automated bitstream SEI NAL verification.
- **Scope:**
  - Audit and correct CTA-861.3 Little-Endian binary layout for `KEY_HDR_STATIC_INFO`.
  - Implement dynamic Content Light Level calculation ($\text{MaxCLL}$ / $\text{MaxFALL}$) based on decoded pixel values; establish standards-compliant omission policy when unmeasured.
  - Create bitstream NAL parser verifying HEVC SPS (`profile_idc == 2`, `bit_depth == 10`), VUI (BT.2020 / ST 2084), and Prefix SEI payloads 137 and 144.
  - Verify MP4 container atoms (`colr`, `mdcv`, `clli`) with correct Big-Endian scaling.
  - Verify Adreno EGL 10-bit surface transfer fidelity.
  - Formalize physical verification requirements for Snapdragon 8 Gen 3 hardware.
- **Explicit Exclusions:** Color grading UI, 3D LUTs, film grain, frame rate conversion.
- **Prerequisites:** Completed Stage 2.3.1 code baseline.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/engine/hdr/HdrStaticMetadata.kt`
  - `app/src/main/java/com/unidownco/engine/processor/VideoProcessingEngine.kt`
  - `app/src/main/java/com/unidownco/engine/verifier/ProcessingOutputValidator.kt`
  - `app/src/test/java/com/unidownco/ColorScienceTest.kt`
  - `app/src/test/java/com/unidownco/HdrPipelineValidationTest.kt`
- **Acceptance Criteria:**
  - No hardcoded 1000/400 metadata emitted for arbitrary inputs.
  - Bitstream parser confirms exact SEI payload 137 and 144 syntax in generated elementary stream.
  - Unit tests confirm MP4 atom byte layouts match ISO/IEC 14496-12 and ISO/IEC 23008-12.
  - Xiaomi SM6225 device tests continue passing 18/18 with honest 8-bit gating.
  - Explicit statement recorded: `MAIN10 PHYSICAL DEVICE VERIFICATION: NOT EXECUTED`.
- **Approval Gate:** Dedicated verification report; requires explicit user approval before Phase 2.

---

### PHASE 2: BUILT-IN MEDIA INSPECTOR & TECHNICAL ANALYSIS (PHASE B6)
- **Purpose:** Provide users and engineers with a frame-accurate, deep technical inspection tool.
- **Scope:**
  - Build deep inspection modal in `feature/details`.
  - Extract container atoms, video codec profile/level, exact frame rate, VFR timestamps, bit depth, color primaries, transfer curve, matrix coefficients, audio sample rate, channels, bitrates.
  - Format all technical specs using JetBrains Mono typography per `DESIGN.md`.
- **Explicit Exclusions:** Transcoding, editing, or trimming.
- **Prerequisites:** Phase 1 completion.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/feature/details/VideoDetailsScreen.kt`
  - `app/src/main/java/com/unidownco/engine/analyzer/VideoAnalyzerImpl.kt`
  - `app/src/main/java/com/unidownco/core/database/VideoMetadataEntity.kt`
- **Acceptance Criteria:**
  - Accurate display of technical metadata verified against FFprobe on standard test files.
  - Compliant with `DESIGN.md` component and typography tokens.
- **Approval Gate:** Separate explicit user approval required.

---

### PHASE 3: BUILT-IN VIDEO TRIMMER & FAST STREAM-COPY ENGINE (PHASE B1)
- **Purpose:** Enable fast, frame-accurate video trimming without quality loss when possible.
- **Scope:**
  - Interactive trimming UI with thumbnail filmstrip and start/end handles.
  - Fast-path lossless stream-copy trimmer via `MediaExtractor` + `MediaMuxer` (cutting on keyframes).
  - Fallback frame-accurate re-encoding trimmer via `VideoProcessingEngine` for sub-GOP accuracy.
  - Scoped storage protection with temporary `.part` buffers and atomic promotion.
- **Explicit Exclusions:** Audio mixing, color grading, multi-clip concatenation.
- **Prerequisites:** Phase 2 completion.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/feature/toolkit/TrimmerScreen.kt` (New)
  - `app/src/main/java/com/unidownco/engine/toolkit/VideoTrimmerEngine.kt` (New)
  - `app/src/main/java/com/unidownco/core/design/components/FilmstripScrubber.kt` (New)
- **Acceptance Criteria:**
  - Lossless trim completes in $< 2$ seconds for a 5-minute video.
  - Frame-accurate trim maintains audio/video sync within 50ms.
  - Original source file SHA-256 remains untouched.
- **Approval Gate:** Separate explicit user approval required.

---

### PHASE 4: AUDIO EXTRACTOR & MEDIA FORMAT CONVERTER (PHASES B3 & B5)
- **Purpose:** Extract standalone audio tracks and convert media containers without re-encoding when possible.
- **Scope:**
  - Audio Extractor: lossless demuxing of AAC/Opus tracks into `.m4a` / `.opus`, transcode extraction to MP3 via in-app engine.
  - Format Converter: remuxes MKV/WebM to MP4 where streams are compatible; transcode fallback when required.
- **Explicit Exclusions:** Multi-track audio mixing or equalization.
- **Prerequisites:** Phase 3 completion.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/engine/toolkit/AudioExtractorEngine.kt` (New)
  - `app/src/main/java/com/unidownco/engine/toolkit/FormatConverterEngine.kt` (New)
  - `app/src/main/java/com/unidownco/feature/toolkit/AudioExtractorDialog.kt` (New)
- **Database Impact:** Room database migration from v3 to v4 (`mediaType` column added).
- **Acceptance Criteria:**
  - Extracted audio plays back with correct sample rate and duration.
  - Remuxed MP4 files open cleanly in native Android MediaPlayer.
- **Approval Gate:** Separate explicit user approval required.

---

### PHASE 5: ADVANCED VIDEO COMPRESSOR (PHASE B2)
- **Purpose:** Provide user-controlled video compression targeting specific file sizes or quality levels.
- **Scope:**
  - Compression UI with target file size slider and visual presets (Fast 720p, Balanced 1080p, Archive HEVC).
  - Bitrate calculator: $R_v = \frac{\text{TargetSize} - \text{AudioSize}}{\text{Duration}}$.
  - Real-time progress and compression ratio display.
- **Explicit Exclusions:** Multi-pass VBR (hardware limitations).
- **Prerequisites:** Phase 4 completion.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/feature/toolkit/CompressorScreen.kt` (New)
  - `app/src/main/java/com/unidownco/engine/toolkit/VideoCompressorEngine.kt` (New)
- **Acceptance Criteria:**
  - Output file size matches target within $\pm 10\%$.
  - Audio and video synchronization preserved.
- **Approval Gate:** Separate explicit user approval required.

---

### PHASE 6: ADVANCED DOWNLOADER: BATCH, PLAYLISTS & FORMAT PICKER (PHASES A1, A2, A5)
- **Purpose:** Expand downloader capabilities to multi-URL inputs, YouTube playlists, and pre-download stream inspection.
- **Scope:**
  - Batch URL input text area with auto-paste and duplicate detection.
  - Playlist parser supporting selective item checkmarks and bulk enqueueing.
  - Pre-download format inspection bottom sheet displaying resolution, codec, audio bitrate, and estimated size.
- **Explicit Exclusions:** Scheduled downloads (Phase 7), credential vault (Phase 7).
- **Prerequisites:** Phase 5 completion.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/feature/home/BatchDownloadDialog.kt` (New)
  - `app/src/main/java/com/unidownco/feature/home/PlaylistSelectionDialog.kt` (New)
  - `app/src/main/java/com/unidownco/data/extractor/PlaylistExtractor.kt` (New)
  - `app/src/main/java/com/unidownco/data/repository/DownloadRepository.kt`
- **Database Impact:** Add `batchId` and `playlistId` columns to `downloads`.
- **Acceptance Criteria:**
  - 10-item batch enqueues and downloads reliably without memory leaks.
  - 50-item YouTube playlist parses and allows selective download.
- **Approval Gate:** Separate explicit user approval required.

---

### PHASE 7: DOWNLOADER RELIABILITY: SCHEDULING, ERROR CLASSIFIER, SUBTITLES & SECURE SESSIONS (PHASES A3, A4, A6, A7)
- **Purpose:** Maximize download resilience under adverse network, rate-limited, and authenticated conditions.
- **Scope:**
  - `WorkManager` scheduler: Wi-Fi-only, Charging-only, and scheduled start times.
  - Error Classifier: distinct handling for HTTP 429 (rate limit exponential backoff), HTTP 403 (expired auth), and network drops.
  - Subtitle discovery (`yt-dlp --list-subs`), download, and optional MP4 soft-muxing.
  - Android Keystore session vault (`AES-256-GCM`) for encrypted cookie/credential storage.
- **Explicit Exclusions:** CAPTCHA bypass, rate-limit evasion, proxy rotation (strictly forbidden).
- **Prerequisites:** Phase 6 completion.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/core/network/DownloadSchedulerWorker.kt` (New)
  - `app/src/main/java/com/unidownco/core/common/ErrorClassifier.kt` (New)
  - `app/src/main/java/com/unidownco/engine/subtitles/SubtitleManager.kt` (New)
  - `app/src/main/java/com/unidownco/core/security/SessionVault.kt` (New)
- **Acceptance Criteria:**
  - Scheduled download respects Wi-Fi and charging constraints.
  - HTTP 429 triggers exponential backoff without crashing.
  - Keystore secrets are excluded from Android Auto Backup.
- **Approval Gate:** Separate explicit user approval required.

---

### PHASE 8: SMART LIBRARY: CATEGORIES, FILTERING, STORAGE DASHBOARD, DUPLICATE DETECTION & BACKUP (PHASES C1 - C6)
- **Purpose:** Deliver a comprehensive media management experience with storage intelligence.
- **Scope:**
  - Categorization tabs: All, Videos, Audio, Subtitles, Processed.
  - Multi-tag search and filter drawer (Platform, Resolution, Codec, HDR, Date).
  - SHA-256 duplicate scanner with safe deletion confirmation.
  - Storage analytics dashboard with visual breakdown and temporary file cleanup.
  - JSON database backup export and restoration.
  - Custom user collections and playlists.
- **Explicit Exclusions:** Remote cloud synchronization.
- **Prerequisites:** Phase 7 completion.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/feature/library/LibraryScreen.kt`
  - `app/src/main/java/com/unidownco/feature/library/LibraryViewModel.kt`
  - `app/src/main/java/com/unidownco/feature/library/StorageDashboardScreen.kt` (New)
  - `app/src/main/java/com/unidownco/data/repository/BackupRepository.kt` (New)
- **Database Impact:** Room database migration adding `collections` and `collection_items` tables.
- **Acceptance Criteria:**
  - 500+ library items filter smoothly at 60 FPS.
  - Orphan cleanup removes dangling `.part` files safely.
  - Backup JSON export and restore produces identical database state.
- **Approval Gate:** Separate explicit user approval required.

---

### PHASE 9: VIDEO ENHANCEMENT: CINEMATIC COLOR GRADING & 3D LUT INTEGRATION (PHASES D1, D2, D3)
- **Purpose:** Introduce studio-grade color adjustment and 3D lookup table rendering.
- **Scope:**
  - Frame rate cadence conversion (25, 30, 50, 60 FPS, Original).
  - Cinematic Color Grading fragment shader: Exposure, Contrast, Saturation, Temperature, Tint, Highlights/Shadows.
  - Standard `.cube` 3D LUT parser (17x17x17 and 33x33x33), uploaded as `GL_TEXTURE_3D` with trilinear filtering.
  - Real-time preview composable before transcode commitment.
- **Explicit Exclusions:** Optical-flow AI motion interpolation (research only).
- **Prerequisites:** Phase 1 and Phase 8 completion.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/engine/hdr/gpu/CinematicColorShader.kt` (New)
  - `app/src/main/java/com/unidownco/engine/lut/CubeLutParser.kt` (New)
  - `app/src/main/java/com/unidownco/feature/enhancement/ColorGradingScreen.kt` (New)
  - `app/src/main/java/com/unidownco/engine/hdr/gpu/HdrRenderCore.kt`
- **Database Impact:** Add color grading and LUT parameters to `processing_jobs`.
- **Acceptance Criteria:**
  - `.cube` LUT renders accurately without banding or hue shift.
  - Color grading shader executes in $< 5\text{ms}$ per 1080p frame on Adreno 610.
  - Output HEVC bitstream conforms to standard.
- **Approval Gate:** Separate explicit user approval required.

---

### PHASE 10: VIDEO ENHANCEMENT: FILM GRAIN, WATERMARKS & UNIFIED PROCESSING PIPELINE (PHASES D4, D5, D9)
- **Purpose:** Complete the video processing studio with organic film grain, watermark overlays, and a single-pass fused GPU pipeline.
- **Scope:**
  - Parametric procedural film grain simulation in GLSL with temporal noise seed and luminance mask.
  - 2D alpha texture compositor for custom text and PNG logo watermarks with temporal bounds.
  - Dynamic fused GPU pipeline chaining active shader stages into minimal render passes:
    $$\text{Decode} \rightarrow \text{Linearize} \rightarrow \text{Gamut} \rightarrow \text{Grade} \rightarrow \text{LUT} \rightarrow \text{ITM/PQ} \rightarrow \text{Grain} \rightarrow \text{Overlay} \rightarrow \text{Encode}$$
- **Explicit Exclusions:** Dolby Vision authoring (strictly excluded).
- **Prerequisites:** Phase 9 completion.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/engine/hdr/gpu/FilmGrainShader.kt` (New)
  - `app/src/main/java/com/unidownco/engine/overlay/WatermarkCompositor.kt` (New)
  - `app/src/main/java/com/unidownco/engine/hdr/gpu/FusedEnhancementPipeline.kt` (New)
- **Database Impact:** Add grain and watermark parameters to `processing_jobs`.
- **Acceptance Criteria:**
  - Fused pipeline processes 1080p 30 FPS video without dropping frames or memory leakage.
  - Film grain appears natural and grain clumping is absent in deep blacks.
  - Watermark alpha blending renders sharply without color clipping.
- **Approval Gate:** Separate explicit user approval required.

---

### PHASE 11: PREMIUM UX & BACKGROUND EXPERIENCE (CORRECTION A)
- **Purpose:** Harden application lifecycle resilience, background service compliance, queue orchestration, and user experience polish.
- **Scope:**
  - **Background Experience:**
    - Audit and enforce Android 14/15/16 background execution compliance for `DownloadForegroundService` (type `dataSync`).
    - Implement persistent notification controls (Pause, Resume, Cancel) with real-time throughput and byte counts.
    - Robust recovery across app process death, low-memory kills (LMK), and device reboots via `StartupReconciliationWorker`.
    - Clear distinction between transient retries and permanent terminal failures.
  - **Queue Management:**
    - Drag-and-drop queue priority reordering.
    - Visual grouping by platform, status, and batch/playlist.
    - Concurrency throttle: prevent simultaneous heavy transcode jobs while allowing parallel downloads.
  - **Picture-in-Picture (PiP):**
    - Support native Android PiP mode during video preview and playback.
    - Handle seamless lifecycle transitions (`onUserLeaveHint`, aspect ratio preservation, auto-enter PiP).
  - **User Experience & Accessibility:**
    - Polished loading, empty, offline, permission, error, and recovery states adhering strictly to `DESIGN.md`.
    - Full TalkBack accessibility labeling, touch target minimums ($48\times 48\text{dp}$), and dynamic font scaling.
    - Responsive layout adaptation across compact phones, foldables, and large tablet screens.
- **Explicit Exclusions:** Web-based interfaces (native Compose strictly required).
- **Prerequisites:** Phases 6, 7, 8, 9, 10 completion.
- **Files Affected:**
  - `app/src/main/java/com/unidownco/core/network/DownloadForegroundService.kt`
  - `app/src/main/java/com/unidownco/feature/queue/QueueScreen.kt`
  - `app/src/main/java/com/unidownco/feature/queue/QueueViewModel.kt`
  - `app/src/main/java/com/unidownco/feature/player/PipPlayerActivity.kt` (New)
- **Acceptance Criteria:**
  - Background downloads continue across 30+ minutes of screen-off deep sleep.
  - Process death recovery restores queue state without duplicate files or database corruption.
  - PiP transitions cleanly without video flicker or audio stutters.
  - 100% TalkBack accessibility audit pass.
- **Approval Gate:** Separate explicit user approval required.

---

### PHASE 12: FINAL QUALITY, SECURITY, COMPATIBILITY & RELEASE READINESS (CORRECTION B)
- **Purpose:** Perform final stabilization, end-to-end multi-platform audit, security hardening, and release packaging verification.
- **Scope:**
  - **Downloader Integrity Audit:** Verify YouTube, Facebook, Direct HTTP, and Instagram routing under active rate limits and edge cases.
  - **Media-Processing Integrity Audit:** Verify 8-bit HEVC, 10-bit HDR (on compatible hardware), rotation integrity, audio synchronization, and container health.
  - **Storage & Database Audit:** Verify Room migration path ($v1 \rightarrow v2 \rightarrow v3 \rightarrow v4 \rightarrow v5$), scoped storage rules, and orphan cleanup.
  - **Security & Privacy Audit:** Confirm Android Keystore encryption, zero-log sanitization of sensitive URLs/tokens, and strict dependency license compliance (LGPL/Apache).
  - **Performance & Thermal Audit:** Verify memory footprint ($< 150\text{ MB}$ typical), zero memory leaks (LeakCanary audit), and thermal backpressure.
  - **Release Governance & Packaging:**
    - Verification of release package ID (`com.unidownco`) and debug ID (`com.unidownco.debug`).
    - APK Signature Scheme v2/v3 verification.
    - Automated generation of SHA-256 checksums across all ABI splits (`arm64-v8a`, `armeabi-v7a`, `universal`, `x86_64`).
    - **Immutable Release Tag Policy:** Strict prohibition of force-updating or rewriting existing published tags (such as `v1.0.0`). Every release must use an incremental version code, a new version name, and a unique immutable Git tag.
- **Explicit Exclusions:** Production release publication without explicit user signoff.
- **Prerequisites:** All prior phases (Phases 1 through 11) completed and verified.
- **Deliverables:**
  - `FINAL_RELEASE_VERIFICATION_REPORT.md`
  - Final Release Readiness Checklist with signoff gates.
- **Acceptance Criteria:**
  - 100% pass rate across automated regression suite (host unit tests + instrumentation tests).
  - Zero critical security vulnerabilities and clean dependency audit.
  - Immutable tag and release documentation prepared.
- **Approval Gate:** Final release publication requires separate, explicit user authorization.

---

## 8. DEPENDENCY GRAPH & CRITICAL PATH

```
[Phase 1: Stage 2.3.1.1 HDR10 Verification] ────────► MUST BE COMPLETED FIRST
                     │
                     ▼
[Phase 2: Media Inspector & Analysis] ──────────────► Required for inspecting outputs
       │                         │
       ▼                         ▼
[Phase 3: Video Trimmer]   [Phase 4: Audio Extractor & Converter]
       │                         │
       ▼                         ▼
[Phase 5: Advanced Video Compressor]
       │
       ▼
[Phase 6: Downloader Batch & Playlists] ────────────► Downloader Enhancement Branch
       │
       ▼
[Phase 7: Downloader Reliability & Subs]
       │
       ▼
[Phase 8: Smart Library & Storage Dashboard] ────────► Library & Data Hub
       │
       ▼
[Phase 9: Enhancement: Color Grading & 3D LUT] ──────► Enhancement Branch
       │
       ▼
[Phase 10: Enhancement: Grain, Watermarks & Fused]
       │
       ▼
[Phase 11: Premium UX & Background Experience] ──────► Full App Polish
       │
       ▼
[Phase 12: Final Quality, Security & Release Readiness] ──► Release Gate
```

---

## 9. DATABASE ARCHITECTURE & MIGRATION STRATEGY

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
  - Explicit `Migration(4, 5)` with foreign key constraints intact.

---

## 10. PERFORMANCE, MEMORY & THERMAL BUDGET

| Metric / Resource | Xiaomi SM6225 (Baseline) | OnePlus Snapdragon 8 Gen 3 (Target) | Failure Threshold / Safety Policy |
|---|---|---|---|
| **Max Frame Queue** | $\le 4$ frames in memory | $\le 8$ frames in memory | $> 12$ frames triggers decoder backpressure |
| **Max Native Heap** | $< 120\text{ MB}$ | $< 250\text{ MB}$ | $> 350\text{ MB}$ triggers GC / buffer flush |
| **GPU Texture Budget** | 1 intermediate FBO ($1080\text{p}$) | 2 intermediate FBOs ($4\text{K}$) | FBO reuse mandatory; no allocations per frame |
| **Peak Transcode FPS** | $45 - 60\text{ FPS}$ ($1080\text{p}$) | $180 - 240\text{ FPS}$ ($1080\text{p}$) | $< 15\text{ FPS}$ triggers complexity warning |
| **Thermal Mitigation** | Throttle at `THERMAL_STATUS_SEVERE` | Throttle at `THERMAL_STATUS_SEVERE` | Pause job at `CRITICAL`; resume on `MODERATE` |

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
| **2. Lack of Physical SM8650 Device for Main10 Verification** | HIGH | MEDIUM | Formal gating: Mark Main10 as `NOT EXECUTED (Hardware not physically connected)`; test on connected hardware when available. |
| **3. Thermal Throttling during Long Transcode Jobs** | MEDIUM | HIGH | Enforce backpressure using `PowerManager.currentThermalStatus`; sleep 10ms on SEVERE; pause on CRITICAL. |
| **4. Storage Exhaustion during High-Bitrate Transcodes** | HIGH | LOW | Pre-flight disk space check requiring $\ge 2.5\times$ estimated output size before transcode begins. |
| **5. Large 3D LUT Memory Overhead** | MEDIUM | LOW | Restrict imported `.cube` LUT size to maximum $33\times 33\times 33$ ($143\text{ KB}$ raw texture memory). |
| **6. Background Process Termination by OS** | MEDIUM | MEDIUM | Foreground service with ongoing notification, `WorkManager` fallback, startup queue reconciliation. |

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
Every implementation phase in Section 7 is an **independent milestone**. Completing one phase **does NOT authorize beginning the next phase**. The agent must stop, report verified test results, and wait for explicit user approval before proceeding.

### Recommended First Implementation Stage
**Recommendation:** **PHASE 1 (STAGE 2.3.1.1: HDR10 METADATA CORRECTION & END-TO-END BITSTREAM VERIFICATION)**

#### Justification:
1. **Closes the Open Architectural Foundation:** Stage 2.3.1 established the mathematical and shader foundation, but the outstanding concerns (hardcoded 1000/400 nits, binary layout verification, and bitstream NAL unit inspection) must be closed to ensure a mathematically unshakeable video enhancement engine.
2. **Zero Regression Risk:** Phase 1 focuses on verification and mathematical refinement without introducing risky UI alterations or database migrations.
3. **Essential Prerequisite for Color Studio:** Developing 3D LUTs (Phase 9) or Film Grain (Phase 10) on top of an unverified color pipeline creates technical debt. Verifying the bitstream first ensures every subsequent video enhancement feature is built upon verified silicon reality.
