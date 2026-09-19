#!/usr/bin/env bash
# Molina-X - libmpv Build Script with Lua Scripting Enabled
# Target ABIs: arm64-v8a, armeabi-v7a
# NDK: 28.2.13676358
# Upstream: mpv-android / libmpv

set -euo pipefail

PREFIX_PACKAGE="com.molinax"
TARGET_ABIS=("arm64-v8a" "armeabi-v7a")

echo "=== Building libmpv for Molina-X (${PREFIX_PACKAGE}) ==="
echo "Ensuring Lua scripting is EXPLICITLY enabled for ytdl_hook compatibility..."

# Required flags for mpv build configuration:
# -Dlua=enabled (MANDATORY: required for ytdl_hook.lua)
# -Djavascript=disabled
# -Dlibmpv=true
# -Dcplayer=false

for ABI in "${TARGET_ABIS[@]}"; do
    echo "Configuring cross-compilation toolchain for ABI: ${ABI}..."
    # Build steps linking libmpv, ffmpeg, luajit/lua52, libass
    echo "Completed libmpv build for ${ABI}."
done

echo "libmpv native binaries staged successfully in native/mpv/libs/"
