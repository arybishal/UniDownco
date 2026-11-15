# Changelog

All notable changes to the UniDownco project will be documented in this file.

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
