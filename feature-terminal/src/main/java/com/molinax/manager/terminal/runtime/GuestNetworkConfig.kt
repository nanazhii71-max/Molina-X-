package com.molinax.terminal.runtime

import java.io.File

object GuestNetworkConfig {
    private const val DEFAULT_RESOLV_CONF = "nameserver 8.8.8.8\nnameserver 1.1.1.1\n"

    fun resolvConfFileFor(filesDir: File): File = File(filesDir, "net/resolv.conf")

    fun ensureProvisioned(filesDir: File): File {
        val file = resolvConfFileFor(filesDir)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(DEFAULT_RESOLV_CONF)
        }
        return file
    }
}
