# Licensing & Open Source Attributions

## Molina-X License

Molina-X is licensed under the **GNU General Public License v3.0 (GPLv3)**.
See the [LICENSE](../LICENSE) file for the full license text.

---

## Upstream Components & Copyleft Analysis

Molina-X combines four key subsystems designed for advanced Android developers, sysadmins, and power users:

| Subsystem | Upstream Reference | Upstream License | Copyleft & Integration Status |
|---|---|---|---|
| **Terminal Subsystem** | [Termux](https://github.com/termux/termux-app) | GPLv3 | Native terminal emulator, PTY bridge, bootstrap architecture. Strong copyleft requires downstream distribution under GPLv3. |
| **Player Subsystem** | [libmpv](https://github.com/mpv-player/mpv) / [mpv-android](https://github.com/mpv-android/mpv-android) | GPLv2+ / LGPLv2.1+ (libmpv), MIT (mpv-android app) | The application links and interacts with libmpv (and utilizes Lua scripting for `ytdl_hook`). Because libmpv builds with GPL components enabled, downstream derivative works must be distributed under GPLv3. |
| **Editor Subsystem** | [Sora Editor](https://github.com/Rosemoe/sora-editor) by Rosemoe | LGPL-2.1-or-later | Sora Editor engine provides core text manipulation, TextMate grammar parsing, and Tree-sitter AST integration. The "or-later" clause allows compatible combination with GPLv3 binaries. |
| **Utilities Subsystem** | MolinaX Core Architecture | GPLv3 | Package management (apt/dpkg hooks), media extraction, archive utilities, and file tools interacting via `RuntimeExecutionBridge`. |

---

## Package Name & Runtime Isolation

The application ID and runtime path are strictly isolated to:
- **Package:** `com.molinax`
- **PREFIX:** `/data/data/com.molinax/files/usr`
- **HOME:** `/data/data/com.molinax/files/home`

Molina-X operates in its own sandboxed environment and does not depend on or interfere with third-party application directories.
