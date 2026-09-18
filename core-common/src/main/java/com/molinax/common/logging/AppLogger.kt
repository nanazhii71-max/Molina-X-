package com.molinax.common.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Logger internal untuk debugging di device fisik tanpa PC/adb/root. Menulis ke logcat
 * (android.util.Log, tetap kelihatan kalau adb ada) DAN ke file app-private
 * (filesDir/debug-logs/molinax-debug.log) yang bisa dibaca+disalin dari dalam app --
 * perluasan dari pola crash logger (MolinaXApplication) untuk event non-fatal
 * (lifecycle, state transition, retry, dst), dipakai lintas modul (Phase 3 Terminal,
 * Phase 4+ Player/Editor/Utilities) makanya ditaruh di core-common.
 */
object AppLogger {
    private const val TAG_PREFIX = "MolinaX"
    private const val MAX_FILE_BYTES = 1_000_000L
    private val lock = ReentrantLock()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var logFile: File? = null

    fun init(context: Context) {
        if (logFile != null) return
        val dir = File(context.filesDir, "debug-logs").apply { mkdirs() }
        logFile = File(dir, "molinax-debug.log")
    }

    fun d(tag: String, message: String) = write("D", tag, message, null)
    fun i(tag: String, message: String) = write("I", tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = write("W", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = write("E", tag, message, throwable)

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val fullTag = "$TAG_PREFIX.$tag"
        when (level) {
            "D" -> Log.d(fullTag, message, throwable)
            "I" -> Log.i(fullTag, message, throwable)
            "W" -> Log.w(fullTag, message, throwable)
            "E" -> Log.e(fullTag, message, throwable)
        }
        val file = logFile ?: return
        lock.withLock {
            try {
                if (file.length() > MAX_FILE_BYTES) {
                    val lines = file.readLines()
                    file.writeText(lines.drop(lines.size / 2).joinToString("\n"))
                }
                val ts = timestampFormat.format(Date())
                val trace = throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""
                file.appendText("$ts [$level] $fullTag: $message$trace\n")
            } catch (writeFailure: Throwable) {
                Log.e("$TAG_PREFIX.AppLogger", "Gagal menulis debug log", writeFailure)
            }
        }
    }

    fun readAll(): String {
        val file = logFile ?: return "(logger belum di-init)"
        return if (file.exists()) file.readText() else "(belum ada log)"
    }

    fun clear() {
        logFile?.delete()
    }
}
