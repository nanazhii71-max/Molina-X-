package com.molinax.core

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    // Officially verified extensions based on docs/player.md (libmpv & ffmpeg formats)
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "wmv", "flv", "ts", "m2ts", "m4v", "3gp"
    )

    val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "ogg", "oga", "opus", "m4a", "aac", "aiff", "alac", "wma"
    )

    val CODE_EXTENSIONS = setOf(
        "kt", "java", "py", "sh", "bash", "c", "cpp", "h", "hpp", "json",
        "xml", "html", "css", "js", "ts", "md", "txt", "gradle", "properties", "yaml", "yml"
    )

    fun isMediaFile(file: File): Boolean {
        val ext = file.extension.lowercase(Locale.ROOT)
        return ext in VIDEO_EXTENSIONS || ext in AUDIO_EXTENSIONS
    }

    fun isVideoFile(file: File): Boolean {
        return file.extension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS
    }

    fun isAudioFile(file: File): Boolean {
        return file.extension.lowercase(Locale.ROOT) in AUDIO_EXTENSIONS
    }

    fun isCodeOrTextFile(file: File): Boolean {
        val ext = file.extension.lowercase(Locale.ROOT)
        return ext in CODE_EXTENSIONS || file.length() < 5_000_000 // Treat readable files as text
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups.coerceIn(0, units.size - 1)])
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun computeHash(file: File, algorithm: String = "SHA-256"): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
