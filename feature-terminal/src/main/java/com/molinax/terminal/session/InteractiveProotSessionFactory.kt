package com.molinax.terminal.session

import android.content.Context
import com.molinax.terminal.runtime.GuestNetworkConfig
import com.molinax.terminal.runtime.PinnedDebianRootfs
import com.molinax.terminal.runtime.RootfsProvisioner
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

class RootfsNotReadyException(message: String) : IllegalStateException(message)
class ProotBinaryMissingException(message: String) : IllegalStateException(message)

class InteractiveProotSessionFactory(
    private val context: Context,
    private val rootfsProvisioner: RootfsProvisioner = RootfsProvisioner(context.filesDir),
) {

    private val prootBinary: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    fun create(
        cwd: String,
        client: TerminalSessionClient,
        transcriptRows: Int = 2000,
    ): TerminalSession {
        val spec = PinnedDebianRootfs.forCurrentDevice()
        val rootfsDir = rootfsProvisioner.rootfsDirFor(spec)
        if (!rootfsProvisioner.isProvisioned(spec)) {
            throw RootfsNotReadyException(
                "Rootfs ${spec.abiDir} belum diprovisi di ${rootfsDir.absolutePath} - " +
                    "panggil RootfsProvisioner.provision() dulu sebelum membuka Terminal."
            )
        }
        if (!prootBinary.exists() || !prootBinary.canExecute()) {
            throw ProotBinaryMissingException(
                "Binary proot tidak ditemukan/tidak executable di ${prootBinary.absolutePath}."
            )
        }

        val tmpDir = File(context.filesDir, "tmp").apply { mkdirs() }
        val resolvConf = GuestNetworkConfig.ensureProvisioned(context.filesDir)

        val hostArgs = buildList {
            add("proot")
            add("--kill-on-exit")
            add("--sysvipc")
            add("--ashmem-memfd")
            add("-0")
            add("-r"); add(rootfsDir.absolutePath)
            add("-w"); add(cwd)
            add("-b"); add("/proc")
            add("-b"); add("/sys")
            add("-b"); add("/dev")
            add("-b"); add("${tmpDir.absolutePath}:/tmp")
            add("-b"); add("${resolvConf.absolutePath}:/etc/resolv.conf")
            add("/bin/bash")
            add("--login")
        }

        val env = buildList {
            add("PROOT_TMP_DIR=${tmpDir.absolutePath}")
            add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            add("HOME=/root")
            add("TERM=xterm-256color")
        }

        return TerminalSession(
            prootBinary.absolutePath,
            context.filesDir.absolutePath,
            hostArgs.toTypedArray(),
            env.toTypedArray(),
            transcriptRows,
            client,
        )
    }
}
