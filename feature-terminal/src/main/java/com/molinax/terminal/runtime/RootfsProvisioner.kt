package com.molinax.terminal.runtime

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.HashingSink
import okio.buffer
import okio.sink
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject
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
 * Fetch + verifikasi + extract rootfs Debian resmi lewat Docker Registry HTTP API v2,
 * dengan digest layer yang sudah dipin manual (lihat [PinnedDebianRootfs] & docs/terminal.md).
 *
 * Alur (semua terverifikasi manual sebelum kode ini ditulis — lihat handoff Phase 3):
 *  1. Ambil bearer token anonim dari auth.docker.io (pull publik, tidak perlu kredensial)
 *  2. GET blob layer langsung by digest dari registry-1.docker.io (skip resolusi manifest —
 *     karena digest sudah dipin, tidak ada langkah manifest-list/manifest-per-platform di runtime)
 *  3. Verifikasi SHA-256 dari isi yang benar-benar diterima (streaming hash, bukan percaya header)
 *  4. Extract tar.gz ke ROOTFS dengan preservasi permission + symlink, dengan guard path traversal
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
        private const val AUTH_URL =
            "https://auth.docker.io/token?service=registry.docker.io&scope=repository:${PinnedDebianRootfs.DOCKER_REPOSITORY}:pull"
        private const val BLOB_URL_TEMPLATE =
            "https://registry-1.docker.io/v2/${PinnedDebianRootfs.DOCKER_REPOSITORY}/blobs/sha256:%s"
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
            Log.i(TAG, "Mengambil token anonim Docker Registry untuk ${PinnedDebianRootfs.DOCKER_REPOSITORY}")
            val token = fetchAnonymousToken()

            Log.i(TAG, "Mengunduh layer rootfs ${spec.abiDir} (sha256:${spec.layerDigestSha256})")
            downloadAndVerifyBlob(token, spec, downloadFile, onProgress)

            Log.i(TAG, "Checksum cocok, mengekstrak ke ${rootfsDirFor(spec)}")
            extractRootfs(downloadFile, rootfsDirFor(spec))

            markerFileFor(spec).writeText(
                "digest=sha256:${spec.layerDigestSha256}\n" +
                    "sizeBytes=${spec.layerSizeBytes}\n" +
                    "maintenanceReferenceTag=${spec.maintenanceReferenceTag}\n"
            )
            Log.i(TAG, "Rootfs ${spec.abiDir} selesai diprovisi.")
        } finally {
            downloadFile.delete()
        }
    }

    private fun fetchAnonymousToken(): String {
        val request = Request.Builder().url(AUTH_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "Gagal ambil token Docker Registry untuk ${PinnedDebianRootfs.DOCKER_REPOSITORY}: " +
                        "HTTP ${response.code}"
                )
            }
            val body = response.body?.string()
                ?: throw IOException("Response token Docker Registry kosong (HTTP ${response.code})")
            return JSONObject(body).getString("token")
        }
    }

    private fun downloadAndVerifyBlob(
        token: String,
        spec: RootfsSpec,
        destination: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        val url = BLOB_URL_TEMPLATE.format(spec.layerDigestSha256)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        // OkHttp secara default mengikuti redirect (registry selalu 307 ke CDN signed URL,
        // sudah dibuktikan manual) dan melepas header Authorization saat host redirect berbeda
        // dari host asal — persis perilaku aman yang kita butuhkan di sini, tanpa kode tambahan.
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "Gagal unduh blob rootfs ${spec.abiDir} (sha256:${spec.layerDigestSha256}): " +
                        "HTTP ${response.code}"
                )
            }
            val body = response.body
                ?: throw IOException("Body blob rootfs ${spec.abiDir} kosong (HTTP ${response.code})")

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
                        onProgress(totalRead, spec.layerSizeBytes)
                    }
                }
            }

            val actualDigest = hashingSink.hash.hex()
            if (!actualDigest.equals(spec.layerDigestSha256, ignoreCase = true)) {
                destination.delete()
                throw RootfsIntegrityException(
                    "Checksum rootfs ${spec.abiDir} TIDAK COCOK. Diharapkan " +
                        "sha256:${spec.layerDigestSha256}, didapat sha256:$actualDigest " +
                        "(${totalRead} byte diterima). Download dibatalkan — kemungkinan korup " +
                        "di jalur jaringan atau digest yang dipin sudah tidak valid, JANGAN dipakai."
                )
            }
            if (totalRead != spec.layerSizeBytes) {
                destination.delete()
                throw RootfsIntegrityException(
                    "Ukuran rootfs ${spec.abiDir} tidak cocok. Diharapkan ${spec.layerSizeBytes} byte, " +
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
            // Pass pertama: buat semua direktori & file biasa, kumpulkan symlink/hardlink
            // untuk pass kedua (link bisa mereferensikan entry yang belum ter-extract kalau
            // urutan di tar tidak menjamin dependency-first).
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
                    // Hardlink: target harus sudah ada dari pass pertama.
                    val linkTargetResolved = resolveEntryPathOrThrow(targetRootCanonical, linkEntry.linkName)
                    if (!Files.exists(linkTargetResolved)) {
                        throw UnsupportedTarEntryException(
                            "Hardlink '${linkEntry.name}' -> '${linkEntry.linkName}' tidak bisa " +
                                "diselesaikan: target belum ada saat extract. Perlu penanganan " +
                                "topological-sort, bukan diabaikan."
                        )
                    }
                    Files.deleteIfExists(resolvedPath)
                    Files.createLink(resolvedPath, linkTargetResolved)
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
