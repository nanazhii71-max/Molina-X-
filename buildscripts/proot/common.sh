#!/usr/bin/env bash
# Shared toolchain resolver — dipakai oleh build-libtalloc.sh, build-libandroid-shmem.sh, build-proot.sh
# Sumber konvensi: developer.android.com/ndk/guides/other_build_systems + standalone_toolchain.html
set -euo pipefail

ABI="${1:?Usage: source common.sh <arm64-v8a|armeabi-v7a>}"
API_LEVEL="${API_LEVEL:-26}"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    echo "ERROR: ANDROID_NDK_HOME belum di-set (harus mengarah ke root NDK 28.2.13676358)" >&2
    exit 1
fi

HOST_TAG="linux-x86_64"
TOOLCHAIN_BIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/${HOST_TAG}/bin"

if [ ! -d "${TOOLCHAIN_BIN}" ]; then
    echo "ERROR: toolchain bin tidak ditemukan di ${TOOLCHAIN_BIN}" >&2
    exit 1
fi

case "${ABI}" in
    arm64-v8a)
        CLANG_TRIPLE="aarch64-linux-android"
        ;;
    armeabi-v7a)
        CLANG_TRIPLE="armv7a-linux-androideabi"
        ;;
    *)
        echo "ERROR: ABI tidak dikenal: ${ABI} (harus arm64-v8a atau armeabi-v7a)" >&2
        exit 1
        ;;
esac

export CC="${TOOLCHAIN_BIN}/${CLANG_TRIPLE}${API_LEVEL}-clang"
export CXX="${TOOLCHAIN_BIN}/${CLANG_TRIPLE}${API_LEVEL}-clang++"
export AR="${TOOLCHAIN_BIN}/llvm-ar"
export RANLIB="${TOOLCHAIN_BIN}/llvm-ranlib"
export STRIP="${TOOLCHAIN_BIN}/llvm-strip"
export OBJCOPY="${TOOLCHAIN_BIN}/llvm-objcopy"
export OBJDUMP="${TOOLCHAIN_BIN}/llvm-objdump"

for tool in "$CC" "$CXX" "$AR" "$RANLIB" "$STRIP" "$OBJCOPY" "$OBJDUMP"; do
    if [ ! -x "$tool" ]; then
        echo "ERROR: tool tidak ditemukan/tidak executable: $tool" >&2
        exit 1
    fi
done

MOLINAX_ROOT="${MOLINAX_ROOT:-$(pwd)}"
STAGING_DIR="${MOLINAX_ROOT}/native/proot/staging-${ABI}"
DIST_DIR="${MOLINAX_ROOT}/native/proot/${ABI}"
mkdir -p "${STAGING_DIR}/include" "${STAGING_DIR}/lib" "${DIST_DIR}"

echo "== common.sh OK — ABI=${ABI} API=${API_LEVEL}"
echo "== CC=${CC}"
echo "== STAGING_DIR=${STAGING_DIR}"
