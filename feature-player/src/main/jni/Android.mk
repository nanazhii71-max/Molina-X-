LOCAL_PATH := $(call my-dir)

# PREFIX32/PREFIX64 diteruskan lewat externalNativeBuild.ndkBuild.arguments
# di feature-player/build.gradle.kts (setara PREFIX32/PREFIX64 di
# buildscripts/mpv/scripts/mpv-android.sh upstream) — masing-masing
# menunjuk ke hasil download artifact mpv-prefix-<abi> (lib/ + include/
# penuh, bukan cuma .so runtime) dari job build-libmpv CI.
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
PREFIX := $(PREFIX32)
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
PREFIX := $(PREFIX64)
endif

# Hanya 3 modul PREBUILT_SHARED_LIBRARY yang genuinely dilink langsung oleh
# libplayer (LOCAL_SHARED_LIBRARIES di bawah) — BEDA dari Android.mk upstream
# yang mendeklarasikan 8 modul (termasuk libavformat/libavfilter/libavdevice/
# libavutil/libswresample/libpostproc yang cuma dependency transitif runtime,
# bukan dilink langsung). Dependency transitif runtime itu sudah dibundel
# terpisah ke jniLibs lewat step CI "Tempatkan libmpv.so + dependency FFmpeg"
# (build-verify.yml) — TIDAK didobelkan di sini supaya ndk-build tidak
# menyalin ulang file yang sama ke libs/<abi>/.

include $(CLEAR_VARS)
LOCAL_MODULE := libavcodec
LOCAL_SRC_FILES := $(PREFIX)/lib/$(LOCAL_MODULE).so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libswscale
LOCAL_SRC_FILES := $(PREFIX)/lib/$(LOCAL_MODULE).so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libmpv
LOCAL_SRC_FILES := $(PREFIX)/lib/libmpv.so
# Satu direktori include bersama untuk seluruh unified build FFmpeg+mpv
# (libavcodec/jni.h, libswscale/swscale.h, mpv/client.h semua di bawah
# $(PREFIX)/include yang sama) — cukup di-export sekali dari modul ini,
# ndk-build meneruskannya ke libplayer lewat LOCAL_SHARED_LIBRARIES.
# VERIFIED dari Android.mk upstream mpv-android (commit 751d532): pola
# yang sama persis dipakai di sana (hanya libavdevice & libmpv yang
# LOCAL_EXPORT_C_INCLUDES, bukan tiap modul).
LOCAL_EXPORT_C_INCLUDES := $(PREFIX)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := libplayer
LOCAL_CFLAGS    := -Werror
LOCAL_CPPFLAGS  += -std=c++11
LOCAL_SRC_FILES := \
	main.cpp \
	render.cpp \
	log.cpp \
	jni_utils.cpp \
	property.cpp \
	event.cpp \
	thumbnail.cpp
LOCAL_LDLIBS    := -llog -latomic
LOCAL_SHARED_LIBRARIES := swscale avcodec mpv

include $(BUILD_SHARED_LIBRARY)
