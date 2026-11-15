# UniDownco Video Download Engine Specification

## Supported Platforms & Extraction Protocols

| Platform | Extraction Strategy | Formats Supported | Audio Merging |
| :--- | :--- | :--- | :--- |
| **YouTube** | Innertube Android Client API + oembed | 1080p, 720p, 480p, 360p (MP4/WebM) | Separate adaptive tracks supported |
| **TikTok** | Web embed & video address metadata | Direct MP4 (Watermark-free) | Combined audio/video |
| **Instagram** | Reel/post captioned embed endpoints | Direct 1080p MP4 | Combined audio/video |
| **Reddit** | JSON API (`.json`) & `v.redd.it` | 1080p, 720p, 480p fallback MP4 | `DASH_AUDIO_128.mp4` paired |
| **X / Twitter** | Syndication API (`cdn.syndication.twimg.com`) | Highest bitrate MP4 variant | Combined audio/video |
| **Facebook** | Page scrapers & OpenGraph video tags | HD (1080p) and SD (720p) MP4 | Combined audio/video |
| **Generic** | Direct URL extension & OpenGraph meta tags | MP4, WebM, MKV | Standard container |

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
   VideoExtractor.extract(url) ──(Failure)──► [Retry / Error]
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
   ┌──────┴─────────────────────────────────┐
   │                                        │
   ▼                                        ▼
[Pause] ──► Status: PAUSED             [Cancel] ──► Status: CANCELLED
   │                                        │
   ▼                                        ▼
[Resume] (Range: bytes=X-)              Delete .part file
   │
   ▼
[Download Completed]
   ├── Verify byte length > 0
   ├── Rename .part -> .mp4
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
