package com.molinax.manager.terminal

import com.molinax.manager.core.ExecutionResult
import com.molinax.manager.core.RuntimeExecutionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TerminalRuntimeBridge(
    override val prefixDir: File,
    override val homeDir: File
) : RuntimeExecutionBridge {

    override fun isExecutableAvailable(executableName: String): Boolean {
        val inPrefix = File(prefixDir, "bin/$executableName").exists()
        val inSystem = File("/system/bin/$executableName").exists()
        return inPrefix || inSystem
    }

    override suspend fun execute(
        command: String,
        workingDir: File?,
        environment: Map<String, String>,
        onOutput: (String) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val workDir = workingDir?.takeIf { it.exists() && it.isDirectory } ?: homeDir
        if (!workDir.exists()) workDir.mkdirs()

        // Shell choice
        val prefixSh = File(prefixDir, "bin/sh")
        val prefixBash = File(prefixDir, "bin/bash")
        val shellPath = when {
            prefixBash.exists() -> prefixBash.absolutePath
            prefixSh.exists() -> prefixSh.absolutePath
            else -> "/system/bin/sh"
        }

        val pb = ProcessBuilder(shellPath, "-c", command)
        pb.directory(workDir)

        val env = pb.environment()
        env["PREFIX"] = prefixDir.absolutePath
        env["HOME"] = homeDir.absolutePath
        env["TMPDIR"] = File(prefixDir, "tmp").absolutePath
        env["PATH"] = "${File(prefixDir, "bin").absolutePath}:/system/bin:/system/xbin"
        env["LD_LIBRARY_PATH"] = File(prefixDir, "lib").absolutePath
        env["TERM"] = "xterm-256color"
        environment.forEach { (k, v) -> env[k] = v }

        pb.redirectErrorStream(false)

        try {
            val process = pb.start()
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutThread = Thread {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(stdoutBuilder) {
                            stdoutBuilder.append(line).append("\n")
                        }
                        onOutput(line)
                    }
                }
            }

            val stderrThread = Thread {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(stderrBuilder) {
                            stderrBuilder.append(line).append("\n")
                        }
                        onOutput("[ERR] $line")
                    }
                }
            }

            stdoutThread.start()
            stderrThread.start()

            val exitCode = process.waitFor()
            stdoutThread.join()
            stderrThread.join()

            val duration = System.currentTimeMillis() - startTime
            ExecutionResult(
                exitCode = exitCode,
                stdout = stdoutBuilder.toString().trimEnd(),
                stderr = stderrBuilder.toString().trimEnd(),
                durationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ExecutionResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Execution failed",
                durationMs = duration
            )
        }
    }
}
