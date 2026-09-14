#!/usr/bin/env bash
# Build libandroid-shmem (.a + .so) untuk satu ABI — sumber: github.com/termux/libandroid-shmem
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ABI="${1:?Usage: build-libandroid-shmem.sh <arm64-v8a|armeabi-v7a>}"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh" "${ABI}"

SHMEM_VERSION="0.7"
SHMEM_SHA256="1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867"
BUILD_DIR="${MOLINAX_ROOT}/build-tmp/android-shmem-${ABI}"
rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"

echo "== Download libandroid-shmem v${SHMEM_VERSION}"
curl -fSL "https://github.com/termux/libandroid-shmem/archive/refs/tags/v${SHMEM_VERSION}.tar.gz" -o shmem.tar.gz

echo "== Verifikasi checksum SHA-256"
echo "${SHMEM_SHA256}  shmem.tar.gz" | sha256sum -c -

tar xf shmem.tar.gz
cd "libandroid-shmem-${SHMEM_VERSION}"

echo "== Build .a dan .so"
make CC="${CC}" AR="${AR}" libandroid-shmem.a
make CC="${CC}" AR="${AR}" libandroid-shmem.so

echo "== Salin artifact ke staging (proot link ke .a saja, .so disimpan sbg artifact cadangan)"
cp libandroid-shmem.a "${STAGING_DIR}/lib/"
cp libandroid-shmem.so "${DIST_DIR}/"
cp shm.h "${STAGING_DIR}/include/"

echo "== SELESAI libandroid-shmem ABI=${ABI}"
ls -la "${STAGING_DIR}/lib/libandroid-shmem.a" "${DIST_DIR}/libandroid-shmem.so" "${STAGING_DIR}/include/shm.h"
