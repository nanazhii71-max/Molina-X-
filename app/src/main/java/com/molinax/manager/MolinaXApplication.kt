package com.molinax.manager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.molinax.manager.data.AppDatabase
import com.molinax.manager.data.DataRepository
import com.molinax.manager.data.local.AppRecordRepository
import com.molinax.manager.player.PlayerService
import com.molinax.manager.terminal.TerminalService
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class MolinaXApplication : Application() {

    lateinit var prefixDir: File
        private set
    lateinit var homeDir: File
        private set

    @Inject
    lateinit var injectedDatabase: AppDatabase

    @Inject
    lateinit var injectedRepository: AppRecordRepository

    @Inject
    lateinit var injectedDataRepository: DataRepository

    val database: AppDatabase
        get() = if (::injectedDatabase.isInitialized) injectedDatabase else AppDatabase.getDatabase(this)

    val appRecordRepository: AppRecordRepository
        get() = if (::injectedRepository.isInitialized) injectedRepository else AppRecordRepository(database.appRecordDao())

    val dataRepository: DataRepository
        get() = if (::injectedDataRepository.isInitialized) injectedDataRepository else DataRepository(database)

    override fun onCreate() {
        super.onCreate()
        initializePrefixEnvironment()
        createNotificationChannels()
    }

    private fun initializePrefixEnvironment() {
        // According to architecture blueprint:
        // PREFIX = /data/data/com.molinax.manager/files/usr
        // HOME   = /data/data/com.molinax.manager/files/home
        prefixDir = File(filesDir, "usr")
        homeDir = File(filesDir, "home")

        val dirs = listOf(
            prefixDir,
            homeDir,
            File(prefixDir, "bin"),
            File(prefixDir, "lib"),
            File(prefixDir, "etc"),
            File(prefixDir, "tmp"),
            File(prefixDir, "var/lib/proot-distro/installed-rootfs"),
            File(homeDir, "downloads")
        )

        dirs.forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val terminalChannel = NotificationChannel(
                TerminalService.CHANNEL_ID,
                "MolinaX Terminal Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps shell processes active in background"
            }

            val playerChannel = NotificationChannel(
                PlayerService.CHANNEL_ID,
                "MolinaX Media Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground playback notification with controls"
            }

            manager.createNotificationChannel(terminalChannel)
            manager.createNotificationChannel(playerChannel)
        }
    }
}
