# Terminal Subsystem Architecture & Runtime Guidelines

## 1. Paths and Namespace
- **Package:** `com.molinax.manager`
- **PREFIX:** `/data/data/com.molinax.manager/files/usr`
- **HOME:** `/data/data/com.molinax.manager/files/home`
- **TMPDIR:** `/data/data/com.molinax.manager/files/usr/tmp`

All bootstrap binaries, dynamic linkers (`/system/bin/linker64`), shared libraries (`$PREFIX/lib`), and executables (`$PREFIX/bin`) are compiled targeting `$PREFIX`.

## 2. Process Execution & Foreground Service
To prevent the Android OS from terminating background shell processes:
- `TerminalService` runs as an explicit **Foreground Service** with a persistent notification (`POST_NOTIFICATIONS`).
- In Android 12+ (API 31+), the Android OS introduces the **Phantom Process Killer** which limits phantom (child) processes up to 32 spawned processes globally per device.
- **Mitigation Strategy:**
  1. MolinaX Manager tracks child PIDs and terminal session states inside persistent session registry.
  2. If a background process is terminated by the OS, `TerminalSession` detects EOF on stdout/stderr and marks the session with the termination exit code.
  3. Reconnection and session recovery allow recreating the PTY and restoring the working directory and environment.

## 3. RuntimeExecutionBridge
The `RuntimeExecutionBridge` is a clean architectural contract between the App Host and the Terminal runtime:
```kotlin
interface RuntimeExecutionBridge {
    suspend fun execute(
        command: String,
        workingDir: File? = null,
        environment: Map<String, String> = emptyMap(),
        onOutput: (String) -> Unit = {}
    ): ExecutionResult
}
```
This is utilized by:
- **Package Manager:** Running `apt update`, `apt install <pkg>`, `dpkg -i <deb>`.
- **Player Subsystem:** Invoking `yt-dlp` to download media and inspect formats.
- **Utilities:** Extracting archives (`tar`, `gzip`, `xz`), computing hashes (`sha256sum`), inspecting media (`ffmpeg`, `ffprobe`).
