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

## 2. Downloader Engine Design

The extraction architecture uses a modular strategy pattern:
```
VideoExtractor (Interface)
  ├── YouTubeExtractor      (Innertube Android API & oembed)
  ├── TikTokExtractor       (Direct stream extraction without watermark)
  ├── InstagramExtractor    (Reels & Post embed stream parser)
  ├── RedditExtractor       (v.redd.it DASH video and audio metadata)
  ├── TwitterExtractor      (X syndication high-bitrate variants)
  ├── FacebookExtractor     (HD & SD video endpoints)
  └── GenericExtractor      (Direct links & OpenGraph video tags)
```

The `UniversalExtractorRegistry` coordinates URL detection and routes extraction requests to the corresponding implementation with zero overhead.

### Download Execution
- **Chunked Streams**: 64KB buffer streaming via OkHttp.
- **Resume Support**: Utilizes HTTP `Range: bytes=X-` and temporary `.part` file buffers.
- **Speed & ETA**: Rolling time window metric calculations emitted every 500ms.
- **Cancellation**: Full coroutine cancellation support with cleanup.

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
