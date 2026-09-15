package com.molinax.terminal.runtime

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.HashingSink
import okio.buffer
import okio.sink
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Rootfs gagal diverifikasi (checksum/ukuran tidak cocok) — bukan sekadar IOException jaringan biasa. */
class RootfsIntegrityException(message: String) : IOException(message)

/** Entry tar dengan tipe yang tidak didukung ditemukan (mis. device node, hardlink tak terselesaikan). */
class UnsupportedTarEntryException(message: String) : IOException(message)

/**
 * Fetch + verifikasi + extract tarball rootfs Debian resmi lewat HTTPS polos
 * (raw.githubusercontent.com/debuerreotype/docker-debian-artifacts) — TANPA Docker Registry
 * API/token. Lihat [RootfsSpec] untuk asal-usul & verifikasi digest.
 *
 * Alur:
 *  1. GET tarball gzip langsung dari [RootfsSpec.downloadUrl] (HTTPS polos, tanpa auth)
 *  2. Verifikasi SHA-256 dari isi yang benar-benar diterima (streaming hash, bukan percaya header)
 *  3. Extract tar.gz ke ROOTFS dengan preservasi permission + symlink, dengan guard path traversal
 *
 * @param filesDir harus context.filesDir (app-private storage, lihat blueprint §8.3)
 */
class RootfsProvisioner(
    private val filesDir: File,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build(),
) {

    companion object {
        private const val TAG = "RootfsProvisioner"
    }

    /** Direktori ROOTFS final sesuai blueprint §8.1: /data/data/com.molinax/files/rootfs/debian-<arch>/ */
    fun rootfsDirFor(spec: RootfsSpec): File = File(filesDir, "rootfs/debian-${spec.abiDir}")

    private fun markerFileFor(spec: RootfsSpec): File =
        File(rootfsDirFor(spec), ".molinax-rootfs-complete")

    fun isProvisioned(spec: RootfsSpec): Boolean = markerFileFor(spec).exists()

    /**
     * Jalankan provisioning penuh. Melempar exception eksplisit di setiap kegagalan —
     * tidak ada asumsi sukses tanpa bukti (marker file cuma ditulis di akhir, setelah semua
     * verifikasi lolos).
     *
     * Sebagai efek samping, memastikan `<filesDir>/tmp/` ada — direktori ini WAJIB ada sebelum
     * proot pertama kali dijalankan karena `_PATH_TMP` di-hardcode ke path itu saat build
     * libandroid-shmem (lihat handoff Phase 3 §5). Provisioning rootfs selalu terjadi sebelum
     * TerminalSession pertama, jadi ini titik yang tepat untuk menjaminnya.
     */
    @Throws(IOException::class)
    fun provision(
        spec: RootfsSpec = PinnedDebianRootfs.forCurrentDevice(),
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        val tmpRoot = File(filesDir, "tmp").apply { mkdirs() }

        if (isProvisioned(spec)) {
            Log.i(TAG, "Rootfs ${spec.abiDir} sudah terprovisi di ${rootfsDirFor(spec)}, skip.")
            return
        }

        val downloadStagingDir = File(tmpRoot, "rootfs-download").apply { mkdirs() }
        val downloadFile = File(downloadStagingDir, "debian-${spec.abiDir}.tar.gz")

        try {
            Log.i(TAG, "Mengunduh tarball rootfs resmi ${spec.abiDir} dari ${spec.downloadUrl}")
            downloadAndVerify(spec, downloadFile, onProgress)

            Log.i(TAG, "Checksum cocok, mengekstrak ke ${rootfsDirFor(spec)}")
            extractRootfs(downloadFile, rootfsDirFor(spec))

            markerFileFor(spec).writeText(
                "sha256=${spec.sha256}\n" +
                    "sizeBytes=${spec.sizeBytes}\n" +
                    "sourceUrl=${spec.downloadUrl}\n"
            )
            Log.i(TAG, "Rootfs ${spec.abiDir} selesai diprovisi.")
        } finally {
            downloadFile.delete()
        }
    }

    private fun downloadAndVerify(
        spec: RootfsSpec,
        destination: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        val request = Request.Builder().url(spec.downloadUrl).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "Gagal unduh tarball rootfs ${spec.abiDir} dari ${spec.downloadUrl}: " +
                        "HTTP ${response.code}"
                )
            }
            val body = response.body
                ?: throw IOException("Body tarball rootfs ${spec.abiDir} kosong (HTTP ${response.code})")

            val hashingSink = HashingSink.sha256(destination.sink())
            var totalRead = 0L

            hashingSink.buffer().use { sink ->
                body.source().use { source ->
                    val chunkSize = 64L * 1024
                    while (true) {
                        val read = source.read(sink.buffer, chunkSize)
                        if (read == -1L) break
                        sink.emit()
                        totalRead += read
                        onProgress(totalRead, spec.sizeBytes)
                    }
                }
            }

            val actualDigest = hashingSink.hash.hex()
            if (!actualDigest.equals(spec.sha256, ignoreCase = true)) {
                destination.delete()
                throw RootfsIntegrityException(
                    "Checksum rootfs ${spec.abiDir} TIDAK COCOK. Diharapkan sha256:${spec.sha256}, " +
                        "didapat sha256:$actualDigest (${totalRead} byte diterima). Download " +
                        "dibatalkan — kemungkinan korup di jalur jaringan atau tarball upstream " +
                        "berubah, JANGAN dipakai."
                )
            }
            if (totalRead != spec.sizeBytes) {
                destination.delete()
                throw RootfsIntegrityException(
                    "Ukuran rootfs ${spec.abiDir} tidak cocok. Diharapkan ${spec.sizeBytes} byte, " +
                        "diterima $totalRead byte."
                )
            }
        }
    }

    private fun extractRootfs(tarGzFile: File, targetDir: File) {
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()
        val targetRootCanonical = targetDir.canonicalFile.toPath()

        TarArchiveInputStream(GZIPInputStream(FileInputStream(tarGzFile))).use { tarStream ->
            data class DeferredLink(val entry: TarArchiveEntry, val targetPath: Path)
            val deferredLinks = mutableListOf<DeferredLink>()

            var entry = tarStream.nextEntry
            while (entry != null) {
                val resolvedPath = resolveEntryPathOrThrow(targetRootCanonical, entry.name)

                when {
                    entry.isDirectory -> {
                        Files.createDirectories(resolvedPath)
                    }
                    entry.isSymbolicLink || entry.isLink -> {
                        Files.createDirectories(resolvedPath.parent)
                        deferredLinks += DeferredLink(entry, resolvedPath)
                    }
                    entry.isFile -> {
                        Files.createDirectories(resolvedPath.parent)
                        Files.newOutputStream(resolvedPath).use { out ->
                            tarStream.copyTo(out)
                        }
                        applyPosixPermissions(resolvedPath, entry.mode)
                    }
                    else -> throw UnsupportedTarEntryException(
                        "Tipe entry tar tidak didukung untuk '${entry.name}' " +
                            "(mode=${entry.mode.toString(8)}). Rootfs ini mengandung tipe file " +
                            "di luar regular/directory/symlink/hardlink yang belum ditangani — " +
                            "gap ini harus diselesaikan, bukan diabaikan."
                    )
                }
                entry = tarStream.nextEntry
            }

            for ((linkEntry, resolvedPath) in deferredLinks) {
                if (linkEntry.isSymbolicLink) {
                    Files.deleteIfExists(resolvedPath)
                    Files.createSymbolicLink(resolvedPath, java.nio.file.Paths.get(linkEntry.linkName))
                } else {
                    val linkTargetResolved = resolveEntryPathOrThrow(targetRootCanonical, linkEntry.linkName)
                    if (!Files.exists(linkTargetResolved)) {
                        throw UnsupportedTarEntryException(
                            "Hardlink '${linkEntry.name}' -> '${linkEntry.linkName}' tidak bisa " +
                                "diselesaikan: target belum ada saat extract. Perlu penanganan " +
                                "topological-sort, bukan diabaikan."
                        )
                    }
                    // Android (SELinux, domain untrusted_app) menolak syscall link() ke
                    // app_data_file -- "avc: denied { link }" -- bukan bug ekstraksi kita,
                    // bukan soal urutan tar, dan TIDAK bisa dibuka lewat permission Manifest
                    // apa pun (VERIFIED: kasus identik github.com/dotnet/runtime#126297,
                    // AVC denial sama persis di /data/user/0 di semua ABI Android).
                    // Hardlink diselesaikan sebagai copy byte-for-byte + permission entry asli
                    // dari tar -- isi & mode identik dengan file asal, cukup untuk kebutuhan
                    // dpkg/apt/eksekusi biner di dalam rootfs (tidak butuh inode sharing nyata).
                    Files.deleteIfExists(resolvedPath)
                    Files.copy(
                        linkTargetResolved,
                        resolvedPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    )
                    applyPosixPermissions(resolvedPath, linkEntry.mode)
                }
            }
        }
    }

    /** Guard path traversal (zip-slip): pastikan hasil resolve tetap di dalam targetRoot. */
    private fun resolveEntryPathOrThrow(targetRootCanonical: Path, entryName: String): Path {
        val candidate = targetRootCanonical.resolve(entryName).normalize()
        if (!candidate.startsWith(targetRootCanonical)) {
            throw SecurityException(
                "Entry tar '$entryName' mencoba keluar dari direktori rootfs target " +
                    "(path traversal) — ekstraksi dihentikan."
            )
        }
        return candidate
    }

    private fun applyPosixPermissions(path: Path, mode: Int) {
        val perms = mutableSetOf<PosixFilePermission>()
        if (mode and 0b100_000_000 != 0) perms.add(PosixFilePermission.OWNER_READ)
        if (mode and 0b010_000_000 != 0) perms.add(PosixFilePermission.OWNER_WRITE)
        if (mode and 0b001_000_000 != 0) perms.add(PosixFilePermission.OWNER_EXECUTE)
        if (mode and 0b000_100_000 != 0) perms.add(PosixFilePermission.GROUP_READ)
        if (mode and 0b000_010_000 != 0) perms.add(PosixFilePermission.GROUP_WRITE)
        if (mode and 0b000_001_000 != 0) perms.add(PosixFilePermission.GROUP_EXECUTE)
        if (mode and 0b000_000_100 != 0) perms.add(PosixFilePermission.OTHERS_READ)
        if (mode and 0b000_000_010 != 0) perms.add(PosixFilePermission.OTHERS_WRITE)
        if (mode and 0b000_000_001 != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE)
        Files.setPosixFilePermissions(path, perms)
    }
}
