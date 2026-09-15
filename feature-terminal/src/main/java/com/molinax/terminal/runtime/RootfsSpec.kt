package com.molinax.terminal.runtime

import android.os.Build

/**
 * Spesifikasi rootfs Debian resmi per ABI, dipin manual.
 *
 * Sumber: tarball gzip resmi dari `debuerreotype/docker-debian-artifacts`
 * (proyek yang membangun image Debian resmi, dipelihara Debian Developers/debuerreotype,
 * dipakai juga sebagai basis publikasi Docker Hub `library/debian`). Diambil lewat HTTPS
 * polos dari raw.githubusercontent.com branch `dist-<abi>`, TANPA Docker Registry API/token
 * — sesuai blueprint §8.1 ("tarball rootfs Debian resmi").
 *
 * Digest SHA-256 di bawah diverifikasi MANUAL dua kali secara independen:
 *  1. Dari isi file `trixie/oci/blobs/rootfs.tar.gz` di repo git resmi debuerreotype
 *     (branch dist-arm64v8 / dist-arm32v7), commit di-checkout & sha256sum langsung.
 *  2. Dicocokkan dengan `trixie/oci/manifest.json` & `index.json` di repo yang sama
 *     (digest+size layer OCI untuk `arm64v8/debian:trixie` / `arm32v7/debian:trixie`).
 * Kedua sumber cocok persis — bukan asumsi tunggal.
 *
 * TIDAK ADA resolusi branch/tag dinamis di runtime. `dist-<abi>` hanya dipakai manusia
 * saat maintenance untuk cek rilis rootfs baru & pin ulang di sini — bukan jalur trust aplikasi.
 *
 * Terakhir diverifikasi manual: 2026-09-15, clone langsung branch dist-arm64v8/dist-arm32v7
 * repo debuerreotype/docker-debian-artifacts, sha256sum dicocokkan nyata.
 */
data class RootfsSpec(
    val abiDir: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val maintenanceBranch: String,
)

object PinnedDebianRootfs {

    private const val REPO_RAW_BASE =
        "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts"

    /** Cuma referensi untuk manusia saat maintenance (lihat kdoc RootfsSpec). */
    private const val SUITE_PATH = "trixie/oci/blobs/rootfs.tar.gz"

    val ARM64_V8A = RootfsSpec(
        abiDir = "arm64-v8a",
        downloadUrl = "$REPO_RAW_BASE/dist-arm64v8/$SUITE_PATH",
        sha256 = "7f50a08a25277c02b8dfc99818a21e12083215981e0aa03cb07cb74c9116f205",
        sizeBytes = 49_704_853L,
        maintenanceBranch = "dist-arm64v8",
    )

    val ARMEABI_V7A = RootfsSpec(
        abiDir = "armeabi-v7a",
        downloadUrl = "$REPO_RAW_BASE/dist-arm32v7/$SUITE_PATH",
        sha256 = "cdac0eac0749288813a078c4279ee1e58b9f6a38246ae0d43ef25f305013e0fc",
        sizeBytes = 45_764_051L,
        maintenanceBranch = "dist-arm32v7",
    )

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
