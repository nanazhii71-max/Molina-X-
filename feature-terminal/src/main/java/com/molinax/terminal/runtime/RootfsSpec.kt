package com.molinax.terminal.runtime

import android.os.Build

/**
 * Spesifikasi rootfs Debian resmi per ABI, dipin manual (Opsi B — lihat docs/terminal.md).
 *
 * Digest ini adalah digest LAYER OCI (bukan digest manifest), diambil dari image resmi
 * Docker Hub `library/debian` (dibangun debuerreotype, dimaintain Debian Developers).
 * Karena digest = SHA-256 dari isi layer itu sendiri (content-addressed), verifikasi checksum
 * saat download otomatis setara dengan mencocokkan alamat blob yang diminta.
 *
 * TIDAK ADA resolusi tag ("trixie") secara dinamis di runtime. Tag hanya dipakai manusia
 * saat maintenance untuk mengecek apakah ada rilis rootfs baru yang perlu diverifikasi ulang
 * dan di-pin ulang di sini — bukan jalur trust aplikasi.
 *
 * Terakhir diverifikasi manual: 2026-09-15, lewat Docker Registry HTTP API v2
 * (auth.docker.io + registry-1.docker.io), isi tar dan SHA-256 dicocokkan nyata dari device.
 */
data class RootfsSpec(
    val abiDir: String,
    val layerDigestSha256: String,
    val layerSizeBytes: Long,
    val maintenanceReferenceTag: String,
)

object PinnedDebianRootfs {

    const val DOCKER_REPOSITORY = "library/debian"

    /** Cuma referensi untuk manusia saat maintenance (lihat kdoc RootfsSpec). */
    private const val MAINTENANCE_REFERENCE_TAG = "trixie"

    val ARM64_V8A = RootfsSpec(
        abiDir = "arm64-v8a",
        layerDigestSha256 = "7f50a08a25277c02b8dfc99818a21e12083215981e0aa03cb07cb74c9116f205",
        layerSizeBytes = 49_704_853L,
        maintenanceReferenceTag = MAINTENANCE_REFERENCE_TAG,
    )

    val ARMEABI_V7A = RootfsSpec(
        abiDir = "armeabi-v7a",
        layerDigestSha256 = "cdac0eac0749288813a078c4279ee1e58b9f6a38246ae0d43ef25f305013e0fc",
        layerSizeBytes = 45_764_051L,
        maintenanceReferenceTag = MAINTENANCE_REFERENCE_TAG,
    )

    /**
     * Pilih spec berdasarkan ABI primer perangkat. Molina-X cuma target arm64-v8a/armeabi-v7a
     * (lihat blueprint §8.4) — ABI lain wajib gagal eksplisit, bukan fallback diam-diam.
     */
    fun forCurrentDevice(): RootfsSpec {
        val supportedAbi = Build.SUPPORTED_ABIS.firstOrNull { abi ->
            abi == "arm64-v8a" || abi == "armeabi-v7a"
        } ?: throw UnsupportedOperationException(
            "Perangkat ini tidak punya ABI yang didukung Molina-X Terminal " +
                "(arm64-v8a/armeabi-v7a). ABI perangkat: ${Build.SUPPORTED_ABIS.joinToString()}"
        )
        return if (supportedAbi == "arm64-v8a") ARM64_V8A else ARMEABI_V7A
    }
}
