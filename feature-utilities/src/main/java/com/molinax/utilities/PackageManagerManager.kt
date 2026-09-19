package com.molinax.utilities

import com.molinax.core.RuntimeExecutionBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PackageItem(
    val name: String,
    val version: String,
    val description: String = "",
    val isInstalled: Boolean = false
)

class PackageManagerManager(
    private val runtimeBridge: RuntimeExecutionBridge
) {
    private val _packages = MutableStateFlow<List<PackageItem>>(emptyList())
    val packages: StateFlow<List<PackageItem>> = _packages.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // Popular core packages available in Termux/MolinaX bootstrap
    val recommendedPackages = listOf(
        "yt-dlp", "ffmpeg", "git", "python", "curl", "wget",
        "vim", "nano", "tar", "gzip", "clang", "nodejs", "proot", "proot-distro",
        "htop", "tree", "openssh", "tmux", "zsh", "fish", "rsync", "jq"
    )

    suspend fun updateDatabase() {
        executeApt("apt update")
    }

    suspend fun installPackage(packageName: String) {
        executeApt("apt install -y $packageName")
    }

    suspend fun removePackage(packageName: String) {
        executeApt("apt remove -y $packageName")
    }

    suspend fun listInstalled(): List<PackageItem> {
        val result = runtimeBridge.execute("dpkg -l")
        val list = mutableListOf<PackageItem>()
        result.stdout.lines().forEach { line ->
            if (line.startsWith("ii")) {
                val parts = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                if (parts.size >= 3) {
                    list.add(
                        PackageItem(
                            name = parts[1],
                            version = parts[2],
                            description = if (parts.size >= 4) parts.drop(3).joinToString(" ") else "",
                            isInstalled = true
                        )
                    )
                }
            }
        }
        return list
    }

    private suspend fun executeApt(command: String) {
        _isBusy.value = true
        appendLog("> $command")
        runtimeBridge.execute(
            command = command,
            onOutput = { line ->
                appendLog(line)
            }
        )
        _isBusy.value = false
    }

    private fun appendLog(line: String) {
        val current = _logs.value.toMutableList()
        current.add(line)
        if (current.size > 500) {
            current.removeAt(0)
        }
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
