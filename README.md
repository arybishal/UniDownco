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

This release implements **Step 1: Project Foundation + Universal Video Downloader**:
- **High-Contrast Design System**: Built strictly according to `DESIGN.md` (*White Canvas, Black Structure, Yellow Accent, Inter Typography*).
- **Universal Video Downloader Engine**: Multi-platform extractor architecture supporting:
  - **YouTube**: Stream format discovery, audio/video stream resolution via Innertube API.
  - **TikTok**: Direct MP4 extraction without watermark.
  - **Instagram**: Reels and post MP4 video extraction.
  - **Reddit**: `v.redd.it` and post video stream extraction with audio synchronization metadata.
  - **X / Twitter**: Syndication API integration parsing high-bitrate MP4 variants.
  - **Facebook**: HD / SD video stream discovery.
  - **Generic / Direct**: MP4, WebM, MKV, and HTML5 video meta tag parsing.
  - **yt-dlp Compatibility Bridge**: Format schema translation and mapping.
- **Android Share Target Integration**: Seamless URL ingestion directly via Android's native `ACTION_SEND` Share Sheet.
- **Room Persistence**: Robust database caching download history, active queues, video metadata, and progress.
- **Background Execution**: Compliant Android 14/15 foreground service with progress notification, speed calculation, ETA, and cancellation.
- **MediaStore & Scoped Storage**: Automatic export of completed videos into `Movies/UniversalHDR/` with immediate system gallery visibility.
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
