# Changelog

All notable changes to the UniDownco project will be documented in this file.

## [1.0.5-step1.3.3] - 2026-11-15
### Added
- **Release Optimization & Standalone ABI Splits**:
  - Implemented Gradle ABI splits in `app/build.gradle.kts` generating standalone APKs for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, alongside universal APKs and production Android App Bundles (`.aab`).
  - Achieved dramatic APK size reduction: Standalone `arm64-v8a` release APK is **55.8 MB** (67.3% reduction compared to the 170.9 MB universal debug build).
  - Configured R8 minification and hardened `proguard-rules.pro` for Jackson databind, youtubedl-android runtime, Apache Commons, Room, and Domain models.
- **Physical Device Release Verification**:
  - Successfully installed and verified `app-arm64-v8a-release.apk` on physical Xiaomi Redmi Note (`25062RN2DA`, Android 16 / API 36).
  - Confirmed clean cold launch, zero runtime crashes, and proper JNI native binding.
- **Multi-Platform Extraction & Download Verification Matrix**:
  - Executed physical on-device extraction and download tests across 7 target platforms.
  - YouTube: PASS (adaptive video + audio merge, verified, saved to `Movies/UniDownco/`).
  - Direct MP4: PASS (HTTP range streaming, hardware verified, exported to MediaStore).
  - Facebook: PASS (extracted progressive streams, verified, exported to MediaStore).
  - Reddit: AUTH REQUIRED / BLOCKED (HTTP 403 on session endpoint, cookies required).
  - TikTok: FAIL / BLOCKED (anti-bot signature challenge / IP block).
  - Instagram: AUTH REQUIRED (login session cookies required).
  - X / Twitter: AUTH REQUIRED / BLOCKED (syndication API rate limits / cookies required).
- **Foreground Service Android 16 Compatibility**:
  - Validated `DownloadForegroundService` with `foregroundServiceType="dataSync"`, notification channels, ongoing progress updates, and clean lifecycle management.
- **Legal & License Inventory**:
  - Expanded `THIRD_PARTY_NOTICES.md` with full attribution for QuickJS, Jackson JSON Processor, Apache Commons, and test libraries.
- **Unit Testing Suite**:
  - Executed full test suite with 38 unit tests passing (100% pass rate).

## [1.0.4-step1.3.2] - 2026-11-15
### Added
- **Resolution Selection Bottom Sheet & Sticky Download Action**:
  - Completely redesigned format selection modal sheet with a scrollable `LazyColumn` for formats and a persistent, sticky bottom bar.
  - Added primary signature action button **"DOWNLOAD {RESOLUTION}"** (e.g., `DOWNLOAD 1080P`, `DOWNLOAD 720P`) using `UniAccentButton`.
  - Displays codec, container (MP4/WebM), frame rate, estimated file size, audio merge status, and highlighted selection border.
  - Automatically enqueues the exact selected format ID and immediately transitions the user to the active Queue tab.
- **Button System Overhaul**:
  - Implemented consistent button hierarchy in `UniButtons.kt`:
    - `UniPrimaryButton`: Black background (`#000000`), white text, 1dp border, 10dp corners.
    - `UniAccentButton`: Signature Yellow (`#FFD429`), black text, 1dp black border, 10dp corners.
    - `UniOutlinedButton`: White/surface background, black text, 1dp subtle border.
    - `UniDestructiveButton`: Red tint container (`#FCE8E6`), red text (`#D83B01`), red border.
    - `UniCompactButton`: 34dp height for in-field quick actions (Paste, Clear).
- **Bottom Navigation Bar Redesign**:
  - Rebuilt `UniBottomNavBar` with fixed-width weighted tab slots, persistent readable labels, and zero layout shifting when switching destinations.
  - Replaced ad-hoc pill backgrounds with an elegant, static yellow indicator line on the active tab.
  - Added Android 15/16 system navigation bar insets (`navigationBarsPadding()`) preventing overlap with system gesture navigation.
  - Integrated static active download count badge on Queue icon.
- **Structured Bounded Logging & Log Viewer Screen**:
  - Implemented `UniLog` with 500-entry in-memory ring buffer, 2MB rotating disk log file, and automatic redaction of sensitive credentials, tokens, and query parameters.
  - Created dedicated `LogViewerScreen` accessible directly from Settings:
    - Real-time search by keyword, tag, or message.
    - Severity filtering chips: `ALL`, `DEBUG`, `INFO`, `WARN`, `ERROR` with live counts.
    - Click to expand/collapse long error messages.
    - "COPY ENTRY" and "COPY VISIBLE LOGS" to system clipboard.
    - "EXPORT LOGS (.TXT)" via Android Storage Access Framework (`CreateDocument("text/plain")`).
    - "CLEAR LOGS" with confirmation dialog.
- **Unified Storage Destination (`Movies/UniDownco/`)**:
  - Unified all downloaded video publishing under a single consistent folder: `Movies/UniDownco/`.
  - Implemented safe duplicate collision handling and MediaStore Scoped Storage insertion.
  - Added legacy storage permission (`WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`) for Android 9 and below.
  - Displayed destination folder cleanly in Settings and Home storage cards.
- **Automated Tests**:
  - Added `UniLogTest.kt` verifying token redaction, level filtering, search, and export formatting.
  - Added `DownloadFlowAndStorageTest.kt` verifying destination paths, filename sanitization, button text, and bottom nav tabs.
  - 100% test pass rate across all 30 unit tests.

## [1.0.3-step1.3] - 2026-11-15
### Added
- **Operational yt-dlp & FFmpeg Runtime Integration**:
  - Integrated `io.github.junkfood02.youtubedl-android:library:0.18.1` and `ffmpeg:0.18.1` bundling CPython (`libpython.so`), QuickJS (`libqjs.so`), and FFmpeg (`libffmpeg.so`).
  - Configured `packaging.jniLibs.useLegacyPackaging = true` in Gradle build configuration to extract native libraries safely, strictly complying with Android 16's W^X memory execution enforcement.
  - Added comprehensive `THIRD_PARTY_NOTICES.md` documenting GPL-3.0 copyleft obligations, source availability, and third-party dependencies (yt-dlp, FFmpeg, Python, QuickJS, OkHttp, Room).
- **Engine Lifecycle & In-App Dynamic Updater**:
  - Created `YtDlpEngineManager` providing thread-safe background initialization (`init` in `UniDownApplication.onCreate`) and live engine capability verification.
  - Implemented dynamic upstream yt-dlp updates directly from within the app via `YoutubeDL.updateYaml(...)`.
- **Primary yt-dlp Extractor Provider with Multi-Format Mapping**:
  - Upgraded `YtDlpExtractorProvider` to priority 100 to serve as the primary extraction pipeline for social platforms (YouTube, TikTok, Instagram, Reddit, X/Twitter, Facebook).
  - Automatically translates yt-dlp format trees, pairings of separate adaptive video and audio tracks, duration, thumbnail, and author metadata into UniDownco domain models (`VideoExtractionInfo`, `FormatOption`).
  - Adjusted `NativeExtractorProvider` to priority 80 to act as seamless zero-dependency fallback.
- **Dual-Strategy Resilient Download Engine**:
  - Direct static MP4 files continue utilizing high-speed chunked streaming via OkHttp with HTTP Range resume support (`Range: bytes=X-`).
  - Social media and adaptive DASH/HLS streams route through `YoutubeDL.getInstance().execute(...)` with format selection (`-f {format_id}+bestaudio/best`), MP4 remuxing (`--merge-output-format mp4`), and live regex throughput/progress parsing.
  - Bound cancellation and pause to `YoutubeDL.getInstance().destroyProcessById(...)`.
- **System Share Sheet & Auto-Download Automation**:
  - Configured `MainActivity` with `android:launchMode="singleTask"` and intent receiver for `android.intent.action.SEND`.
  - Added auto-download parameter to automatically analyze, select optimal format, and queue downloads when shared from external social media apps.
- **Settings Screen Runtime Management**:
  - Added "EXTRACTION & MEDIA RUNTIME" card in `SettingsScreen` showing yt-dlp version, target device architecture (`arm64-v8a`), and interactive "UPDATE YT-DLP RUNTIME" action.
- **Real-Device Verification (Xiaomi Redmi Note 25062RN2DA, Android 16 / API 36)**:
  - Cold startup verification: `yt-dlp and FFmpeg initialized successfully in 112ms`.
  - Direct MP4 download verified (`flower.mp4`, 1,128,375 bytes, verified by `AndroidMediaVerifier` and exported to `/sdcard/Movies/UniversalHDR/`).
  - Real YouTube video download verified: `Me at the zoo` (`jNQXAC9IVRw`), 575,740 bytes, validated with `ffprobe` (H.264 320x240 @ 15fps, Opus 48kHz audio, ISO MP4 container).
  - Share intent automation verified via ADB.
  - All 26 unit tests passed (`testDebugUnitTest`).

## [1.0.2-step1.2] - 2026-11-15
### Added
- **Safe Non-Destructive Room Migration**:
  - Replaced `.fallbackToDestructiveMigration()` with explicit `Migration(1, 2)` altering table `downloads` with `streamUrl TEXT` and `audioStreamUrl TEXT`.
  - Added unit test `DatabaseMigrationTest` verifying SQL schema migration statements and version transitions.
  - Verified database preservation on physical device across updates without user data loss.
- **Extractor Provider Strategy Architecture**:
  - Created `ExtractorProvider` interface and `ExtractionCapability` enum (`DIRECT_STREAMS`, `ADAPTIVE_STREAMS`, `AUDIO_ONLY`, `HDR_METADATA`, `SESSION_AUTH`, `JS_DECIPHER`).
  - Implemented `NativeExtractorProvider` (priority 100) for zero-dependency native Kotlin extractors.
  - Implemented `YtDlpExtractorProvider` (priority 50) as an architectural decoupling layer for yt-dlp execution (companion IPC or external engine), isolating GPL-3.0 copyleft code and avoiding Android 14/15/16 W^X binary restrictions.
  - Refactored `UniversalExtractorRegistry` to coordinate prioritized multi-engine provider fallbacks.
  - Added comprehensive provider unit tests in `ExtractorProviderTest`.
- **Adaptive Stream Pairing in yt-dlp Bridge**:
  - Enhanced `YtDlpExtractorBridge` to automatically pair video-only adaptive streams with the best available audio-only track, resolving silent downloads for DASH/adaptive streams.
  - Added unit test `testParseYtDlpDumpJson_PairsAdaptiveVideoAndAudioStreams`.
- **Platform Extraction Enhancements & Error Diagnostics**:
  - **YouTube**: Added client attestation and `playabilityStatus` inspection to cleanly report bot-guard requirements.
  - **Reddit**: Added direct `v.redd.it` stream discovery (`DASH_720.mp4` + `DASH_AUDIO_128.mp4`) and accurate HTTP 403 / PoW JS bot challenge diagnosis.
  - **TikTok, Instagram, X/Twitter, Facebook**: Clean diagnostics for platform session authentication and bot gates with zero corrupt HTML downloads.
  - **UI Diagnostics**: Displayed exact failure reason cards on `HomeScreen` and `QueueScreen` (e.g., HTTP 403, client attestation).
- **Physical Device Verification (Redmi Note 25062RN2DA, Android 16 / API 36)**:
  - Verified on-device intent sharing, platform badge detection across all 6 social platforms, direct MP4 downloading (`flower.mp4`, 1.1 MB), `AndroidMediaVerifier` validation, MediaStore gallery export, and playback.

## [1.0.1-step1.1] - 2026-11-15
### Added
- **Physical Device Deployment & Verification**:
  - Connected and verified target Android device via ADB (`25062RN2DA`, Android 16 / API 36).
  - Deployed debug APK `app-debug.apk` and verified Scoped Storage export to `/sdcard/Movies/UniversalHDR/`.
- **Audio/Video Stream Separation & Muxing**:
  - Implemented `MediaStreamMuxer` using Android native `MediaExtractor` and `MediaMuxer` for lossless elementary stream remuxing without third-party dependencies.
  - Implemented concurrent/dual-stream download in `DownloadEngine` when `format.audioUrl` is present.
  - Configured adaptive format stream pairing in `YouTubeExtractor` and `RedditExtractor`.
- **Hardware-Level Media Verification**:
  - Implemented `AndroidMediaVerifier` using `MediaMetadataRetriever` to verify container headers (MP4/MKV/WebM), track presence, video resolution, and duration before marking downloads completed.
  - Successfully catches and rejects corrupt downloads and HTML error pages.
- **Extractor Robustness**:
  - Removed deceptive fallback formats that passed web page URLs as fake MP4s across YouTube, TikTok, Instagram, Reddit, X/Twitter, and Facebook extractors.
  - Standardized clear, actionable error messages for platform auth gates, bot protections, and signature deciphering requirements.
- **Testing & Persistence**:
  - Added `MuxerAndVerifierTest` for format option detection and verifier boundary testing.
  - Added on-device instrumented test `MediaStreamMuxerDeviceTest`.
  - Upgraded Room database schema to version 2 with `streamUrl` and `audioStreamUrl` persistence for robust pause/resume.

## [1.0.0-step1] - 2026-11-15
### Added
- **Project Foundation**:
  - Initialized Android Gradle project targeting Android 15 (API 35+), MinSdk 26.
  - Configured Gradle 8.11.1, Android Gradle Plugin 8.7.2, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Java 17.
  - Setup Room Database 2.6.1 with reactive Flow queries and schema.
  - Configured OkHttp 4.12.0 for high-performance chunked media streaming.
- **Design System (`DESIGN.md`)**:
  - Implemented the editorial design language: *White Canvas, Black Structure, Yellow Accent (`#FFD429`), Inter Typography*.
  - JetBrains Mono dedicated type tokens for technical specifications.
  - Reusable components: `UniPrimaryButton`, `UniOutlinedButton`, `UniCompactButton`, `UniUrlInputField`, `UniCard`, `UniStatusBadge`, `UniTechBadge`, `UniProgressBar`, `UniTopAppBar`, `UniBottomNavBar`.
- **Universal Downloader Engine**:
  - Pluggable `VideoExtractor` strategy architecture.
  - Support for YouTube (Innertube & oembed), TikTok, Instagram (Reels & posts), Reddit (`v.redd.it`), X/Twitter, Facebook, and direct media URLs.
  - `YtDlpExtractorBridge` for yt-dlp schema compatibility and format mapping.
  - Chunked download engine with HTTP `Range: bytes=X-` resume support, speed and ETA estimation.
- **Android Share Target & Service**:
  - Implemented `android.intent.action.SEND` receiver in `MainActivity` with automatic URL extraction from shared text.
  - Android 15 compliant Foreground Service (`foregroundServiceType="dataSync"`) with live progress notification.
  - Scoped Storage and MediaStore integration exporting completed files to `Movies/UniversalHDR/`.
- **Application Screens**:
  - `HomeScreen`: URL input, Paste action, platform detection, format selection modal, system metrics.
  - `QueueScreen`: Active downloads with live progress, pause, resume, cancel, and Step 2/3 processing placeholder.
  - `LibraryScreen`: Saved videos, platform filters, playback launcher, share sheet, gallery export.
  - `VideoDetailsScreen`: Technical specs (codec, resolution, fps, file size) and pipeline readiness.
  - `SettingsScreen`: Automation toggles, hardware profile info (Snapdragon 8 Gen 3 / Adreno 750), cache cleaner.
- **Future Engine Interfaces**:
  - Prepared clean interface boundaries for future steps: `VideoAnalyzer`, `VideoEncoder`, `HDRProcessor`, `FrameInterpolator`, `MediaVerifier`, `GalleryExporter`.
- **Automated Tests**:
  - 16 comprehensive unit tests covering URL validation, platform detection, share intent string sanitization, state transitions, entity mapping, and format parsing.
- **Documentation**:
  - Created `README.md`, `DESIGN.md`, `ARCHITECTURE.md`, `DOWNLOAD_ENGINE.md`, `CHANGELOG.md`.
