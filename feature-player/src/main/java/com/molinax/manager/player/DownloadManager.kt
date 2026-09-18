package com.molinax.manager.player

import com.molinax.manager.core.RuntimeExecutionBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

class DownloadManager(
    private val runtimeBridge: RuntimeExecutionBridge,
    private val downloadDir: File
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    init {
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
    }

    suspend fun startDownload(url: String, customTitle: String? = null): String {
        val taskId = UUID.randomUUID().toString()
        val title = customTitle ?: url.takeLast(30)
        val task = DownloadTask(
            id = taskId,
            url = url,
            title = title,
            progressPercent = 0f,
            downloadSpeed = "Starting...",
            eta = "--:--"
        )
        val current = _tasks.value.toMutableList()
        current.add(0, task)
        _tasks.value = current

        val outputTemplate = File(downloadDir, "%(title)s.%(ext)s").absolutePath
        val command = "yt-dlp -o \"$outputTemplate\" --newline \"$url\""

        val result = runtimeBridge.execute(
            command = command,
            workingDir = downloadDir,
            onOutput = { line ->
                parseYtDlpOutput(taskId, line)
            }
        )

        updateTask(taskId) { old ->
            if (result.isSuccess) {
                old.copy(
                    progressPercent = 100f,
                    downloadSpeed = "Done",
                    eta = "00:00",
                    isCompleted = true
                )
            } else {
                old.copy(
                    isCompleted = false,
                    error = result.stderr.ifBlank { "Download failed" }
                )
            }
        }

        return taskId
    }

    private fun parseYtDlpOutput(taskId: String, line: String) {
        // Sample: [download]  45.2% of 120.00MiB at  4.50MiB/s ETA 00:15
        if (line.contains("[download]") && line.contains("%")) {
            try {
                val percentRegex = """(\d+(\.\d+)?)%""".toRegex()
                val speedRegex = """at\s+([0-9.]+[A-Za-z/]+)""".toRegex()
                val etaRegex = """ETA\s+(\d+:\d+)""".toRegex()

                val percentMatch = percentRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                val speedMatch = speedRegex.find(line)?.groupValues?.get(1) ?: ""
                val etaMatch = etaRegex.find(line)?.groupValues?.get(1) ?: ""

                updateTask(taskId) { old ->
                    old.copy(
                        progressPercent = percentMatch,
                        downloadSpeed = speedMatch,
                        eta = etaMatch
                    )
                }
            } catch (e: Exception) {
                // Ignore parse variance
            }
        }
    }

    private fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        val updated = _tasks.value.map {
            if (it.id == taskId) transform(it) else it
        }
        _tasks.value = updated
    }
}
