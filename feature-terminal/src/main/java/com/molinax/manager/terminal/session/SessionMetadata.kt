package com.molinax.manager.terminal.session

import kotlinx.serialization.Serializable

/**
 * Metadata sesi Terminal yang dipersist lintas restart proses/app.
 *
 * HANYA metadata ini yang dipersist — bukan proses PTY-nya sendiri (tidak bisa
 * diserialize; begitu proses mati, isi shell hilang permanen, sama seperti Termux
 * asli). Saat reconnect, [handle]/[displayName]/[workingDirectory]/[order] dipakai
 * untuk merespawn shell baru per slot; state shell lama tetap hilang, tapi struktur
 * tab/urutan/nama balik seperti semula.
 *
 * @property handle UUID unik per sesi. Nilai ini SAMA dengan [com.termux.terminal.TerminalSession.mHandle]
 *                   yang di-generate saat sesi dibuat — dipakai untuk mengaitkan metadata
 *                   persisten ini dengan instance [com.termux.terminal.TerminalSession] yang berjalan.
 * @property displayName Nama tab yang ditampilkan ke pengguna, bisa di-rename oleh pengguna.
 * @property workingDirectory Direktori kerja awal (di dalam rootfs Debian) tempat shell
 *                             dispawn, dipakai lagi sebagai cwd saat respawn.
 * @property order Urutan tampil tab (0-based), dipakai untuk mengembalikan urutan asli
 *                  saat daftar sesi direstorasi.
 */
@Serializable
data class SessionMetadata(
    val handle: String,
    val displayName: String,
    val workingDirectory: String,
    val order: Int
)
