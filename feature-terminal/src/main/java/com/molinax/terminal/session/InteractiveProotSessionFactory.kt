package com.molinax.terminal.session

import android.content.Context
import android.os.Looper
import com.molinax.terminal.runtime.GuestNetworkConfig
import com.molinax.terminal.runtime.PinnedDebianRootfs
import com.molinax.terminal.runtime.RootfsProvisioner
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

class RootfsNotReadyException(message: String) : IllegalStateException(message)
class ProotBinaryMissingException(message: String) : IllegalStateException(message)

/**
 * Hasil validasi + penyusunan argumen proot -- murni data, tidak menyentuh API Android apa pun
 * yang butuh Looper (mis. [android.os.Handler]). Aman dibangun di background thread/dispatcher
 * mana pun (mis. [kotlinx.coroutines.Dispatchers.IO]).
 *
 * Sengaja dipisah dari konstruksi [TerminalSession]: konstruktor `TerminalSession` (termux-app
 * v0.118.3, `TerminalSession.java` baris 71) membuat `MainThreadHandler` lewat `new Handler()`
 * (no-arg) di baris 336 -- ini WAJIB dipanggil dari thread yang sudah `Looper.prepare()`, yaitu
 * main thread. Memanggil constructor ini dari `Dispatchers.IO`/`Dispatchers.Default` melempar
 * `RuntimeException: Can't create handler inside thread that has not called Looper.prepare()`.
 */
data class ProotLaunchSpec(
    val prootBinaryPath: String,
    val nativeWorkingDirectory: String,
    val hostArgs: Array<String>,
    val env: Array<String>,
)

class InteractiveProotSessionFactory(
    private val context: Context,
    private val rootfsProvisioner: RootfsProvisioner = RootfsProvisioner(context.filesDir),
) {

    private val prootBinary: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    /**
     * Semua validasi (rootfs, binary proot) dan penyusunan argumen -- I/O-bound, aman dipanggil
     * dari [kotlinx.coroutines.Dispatchers.IO]. TIDAK mengonstruksi [TerminalSession] -- lihat
     * [createSession].
     */
    fun prepareLaunchSpec(cwd: String): ProotLaunchSpec {
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
            add("--link2symlink")
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

        return ProotLaunchSpec(
            prootBinaryPath = prootBinary.absolutePath,
            nativeWorkingDirectory = context.filesDir.absolutePath,
            hostArgs = hostArgs.toTypedArray(),
            env = env.toTypedArray(),
        )
    }

    /**
     * Mengonstruksi [TerminalSession] dari [ProotLaunchSpec] yang sudah disiapkan.
     *
     * WAJIB dipanggil dari thread dengan Looper siap (main thread) -- lihat penjelasan di
     * [ProotLaunchSpec]. Caller bertanggung jawab memastikan ini (mis. berada di luar blok
     * `withContext(Dispatchers.IO)`/`Dispatchers.Default`, bukan di dalamnya).
     */
    fun createSession(
        spec: ProotLaunchSpec,
        client: TerminalSessionClient,
        transcriptRows: Int = 2000,
    ): TerminalSession {
        check(Looper.myLooper() != null) {
            "createSession() dipanggil dari thread tanpa Looper -- harus di main thread " +
                "(konstruktor TerminalSession membuat android.os.Handler secara internal)."
        }
        return TerminalSession(
            spec.prootBinaryPath,
            spec.nativeWorkingDirectory,
            spec.hostArgs,
            spec.env,
            transcriptRows,
            client,
        )
    }
}