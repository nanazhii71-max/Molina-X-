package com.molinax.manager.core

import java.io.File

/**
 * Clean architectural contract for spawning processes inside the MolinaX PREFIX or system shell.
 * Owned by the App Host and implemented by the Terminal subsystem.
 * Utilized by the Package Manager (apt/dpkg), Player Downloader (yt-dlp), and Utilities.
 */
interface RuntimeExecutionBridge {
    val prefixDir: File
    val homeDir: File

    suspend fun execute(
        command: String,
        workingDir: File? = null,
        environment: Map<String, String> = emptyMap(),
        onOutput: (String) -> Unit = {}
    ): ExecutionResult

    fun isExecutableAvailable(executableName: String): Boolean
}

data class ExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
) {
    val isSuccess: Boolean get() = exitCode == 0
}
