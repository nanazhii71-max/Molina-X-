#!/usr/bin/env bash
# Build libtalloc.a untuk satu ABI — sumber: samba.org (resmi), resep cross termux-packages
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ABI="${1:?Usage: build-libtalloc.sh <arm64-v8a|armeabi-v7a>}"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh" "${ABI}"

TALLOC_VERSION="2.4.3"
TALLOC_SHA256="dc46c40b9f46bb34dd97fe41f548b0e8b247b77a918576733c528e83abd854dd"
BUILD_DIR="${MOLINAX_ROOT}/build-tmp/talloc-${ABI}"
rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"

echo "== Download talloc ${TALLOC_VERSION}"
curl -fSL "https://www.samba.org/ftp/talloc/talloc-${TALLOC_VERSION}.tar.gz" -o talloc.tar.gz

echo "== Verifikasi checksum SHA-256"
echo "${TALLOC_SHA256}  talloc.tar.gz" | sha256sum -c -

tar xf talloc.tar.gz
cd "talloc-${TALLOC_VERSION}"

# cross-answers.txt persis dari resep resmi termux-packages (packages/libtalloc/build.sh)
cat > cross-answers.txt << 'ANSEOF'
Checking uname sysname type: "Linux"
Checking uname machine type: "dontcare"
Checking uname release type: "dontcare"
Checking uname version type: "dontcare"
Checking simple C program: OK
building library support: OK
Checking for large file support: OK
Checking for -D_FILE_OFFSET_BITS=64: OK
Checking for WORDS_BIGENDIAN: OK
Checking for C99 vsnprintf: OK
Checking for HAVE_SECURE_MKSTEMP: OK
rpath library support: OK
-Wl,--version-script support: FAIL
Checking correct behavior of strtoll: OK
Checking correct behavior of strptime: OK
Checking for HAVE_IFACE_GETIFADDRS: OK
Checking for HAVE_IFACE_IFCONF: OK
Checking for HAVE_IFACE_IFREQ: OK
Checking getconf LFS_CFLAGS: OK
Checking for large file support without additional flags: OK
Checking for working strptime: OK
Checking for HAVE_SHARED_MMAP: OK
Checking for HAVE_MREMAP: OK
Checking for HAVE_INCOHERENT_MMAP: OK
Checking getconf large file support flags work: OK
ANSEOF

echo "== Configure (cross, waf wrapper)"
./configure --prefix=/usr \
    --disable-rpath \
    --disable-python \
    --cross-compile \
    --cross-answers=cross-answers.txt

echo "== Build"
make

echo "== Rakit static archive (persis pola termux_step_post_make_install resmi)"
cd bin/default
"${AR}" rcu libtalloc.a talloc*.o
cd ../..

echo "== Salin artifact ke staging"
cp bin/default/libtalloc.a "${STAGING_DIR}/lib/"
cp talloc.h "${STAGING_DIR}/include/"

echo "== SELESAI libtalloc ABI=${ABI}"
ls -la "${STAGING_DIR}/lib/libtalloc.a" "${STAGING_DIR}/include/talloc.h"
