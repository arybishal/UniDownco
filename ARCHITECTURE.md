# UniDownco Architecture Specification

## 1. Architectural Philosophy
UniDownco follows **Clean Architecture** with **MVVM (Model-View-ViewModel)** separation of concerns.

The application is split into structured layers:
```
app/
 ├── core/
 │    ├── common/      # Domain enums (Platform), Result wrapper, UrlValidator, Formatters
 │    ├── database/    # Room entities, DAOs, Database singleton
 │    ├── design/      # Tokens (Color, Typography, Spacing, Shapes) and reusable UI components
 │    ├── network/     # OkHttp client, Foreground Service
 │    └── storage/     # StorageManager, Scoped Storage, MediaStore integration
 ├── data/
 │    ├── extractor/   # VideoExtractor interface and platform-specific extractors
 │    └── repository/  # DownloadRepository coordinating DB, network, storage
 ├── domain/
 │    ├── models/      # DownloadItem, VideoExtractionInfo, FormatOption, statuses
 │    └── usecases/    # ValidateUrl, ExtractVideoInfo, EnqueueDownload, ManageDownloadQueue
 ├── engine/
 │    ├── downloader/  # Chunked download streaming engine with resume and speed calculation
 │    ├── analyzer/    # VideoAnalyzer interface (Step 2 preparation)
 │    ├── encoder/     # VideoEncoder interface (Step 2 preparation)
 │    ├── hdr/         # HDRProcessor interface (Step 3 preparation)
 │    ├── motion/      # FrameInterpolator interface (Step 3 preparation)
 │    ├── verifier/    # MediaVerifier interface (Step 2/3 preparation)
 │    └── exporter/    # GalleryExporter interface
 └── feature/
      ├── home/        # URL input, paste, format bottom sheet, metrics overview
      ├── queue/       # Live active download queue and processing placeholder
      ├── library/     # Saved video collection, platform filters, playback, export
      ├── details/     # Technical metadata inspection & pipeline readiness
      └── settings/    # Downloader settings, hardware target profile, cache clearing
```

---

## 2. Downloader Engine & Provider Architecture

The extraction engine uses an extensible multi-provider strategy architecture:
```
ExtractorProvider (Interface)
  ├── YtDlpExtractorProvider  (Priority 100, Operational yt-dlp & FFmpeg runtime)
  │     ├── Native CPython + QuickJS + FFmpeg binary extraction
  │     ├── Platform JS signature deciphering & visitor token synthesis
  │     ├── Multi-format mapping & automatic adaptive stream pairing
  │     └── In-app dynamic yt-dlp version updating via YtDlpEngineManager
  │
  └── NativeExtractorProvider (Priority 80, Native Kotlin fallback)
        ├── YouTubeExtractor      (Innertube API & oEmbed metadata)
        ├── TikTokExtractor       (Direct stream extraction)
        ├── InstagramExtractor    (Reels & Post embed stream parser)
        ├── RedditExtractor       (v.redd.it DASH streams & audio pairing)
        ├── TwitterExtractor      (X syndication high-bitrate variants)
        ├── FacebookExtractor     (HD & SD video endpoints)
        └── GenericExtractor      (Direct links & OpenGraph video tags)
```

### Capabilities System (`ExtractionCapability`)
Each provider reports supported capabilities per platform:
- `DIRECT_STREAMS`: Direct single-file HTTP media streams.
- `ADAPTIVE_STREAMS`: Separate video and audio elementary streams.
- `AUDIO_ONLY`: Dedicated audio stream tracks.
- `HDR_METADATA`: Extraction of color primaries, transfer functions, and dynamic range.
- `SESSION_AUTH`: Authenticated requests and cookies.
- `JS_DECIPHER`: JavaScript runtime to solve client signature ciphers.

The `UniversalExtractorRegistry` coordinates URL detection and invokes providers in priority order with graceful fallback.

### Dual-Strategy Download Execution
- **Direct HTTP Chunked Streams**: 64KB buffer streaming via OkHttp with HTTP `Range: bytes=X-` resume support and `.part` buffers.
- **Social & Adaptive Streams**: Ingested via `YoutubeDL.execute(...)` with format selection (`-f {id}+bestaudio/best`), MP4 remuxing (`--merge-output-format mp4`), and live regex throughput/progress parsing.
- **Lossless Muxing**: `MediaStreamMuxer` (native Android `MediaExtractor` + `MediaMuxer`) for DASH/adaptive streams when downloaded via native engine.
- **Hardware Media Verification**: `AndroidMediaVerifier` (`MediaMetadataRetriever`) validating audio/video tracks before completion.
- **Speed & ETA**: Real-time throughput metrics emitted every 500ms.
- **Cancellation**: Full coroutine cancellation and `destroyProcessById` cleanup.

---

## 3. Database Schema

Managed via **Room 2.6.1**:
- `downloads` table:
  - `id`: Auto-generated Long primary key
  - `url`: String (Indexed)
  - `platform`: String
  - `title`, `author`, `thumbnailUrl`: Metadata
  - `downloadPath`, `outputPath`: Local storage paths
  - `resolution`, `fps`, `codec`, `fileSizeBytes`: Technical specs
  - `downloadStatus`: `PENDING`, `FETCHING_METADATA`, `DOWNLOADING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED` (Indexed)
  - `encodingStatus`: `NOT_STARTED`, `ANALYZING`, `ENCODING`, `COMPLETED`, `FAILED`, `SKIPPED`
  - `createdAt`, `completedAt`: Timestamps (Indexed)
- `video_metadata` table:
  - Linked to `downloads.id` via foreign key with cascading delete.
  - Stores future FFprobe / MediaExtractor analysis results (bit depth, color primaries, transfer characteristics, matrix, HDR type, audio channels).

---

## 4. Hardware Acceleration Target Profile
- **Target Device**: OnePlus Ace 5 / OnePlus 13R
- **SoC**: Qualcomm Snapdragon 8 Gen 3 (SM8650-AB)
- **GPU**: Adreno 750
- **Hardware HEVC**: `c2.qti.hevc.encoder` (Main10 profile)
- **Target OS**: Android 15 (API 35+) with Scoped Storage and Foreground Service type `dataSync`.
