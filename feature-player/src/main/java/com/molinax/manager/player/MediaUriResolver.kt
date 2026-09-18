package com.molinax.manager.player

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream

// Port dari is.xyz.mpv.Utils.findRealPath + MPVActivity.resolveUri/translateContentUri
// (mpv-android, commit 751d532) -- dipisah jadi object sendiri (upstream naruhnya sebagai
// method private di dalam MPVActivity.kt/Utils.kt) supaya bisa dipakai dari PlayerHost.kt
// (Compose) tanpa perlu Activity, konsisten dengan pola Molina-X lainnya (util murni,
// tanpa dependency ke lifecycle Activity/View).
//
// mpv (lewat ffmpeg) tidak punya protocol handler native untuk skema "content://" (VERIFIED
// grep langsung ke app/src/main/jni/*.cpp upstream -- nol hasil untuk "content"/"android_content"),
// jadi Uri hasil SAF (ACTION_OPEN_DOCUMENT) harus diterjemahkan dulu ke path filesystem asli
// SEBELUM diteruskan ke MPVLib.command(loadfile). readlink pada /proc/self/fd/<fd> dari file
// descriptor yang dibuka lewat ContentResolver berhasil untuk provider berbasis file nyata
// (Files app bawaan Android, media provider, dst) -- fallback ke string content:// URI apa
// adanya kalau readlink gagal (provider virtual/network), sama seperti fallback upstream.
internal object MediaUriResolver {
    private const val TAG = "MolinaXPlayer"

    /** Resolusi [uri] hasil SAF jadi string path yang aman diteruskan ke MPVLib "loadfile". */
    fun resolveToPlayablePath(context: Context, uri: Uri): String {
        if (uri.scheme != "content") return uri.toString()
        return translateContentUri(context.contentResolver, uri)
    }

    private fun translateContentUri(resolver: ContentResolver, uri: Uri): String {
        try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val path = findRealPath(pfd.fd)
                if (path != null) {
                    Log.i(TAG, "MediaUriResolver: path asli ditemukan untuk $uri -> $path")
                    return path
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaUriResolver: gagal buka file descriptor untuk $uri", e)
        }
        Log.w(TAG, "MediaUriResolver: readlink gagal, fallback ke content URI apa adanya: $uri")
        return uri.toString()
    }

    private fun findRealPath(fd: Int): String? {
        var ins: FileInputStream? = null
        try {
            val path = File("/proc/self/fd/$fd").canonicalPath
            if (!path.startsWith("/proc") && File(path).canRead()) {
                // Double check that we can read it (sama seperti upstream)
                ins = FileInputStream(path)
                ins.read()
                return path
            }
        } catch (e: Exception) {
            // Sengaja ditelan -- ini best-effort probe (identik upstream), fallback ke
            // content:// URI apa adanya sudah disediakan pemanggil.
        } finally {
            ins?.close()
        }
        return null
    }
}
