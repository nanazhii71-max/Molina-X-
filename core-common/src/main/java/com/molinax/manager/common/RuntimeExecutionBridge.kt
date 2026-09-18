package com.molinax.manager.common

import java.io.File

interface RuntimeExecutionBridge {
    suspend fun execute(
        command: String,
        args: List<String> = emptyList(),
        asRoot: Boolean = false,
        workingDirectory: String = "/root",
        env: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = 0,
    ): ExecutionResult

    /**
     * Menghasilkan sebuah shell script executable di app-private storage yang, ketika
     * di-exec langsung oleh proses lain (mis. libmpv native, BUKAN lewat [execute] Kotlin
     * ini), membungkus [command] lewat proot ke dalam Debian rootfs dan meneruskan argumen
     * apa pun yang di-pass ke script itu ("$@") ke [command] di dalam rootfs.
     *
     * Use case: `ytdl_hook` (script Lua built-in libmpv) meng-exec `ytdl_path` langsung
     * secara native (fork/exec dari proses mpv sendiri, BUKAN lewat Kotlin) - binary di
     * dalam rootfs (glibc, linker-nya expect root filesystem Debian) tidak bisa di-exec
     * apa adanya oleh proses yang tidak berjalan di bawah proot. Wrapper ini membuat proot
     * jadi lapisan pembungkus exec-nya, sehingga path absolut ke wrapper inilah yang aman
     * dipakai sebagai `ytdl_path`.
     *
     * Wrapper SELALU ditulis ulang (overwrite) tiap dipanggil, supaya tidak pernah stale
     * kalau path rootfs/proot/tmp berubah (mis. setelah reinstall) - biaya generate ulang
     * kecil (satu file teks), tidak ada cache invalidation terpisah yang bisa salah.
     *
     * @throws RootfsNotProvisionedException kalau rootfs untuk ABI device belum diprovisi
     * @throws ProotLaunchException kalau binary proot tidak ditemukan/tidak executable
     */
    suspend fun buildDirectExecWrapper(
        command: String,
        args: List<String> = emptyList(),
        asRoot: Boolean = false,
        workingDirectory: String = "/root",
        env: Map<String, String> = emptyMap(),
    ): File
}

data class ExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean get() = exitCode == 0
}

class RootfsNotProvisionedException(message: String) : IllegalStateException(message)
class ProotLaunchException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)
