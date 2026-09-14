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

# PATCH SUMBER: extension/ashmem_memfd/ashmem_memfd.c pakai strcmp() (baris 42) dan memset()
# (baris 179) tapi TIDAK PERNAH #include <string.h> — VERIFIED langsung dari source (grep
# "#include" file ini, tidak ada string.h). Ini genuine gap di source upstream utk path Bionic
# (file di-guard #if defined(__ANDROID__) || defined(__BIONIC__)), kemungkinan di host asli
# (glibc) string.h ketarik transitif dari header lain, di Bionic tidak. clang NDK r28 treat
# implicit function declaration sbg error keras (Run CI #4, run 34908349432).
echo "== Patch: tambah #include <string.h> ke ashmem_memfd.c (gap Bionic, VERIFIED dari source)"
sed -i '/#include <unistd.h>/a #include <string.h> /* strcmp, memset -- gap source upstream utk Bionic, VERIFIED tidak di-include sama sekali */' \
    src/extension/ashmem_memfd/ashmem_memfd.c

echo "== Verifikasi patch berhasil masuk"
head -n 10 src/extension/ashmem_memfd/ashmem_memfd.c

# ARG_MAX wajib manual (gap nyata di Bionic, bukan soal git)
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
echo "== DIAGNOSTIK: baris GNUmakefile terkait LDLIBS/LDFLAGS/LIBANDROID_SHMEM/liblog/libandroid" 
grep -n -i "LDLIBS\|LDFLAGS\|LIBANDROID_SHMEM\|liblog\|libandroid" src/GNUmakefile || true 
echo "== DIAGNOSTIK: aturan link target proot (baris di sekitar \$(PROOT):)" 
grep -n -A5 "^\$(PROOT):" src/GNUmakefile || true
make -C src PROOT_WITH_LIBANDROID_SHMEM=true

echo "== Verifikasi arsitektur binary hasil build"
"${OBJDUMP}" -f src/proot

echo "== Salin ke dist"
cp src/proot "${DIST_DIR}/proot"

echo "== SELESAI proot ABI=${ABI}"
ls -la "${DIST_DIR}/proot"
file "${DIST_DIR}/proot" 2>/dev/null || true
