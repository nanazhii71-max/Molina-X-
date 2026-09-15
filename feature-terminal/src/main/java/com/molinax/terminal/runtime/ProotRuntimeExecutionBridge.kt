package com.molinax.terminal.runtime

import android.content.Context
import com.molinax.common.ExecutionResult
import com.molinax.common.ProotLaunchException
import com.molinax.common.RootfsNotProvisionedException
import com.molinax.common.RuntimeExecutionBridge
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

    override suspend fun execute(
        command: String,
        args: List<String>,
        asRoot: Boolean,
        workingDirectory: String,
        env: Map<String, String>,
        timeoutSeconds: Long,
    ): ExecutionResult = withContext(Dispatchers.IO) {
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

        val processBuilder = ProcessBuilder(listOf(prootBinary.absolutePath) + prootArgs)
            .directory(context.filesDir)

        val processEnv = processBuilder.environment()
        processEnv["PROOT_TMP_DIR"] = tmpDir.absolutePath
        processEnv["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        processEnv["HOME"] = "/root"
        processEnv["TERM"] = "xterm-256color"
        processEnv.putAll(env)

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
}
