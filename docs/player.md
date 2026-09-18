# Player Subsystem Architecture & Media Extension Specifications

## 1. Supported Formats (libmpv & ffmpeg Verified)

The following extensions are officially mapped for `MediaAutoRoute` routing from the File Tools / Editor into the Player:

### Video Formats
- `.mp4`
- `.mkv` (Matroska)
- `.webm`
- `.avi`
- `.mov` (QuickTime)
- `.wmv`
- `.flv`
- `.ts` (MPEG Transport Stream)
- `.m2ts`
- `.m4v`
- `.3gp`

### Audio Formats
- `.mp3`
- `.flac`
- `.wav`
- `.ogg` / `.oga`
- `.opus`
- `.m4a`
- `.aac`
- `.aiff`
- `.alac`
- `.wma`

---

## 2. Playback Architecture
- **Local Media:** Direct file descriptor / SAF URI streaming into `BaseMPVView` / MediaPlayer / ExoPlayer / MPVLib.
- **By Title (YouTube):** Resolved via `ytsearch:<query>` query syntax, passed directly to `ytdl_hook.lua`.
- **Universal Link:** Passed to yt-dlp extractor layer for direct URL resolution and playback.
- **Downloader:** Uses `RuntimeExecutionBridge` to invoke `yt-dlp -o <output_template> <url>` directly into the device's storage.

## 3. Background Playback & Foreground Service
Background playback utilizes `PlayerForegroundService` with `MediaSessionCompat` and `NotificationCompat.MediaStyle`, ensuring uninterrupted audio/video background playback according to Android background execution limits (API 26+).
