package com.molinax.manager.terminal.runtime

import android.content.Context
import com.molinax.manager.common.ExecutionResult
import com.molinax.manager.common.ProotLaunchException
import com.molinax.manager.common.RootfsNotProvisionedException
import com.molinax.manager.common.RuntimeExecutionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

class ProotRuntimeExecutionBridge(
    private val context: Context,
    private val rootfsProvisioner: RootfsProvisioner = RootfsProvisioner(context.filesDir),
) : RuntimeExecutionBridge {

    private val prootBinary: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    /** Hasil resolve satu "invocation" proot - dipakai bersama oleh [execute] dan [buildDirectExecWrapper]
     *  supaya argumen proot punya SATU sumber kebenaran, tidak diduplikasi/divergen. */
    private data class ProotInvocation(
        val prootBinary: File,
        val args: List<String>,
        val env: Map<String, String>,
    )

    private fun resolveProotInvocation(
        command: String,
        args: List<String>,
        asRoot: Boolean,
        workingDirectory: String,
        env: Map<String, String>,
    ): ProotInvocation {
        val spec = PinnedDebianRootfs.forCurrentDevice()
        val rootfsDir = rootfsProvisioner.rootfsDirFor(spec)
        if (!rootfsProvisioner.isProvisioned(spec)) {
            throw RootfsNotProvisionedException(
                "Rootfs ${spec.abiDir} belum diprovisi di ${rootfsDir.absolutePath} - " +
                    "panggil RootfsProvisioner.provision() dulu sebelum execute()."
            )
        }
        if (!prootBinary.exists() || !prootBinary.canExecute()) {
            throw ProotLaunchException(
                "Binary proot tidak ditemukan/tidak executable di ${prootBinary.absolutePath}."
            )
        }

        val tmpDir = File(context.filesDir, "tmp").apply { mkdirs() }
        val resolvConf = GuestNetworkConfig.ensureProvisioned(context.filesDir)

        val prootArgs = buildList {
            add("--kill-on-exit")
            add("--link2symlink")
            add("--sysvipc")
            add("--ashmem-memfd")
            if (asRoot) add("-0")
            add("-r"); add(rootfsDir.absolutePath)
            add("-w"); add(workingDirectory)
            add("-b"); add("/proc")
            add("-b"); add("/sys")
            add("-b"); add("/dev")
            add("-b"); add("${tmpDir.absolutePath}:/tmp")
            add("-b"); add("${resolvConf.absolutePath}:/etc/resolv.conf")
            add(command)
            addAll(args)
        }

        val fullEnv = buildMap {
            put("PROOT_TMP_DIR", tmpDir.absolutePath)
            put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            put("HOME", "/root")
            put("TERM", "xterm-256color")
            putAll(env)
        }

        return ProotInvocation(prootBinary, prootArgs, fullEnv)
    }

    override suspend fun execute(
        command: String,
        args: List<String>,
        asRoot: Boolean,
        workingDirectory: String,
        env: Map<String, String>,
        timeoutSeconds: Long,
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val invocation = resolveProotInvocation(command, args, asRoot, workingDirectory, env)

        val processBuilder = ProcessBuilder(listOf(invocation.prootBinary.absolutePath) + invocation.args)
            .directory(context.filesDir)

        val processEnv = processBuilder.environment()
        processEnv.putAll(invocation.env)

        val process = try {
            processBuilder.start()
        } catch (e: IOException) {
            throw ProotLaunchException("Gagal start proses proot: ${e.message}", e)
        }

        val stdoutDeferred = async { process.inputStream.bufferedReader().readText() }
        val stderrDeferred = async { process.errorStream.bufferedReader().readText() }

        val exited = if (timeoutSeconds > 0) {
            withTimeoutOrNull(timeoutSeconds * 1000) {
                process.waitFor()
                true
            } ?: run {
                process.destroyForcibly()
                false
            }
        } else {
            process.waitFor()
            true
        }

        val stdout = stdoutDeferred.await()
        val stderr = stderrDeferred.await()

        if (!exited) {
            throw ProotLaunchException(
                "Proses '$command' timeout setelah ${timeoutSeconds}s, dipaksa dihentikan. " +
                    "stdout sejauh ini: ${stdout.take(500)}"
            )
        }

        ExecutionResult(
            exitCode = process.exitValue(),
            stdout = stdout,
            stderr = stderr,
        )
    }

    override suspend fun buildDirectExecWrapper(
        command: String,
        args: List<String>,
        asRoot: Boolean,
        workingDirectory: String,
        env: Map<String, String>,
    ): File = withContext(Dispatchers.IO) {
        val invocation = resolveProotInvocation(command, args, asRoot, workingDirectory, env)

        val wrapperName = command.substringAfterLast('/').replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val wrapperDir = File(context.filesDir, "exec-wrappers").apply { mkdirs() }
        val wrapperFile = File(wrapperDir, "$wrapperName.sh")

        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# AUTO-GENERATED oleh ProotRuntimeExecutionBridge.buildDirectExecWrapper() -")
            appendLine("# JANGAN diedit manual, akan ditimpa ulang setiap dipanggil.")
            for ((key, value) in invocation.env) {
                appendLine("export ${key}='${value.replace("'", "'\\''")}'")
            }
            val execParts = mutableListOf(shellQuote(invocation.prootBinary.absolutePath))
            invocation.args.forEach { execParts.add(shellQuote(it)) }
            append("exec ")
            append(execParts.joinToString(" "))
            append(" \"\$@\"")
            appendLine()
        }

        wrapperFile.writeText(script)
        if (!wrapperFile.setExecutable(/* executable = */ true, /* ownerOnly = */ false)) {
            throw ProotLaunchException(
                "Gagal set executable bit pada wrapper ${wrapperFile.absolutePath}."
            )
        }
        wrapperFile
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
