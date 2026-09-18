#!/bin/bash -e

## Dependency versions
# Make sure to keep v_ndk and v_ndk_n in sync, both are listed on the NDK download page

v_sdk=11076708_latest
# NDK dikunci ke r28c (28.2.13676358) — SAMA dengan NDK yang dipakai AGP untuk
# build native app (feature-player/libplayer.so dkk). Ini KEPUTUSAN SENGAJA,
# BUKAN default upstream mpv-android (yang pin r30) — supaya libc++_shared.so
# yang dibundel dari pipeline ini (lihat build-libmpv.yml) taken persis dari
# sysroot NDK yang sama dengan yang AGP pakai untuk libplayer.so, menghindari
# dua sumber libc++_shared.so berbeda major version dalam satu APK.
v_ndk=r28c
v_ndk_n=28.2.13676358
v_sdk_platform=36
v_sdk_build_tools=36.0.0

v_lua=5.2.4
v_unibreak=7.0
v_harfbuzz=14.3.1
v_fribidi=1.0.16
v_freetype=2.14.3
v_mbedtls=3.6.7
v_libxml2=2.15.3
v_fontconfig=2.18.2
v_curl=8.21.0


## Dependency tree

dep_mbedtls=()
dep_dav1d=()
dep_libxml2=()
dep_ffmpeg=(mbedtls dav1d libxml2)
dep_freetype2=()
dep_fontconfig=(libxml2 freetype2)
dep_fribidi=()
dep_harfbuzz=()
dep_unibreak=()
dep_libass=(freetype2 fontconfig fribidi harfbuzz unibreak)
dep_lua=()
dep_libplacebo=()
dep_curl=(mbedtls)
dep_mpv=(ffmpeg libass lua libplacebo)
dep_mpv_android=(mpv)


## for CI workflow

# pinned ffmpeg revision
v_ci_ffmpeg=n9.0

# filename used to uniquely identify a build prefix
ci_tarball="prefix-n${v_ndk}-l${v_lua}-u${v_unibreak}-h${v_harfbuzz}-fr${v_fribidi}-ft${v_freetype}-x${v_libxml2}-fo${v_fontconfig}-m${v_mbedtls}-ff${v_ci_ffmpeg}.tgz"
