# UniDownco Video Download Engine Specification

## Supported Platforms & Extraction Protocols

UniDownco utilizes a prioritized multi-provider strategy (`YtDlpExtractorProvider` at Priority 100 with `NativeExtractorProvider` at Priority 80):

| Platform | Primary Engine | Fallback Strategy | Formats Supported | Audio/Video Merging |
| :--- | :--- | :--- | :--- | :--- |
| **YouTube** | yt-dlp (Innertube + cipher deciphering) | Innertube Android Client API + oEmbed | Up to 4K/60fps (MP4/WebM) | Auto-paired adaptive streams merged via FFmpeg |
| **TikTok** | yt-dlp (Signature bypass) | Web embed & video address metadata | Direct watermark-free MP4 | Combined audio/video |
| **Instagram** | yt-dlp (Embed + session handling) | Reel/post captioned embed endpoints | Direct 1080p MP4 | Combined audio/video |
| **Reddit** | yt-dlp (DASH parser) | JSON API (`.json`) & `v.redd.it` | 1080p, 720p, 480p fallback MP4 | `DASH_AUDIO` paired and merged |
| **X / Twitter** | yt-dlp (GraphQL/syndication) | Syndication API (`cdn.syndication.twimg.com`) | Highest bitrate MP4 variant | Combined audio/video |
| **Facebook** | yt-dlp (GraphQL video) | Page scrapers & OpenGraph video tags | HD (1080p) and SD (720p) MP4 | Combined audio/video |
| **Generic** | Native Extractor | Direct URL extension & OpenGraph meta tags | MP4, WebM, MKV | Standard container |

---

## Download Execution & Routing

UniDownco features a **Dual-Mode Download Engine**:

1. **Direct HTTP Range Streamer (OkHttp)**:
   - Used for direct static MP4/WebM URLs.
   - High throughput with 64KB buffers.
   - Resumable chunked downloads via `Range: bytes=X-` headers and `.part` files.
   - Lossless elementary stream remuxing via `MediaStreamMuxer` (native Android `MediaExtractor` + `MediaMuxer`).

2. **yt-dlp Embedded Execution Engine (`youtubedl-android`)**:
   - Used for social platforms requiring complex headers, DASH/HLS manifests, or dynamic token generation.
   - Executes native `yt-dlp` backed by CPython and FFmpeg native shared libraries.
   - Formats selected via `-f {format_id}+bestaudio/best` and merged automatically (`--merge-output-format mp4`).
   - Monitored with live regex progress and speed parsing (`[download] XX% of YY at ZZ`).
   - Integrated with process destruction on cancellation or pause.

---

## Download Resilience & State Transitions

```
[User Input / Share Intent]
          │
          ▼
   UrlValidator ──(Invalid)──► [Error State]
          │
       (Valid)
          │
          ▼
   UniversalExtractorRegistry (Detect platform)
          │
          ▼
   Provider Priority Chain:
   1. YtDlpExtractorProvider (Priority 100)
   2. NativeExtractorProvider (Priority 80)
          │
       (Success)
          │
          ▼
   Format Selection (Default: Highest quality)
          │
          ▼
   Room: Insert DownloadRecord (Status: PENDING)
          │
          ▼
   DownloadForegroundService.start() (Status: DOWNLOADING)
          │
     [Direct MP4?]
     ├─ Yes ──► OkHttp Range Downloader
     └─ No  ──► YoutubeDL Embedded Runner
          │
   ┌──────┴─────────────────────────────────┐
   │                                        │
   ▼                                        ▼
[Pause] ──► Status: PAUSED             [Cancel] ──► Status: CANCELLED
   │                                        │
   ▼                                        ▼
[Resume] (Range: bytes=X-)              Delete .part file / terminate process
   │
   ▼
[Download Completed]
   ├── Verify byte length > 0
   ├── AndroidMediaVerifier (validate video/audio tracks)
   ├── Room: Mark COMPLETED
   ├── Export to MediaStore: Movies/UniversalHDR/
   └── Stop Foreground Service
```

---

## Android 15 & Scoped Storage Compliance

- Uses app-private external files directory (`context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)`) for in-flight `.part` downloads and raw source cache.
- Completed files are inserted into `MediaStore.Video.Media` with `RELATIVE_PATH = "Movies/UniversalHDR"`.
- Uses `FileProvider` with `FLAG_GRANT_READ_URI_PERMISSION` for secure video playback and file sharing.
- Complies with Android 14/15 Foreground Service dataSync requirements (`FOREGROUND_SERVICE_DATA_SYNC`).
