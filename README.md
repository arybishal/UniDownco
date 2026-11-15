# UniDownco

> **Universal Video Downloader & Automatic HDR Encoder for Android**  
> Target Platform: Android 15 (API 35+) • Snapdragon 8 Gen 3 / Adreno 750 (OnePlus Ace 5 / OnePlus 13R)

---

## Overview

**UniDownco** is a high-performance native Android application engineered to download online videos from major platforms and automatically transform them into high-fidelity HEVC HDR videos (10-bit Main10, PQ / BT.2020, 60 FPS motion processing) saved directly to the device gallery.

### Core Experience
$$\text{Share URL} \longrightarrow \text{Detect} \longrightarrow \text{Download} \longrightarrow \text{Analyze} \longrightarrow \text{Encode} \longrightarrow \text{Verify} \longrightarrow \text{Save to Gallery}$$

---

## Step 1 Completed Architecture

This release implements **Step 1: Project Foundation + Universal Video Downloader** (including Step 1.1, 1.2, and 1.3 real-device verification and yt-dlp runtime integration):
- **High-Contrast Design System**: Built strictly according to `DESIGN.md` (*White Canvas, Black Structure, Yellow Accent, Inter Typography*).
- **Universal Video Downloader Engine**:
  - **Operational yt-dlp & FFmpeg Runtime**: Embedded `youtubedl-android` (v0.18.1) with CPython, QuickJS, and FFmpeg for social platform extraction (YouTube, TikTok, Instagram, Reddit, X/Twitter, Facebook).
  - **Zero-Dependency Native Fallback**: Full native Kotlin extractors for direct MP4 streaming and baseline operations.
  - **Dual-Mode Download Pipeline**: High-speed chunked OkHttp range streaming for direct MP4s, alongside yt-dlp subprocess execution for DASH/HLS and adaptive video/audio merging.
  - **Dynamic In-App Updates**: Update yt-dlp core directly within the app settings.
- **Android Share Target Integration**: Seamless URL ingestion directly via Android's native `ACTION_SEND` Share Sheet with automatic format detection and download queuing.
- **Hardware-Level Media Verification**: Pre-export stream validation using Android's `MediaMetadataRetriever` to guarantee media integrity and prevent corrupt files from entering the gallery.
- **Room Persistence**: Non-destructive migrated database caching download history, active queues, and technical metadata.
- **Background Execution**: Compliant Android 14/15/16 foreground service with progress notification, speed calculation, ETA, and cancellation.
- **MediaStore & Scoped Storage**: Automatic export of completed videos into `Movies/UniDownco/` with immediate system gallery visibility.
- **UI/UX & Diagnostics Overhaul**:
  - Resolution selector with scrollable format cards and persistent sticky **"DOWNLOAD {RESOLUTION}"** primary action.
  - Rebuilt four-tab bottom navigation with fixed layout geometry, zero shifting, and Android 15/16 gesture insets.
  - Dedicated in-app **Log Viewer Screen** with real-time keyword search, severity filtering, credential redaction, and SAF export to `.txt`.
  - Consistent button hierarchy (`UniPrimaryButton`, `UniAccentButton`, `UniOutlinedButton`, `UniDestructiveButton`).
- **Encoding Engine Interface Preparation**: Clean architectural boundaries established for Step 2 (`VideoAnalyzer`, `VideoEncoder`) and Step 3 (`HDRProcessor`, `FrameInterpolator`, `MediaVerifier`).

---

## Technology Stack

- **Language**: Kotlin 2.0.21
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM + Clean Architecture
- **Local Database**: Room 2.6.1 + KSP
- **Networking**: OkHttp 4.12.0
- **Asynchronous**: Kotlin Coroutines & Flow
- **Background Work**: Android Foreground Service (`dataSync`), WorkManager
- **Storage**: Scoped Storage & Android MediaStore API
- **Target SDK**: Android 15 (compileSdk 35, targetSdk 35, minSdk 26)

---

## Verification & Build

To assemble the debug APK:
```bash
./gradlew assembleDebug
```
Output artifact: `app/build/outputs/apk/debug/app-debug.apk`

To execute unit tests:
```bash
./gradlew test
```
