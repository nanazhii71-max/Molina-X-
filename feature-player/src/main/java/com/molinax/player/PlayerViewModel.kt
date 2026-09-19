package com.molinax.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.molinax.core.MediaInputPayload
import com.molinax.core.RuntimeExecutionBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class PlayerViewModel(
    application: Application,
    private val runtimeBridge: RuntimeExecutionBridge
) : AndroidViewModel(application) {

    val playerHost: PlayerHost = PlayerHost(application, viewModelScope)
    val mpvEngine = playerHost.mpvEngine
    val downloadDir: File = File(application.getExternalFilesDir(null) ?: application.filesDir, "downloads")
    val downloadManager: DownloadManager = DownloadManager(runtimeBridge, downloadDir)

    val playbackState = playerHost.playbackState
    val playlist = playerHost.playlist
    val downloadTasks = downloadManager.tasks

    private val _isYtDlpInstalled = MutableStateFlow(false)
    val isYtDlpInstalled: StateFlow<Boolean> = _isYtDlpInstalled.asStateFlow()

    init {
        checkYtDlpAvailability()
    }

    fun checkYtDlpAvailability() {
        viewModelScope.launch {
            val available = runtimeBridge.isExecutableAvailable("yt-dlp")
            _isYtDlpInstalled.value = available
        }
    }

    fun playLocalFile(file: File) {
        val item = PlaylistItem(
            id = UUID.randomUUID().toString(),
            title = file.nameWithoutExtension,
            source = file.absolutePath,
            type = MediaInputType.LOCAL,
            localFile = file
        )
        playerHost.addToPlaylist(item)
    }

    fun playByTitle(title: String) {
        val query = "ytsearch:$title"
        val item = PlaylistItem(
            id = UUID.randomUUID().toString(),
            title = title,
            source = query,
            type = MediaInputType.BY_TITLE
        )
        playerHost.addToPlaylist(item)
    }

    fun playUniversalLink(url: String) {
        val item = PlaylistItem(
            id = UUID.randomUUID().toString(),
            title = url.takeLast(25),
            source = url,
            type = MediaInputType.UNIVERSAL_LINK
        )
        playerHost.addToPlaylist(item)
    }

    fun handleExternalPayload(payload: MediaInputPayload) {
        when (payload) {
            is MediaInputPayload.LocalFile -> playLocalFile(payload.file)
            is MediaInputPayload.TitleQuery -> playByTitle(payload.title)
            is MediaInputPayload.UniversalUrl -> playUniversalLink(payload.url)
        }
    }

    fun startDownload(url: String, customTitle: String? = null) {
        viewModelScope.launch {
            downloadManager.startDownload(url, customTitle)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerHost.release()
    }
}
