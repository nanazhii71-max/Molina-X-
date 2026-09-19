package com.molinax.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class TerminalSession(
    val id: String,
    val title: String,
    val prefixDir: File,
    val homeDir: File,
    val initialDir: File? = null,
    val initialCommand: String? = null,
    private val scope: CoroutineScope
) {
    private val _lines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

    private val _isRunning = MutableStateFlow(true)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    private var process: Process? = null
    private var outputStream: OutputStream? = null

    init {
        startProcess()
    }

    private fun startProcess() {
        scope.launch(Dispatchers.IO) {
            try {
                val workingDir = initialDir?.takeIf { it.exists() && it.isDirectory } ?: homeDir
                if (!workingDir.exists()) {
                    workingDir.mkdirs()
                }

                // Determine shell executable
                val prefixSh = File(prefixDir, "bin/sh")
                val prefixBash = File(prefixDir, "bin/bash")
                val shellPath = when {
                    prefixBash.exists() -> prefixBash.absolutePath
                    prefixSh.exists() -> prefixSh.absolutePath
                    else -> "/system/bin/sh"
                }

                val pb = ProcessBuilder(shellPath)
                pb.directory(workingDir)

                val env = pb.environment()
                env["PREFIX"] = prefixDir.absolutePath
                env["HOME"] = homeDir.absolutePath
                env["TMPDIR"] = File(prefixDir, "tmp").absolutePath
                env["PATH"] = "${File(prefixDir, "bin").absolutePath}:/system/bin:/system/xbin"
                env["LD_LIBRARY_PATH"] = File(prefixDir, "lib").absolutePath
                env["TERM"] = "xterm-256color"
                env["SHELL"] = shellPath
                env["USER"] = "molinax"

                val proc = pb.start()
                process = proc
                outputStream = proc.outputStream

                appendLine("\u001b[1;36mMolinaX Terminal Session [${title}]\u001b[0m")
                appendLine("\u001b[32mPREFIX: ${prefixDir.absolutePath}\u001b[0m")
                appendLine("\u001b[32mHOME:   ${homeDir.absolutePath}\u001b[0m")
                appendLine("\u001b[33mShell:  ${shellPath}\u001b[0m\n")

                if (!initialCommand.isNullOrBlank()) {
                    writeCommand(initialCommand)
                }

                // Stream stdout & stderr
                launch(Dispatchers.IO) { readStream(proc.inputStream) }
                launch(Dispatchers.IO) { readStream(proc.errorStream) }

                val code = proc.waitFor()
                _exitCode.value = code
                _isRunning.value = false
                appendLine("\n\u001b[1;31m[Process exited with code $code]\u001b[0m")
            } catch (e: Exception) {
                appendLine("\u001b[1;31mFailed to start shell: ${e.message}\u001b[0m")
                _isRunning.value = false
                _exitCode.value = -1
            }
        }
    }

    private fun readStream(stream: InputStream) {
        val buffer = ByteArray(4096)
        val sb = StringBuilder()
        try {
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                val str = String(buffer, 0, read)
                sb.append(str)

                while (sb.contains("\n")) {
                    val idx = sb.indexOf("\n")
                    val line = sb.substring(0, idx)
                    sb.delete(0, idx + 1)
                    appendLine(line)
                }
            }
            if (sb.isNotEmpty()) {
                appendLine(sb.toString())
            }
        } catch (e: Exception) {
            // Stream closed
        }
    }

    private fun appendLine(raw: String) {
        val parsed = AnsiParser.parseLine(raw)
        val current = _lines.value
        val updated = if (current.size > 2000) {
            current.drop(current.size - 1900) + parsed
        } else {
            current + parsed
        }
        _lines.value = updated
    }

    fun write(input: String) {
        scope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(input.toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                appendLine("\u001b[31m[Write error: ${e.message}]\u001b[0m")
            }
        }
    }

    fun writeCommand(command: String) {
        write(command + "\n")
    }

    fun sendCtrlC() {
        write("\u0003")
    }

    fun sendCtrlD() {
        write("\u0004")
    }

    fun sendCtrlZ() {
        write("\u001a")
    }

    fun clearScreen() {
        _lines.value = emptyList()
    }

    fun destroy() {
        try {
            process?.destroy()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
