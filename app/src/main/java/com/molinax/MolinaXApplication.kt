package com.molinax

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MolinaXApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
    }

    /**
     * Crash logger internal -- device fisik tanpa akses PC/adb/root tidak bisa ambil logcat
     * dari luar proses. Solusi: tangkap uncaught exception sendiri, tulis full stack trace ke
     * app-private storage (filesDir, selalu bisa ditulis tanpa permission apa pun), lalu
     * MainActivity menampilkannya di layar saat app dibuka lagi setelah crash -- supaya bisa
     * disalin manual dari layar tanpa PC/adb/root sama sekali.
     */
    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashDir = File(filesDir, "crash-logs").apply { mkdirs() }
                val logFile = File(crashDir, "last-crash.txt")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                logFile.writeText(
                    buildString {
                        appendLine("Molina-X crash report")
                        appendLine("Waktu: $timestamp")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        appendLine(Log.getStackTraceString(throwable))
                    }
                )
            } catch (loggerFailure: Throwable) {
                // Jangan biarkan logger sendiri melempar exception baru saat proses sedang
                // crash -- itu bisa menutupi stack trace asli / bikin proses mati sebelum
                // sempat tertulis.
                Log.e("MolinaXCrashLogger", "Gagal menulis crash log", loggerFailure)
            }
            // Tetap teruskan ke handler default OS (supaya perilaku force-close/dialog sistem
            // tidak berubah) -- logger ini murni tambahan pencatatan, bukan pengganti.
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
