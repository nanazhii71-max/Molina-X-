#!/usr/bin/env bash
# Build binary proot statis (link ke libtalloc.a + libandroid-shmem.a) — sumber: github.com/termux/proot tag v5.1.107.92
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ABI="${1:?Usage: build-proot.sh <arm64-v8a|armeabi-v7a>}"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh" "${ABI}"

PROOT_TAG="v5.1.107.92"
BUILD_DIR="${MOLINAX_ROOT}/build-tmp/proot-${ABI}"
rm -rf "${BUILD_DIR}"

echo "== Clone termux/proot tag ${PROOT_TAG}"
git clone --branch "${PROOT_TAG}" --depth 1 https://github.com/termux/proot.git "${BUILD_DIR}"
cd "${BUILD_DIR}"

echo "== Verifikasi commit yang benar-benar ter-checkout (integritas via git, content-addressed)"
git rev-parse HEAD
git describe --tags

# ARG_MAX wajib manual (gap nyata di Bionic, bukan soal git)
# PENTING: pakai environment variable export (bukan command-line make var) — konsisten dgn pola
# yang sudah terbukti benar (CC via common.sh, CFLAGS via build-libandroid-shmem.sh). Command-line
# make var sebelumnya bikin -I. dari GNUmakefile (CPPFLAGS += ... -I. -I$(VPATH)) tidak ter-append
# dgn aman, shg cli/cli.h gagal ketemu (Run CI #3, run 34908028898).
export CPPFLAGS="-I${STAGING_DIR}/include"
export CFLAGS="-DARG_MAX=131072"
export LDFLAGS="-L${STAGING_DIR}/lib"

if [ "${ABI}" = "arm64-v8a" ]; then
    # NDK r28: default 16KB page size utk arm64-v8a — proot sudah aman (pakai sysconf(_SC_PAGE_SIZE)
    # runtime, bukan macro compile-time), flag ini preventif utk ELF alignment binary itu sendiri.
    export LDFLAGS="${LDFLAGS} -Wl,-z,max-page-size=16384"
fi

echo "== Build proot (PROOT_WITH_LIBANDROID_SHMEM=true)"
echo "== CPPFLAGS=${CPPFLAGS}"
echo "== CFLAGS=${CFLAGS}"
echo "== LDFLAGS=${LDFLAGS}"
make -C src PROOT_WITH_LIBANDROID_SHMEM=true

echo "== Verifikasi arsitektur binary hasil build"
"${OBJDUMP}" -f src/proot

echo "== Salin ke dist"
cp src/proot "${DIST_DIR}/proot"

echo "== SELESAI proot ABI=${ABI}"
ls -la "${DIST_DIR}/proot"
file "${DIST_DIR}/proot" 2>/dev/null || true
