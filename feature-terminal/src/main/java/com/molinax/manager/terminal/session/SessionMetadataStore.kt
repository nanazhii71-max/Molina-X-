package com.molinax.terminal.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DataStore Preferences singleton delegate untuk metadata sesi Terminal.
 * Nama file preferences: "molinax_terminal_sessions" (disimpan di
 * app-private storage, konsisten dengan storage model Terminal §8.3 blueprint —
 * tidak butuh permission tambahan apa pun).
 */
private val Context.terminalSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "molinax_terminal_sessions"
)

/**
 * Penyimpanan persisten untuk daftar [SessionMetadata] Terminal, di-backing oleh
 * DataStore Preferences dengan satu key berisi list yang di-encode JSON.
 *
 * Semua operasi mutasi (add/remove/rename/reorder) membaca DAN menulis di dalam
 * SATU blok edit{} yang sama — bukan baca di luar lalu tulis terpisah. Ini wajib
 * untuk menghindari race TOCTOU: kalau dua coroutine memanggil mutasi nyaris
 * bersamaan (mis. tap "+" dua kali cepat, atau auto-create sesi bertabrakan
 * dengan restore session), baca-di-luar-tulis-terpisah akan membuat salah satu
 * menimpa perubahan yang lain secara diam-diam.
 */
class SessionMetadataStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private companion object {
        val KEY_SESSIONS = stringPreferencesKey("session_metadata_list")
    }

    /** Flow reaktif berisi daftar sesi terkini, terurut sesuai [SessionMetadata.order]. */
    val sessionsFlow: Flow<List<SessionMetadata>> = context.terminalSessionDataStore.data.map { prefs ->
        decodeSessions(prefs[KEY_SESSIONS]).sortedBy { it.order }
    }

    /** Ambil snapshot daftar sesi saat ini (sekali baca, bukan Flow). */
    suspend fun getSessions(): List<SessionMetadata> = sessionsFlow.first()

    private fun decodeSessions(raw: String?): List<SessionMetadata> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching { json.decodeFromString<List<SessionMetadata>>(raw) }.getOrDefault(emptyList())
    }

    /**
     * Tambah sesi baru di akhir daftar. [order] baru dihitung otomatis
     * (maksimum order yang ada + 1, atau 0 kalau daftar masih kosong).
     * Baca + tulis atomik di dalam satu edit{} — lihat KDoc kelas.
     */
    suspend fun addSession(handle: String, displayName: String, workingDirectory: String): SessionMetadata {
        lateinit var newSession: SessionMetadata
        context.terminalSessionDataStore.edit { prefs ->
            val current = decodeSessions(prefs[KEY_SESSIONS])
            val nextOrder = (current.maxOfOrNull { it.order } ?: -1) + 1
            newSession = SessionMetadata(
                handle = handle,
                displayName = displayName,
                workingDirectory = workingDirectory,
                order = nextOrder
            )
            prefs[KEY_SESSIONS] = json.encodeToString(current + newSession)
        }
        return newSession
    }

    /** Hapus satu sesi berdasarkan [handle]. Tidak error kalau handle tidak ditemukan (no-op). */
    suspend fun removeSession(handle: String) = removeSessions(setOf(handle))

    /**
     * Hapus beberapa sesi sekaligus berdasarkan [handles], atomik. Handle yang
     * tidak ditemukan diabaikan (no-op untuk itu saja, bukan error). Ini jalur
     * hapus EKSPLISIT — dipisah dari [reorderSessions], yang sekarang murni
     * mengurutkan dan tidak pernah menghapus apa pun secara implisit.
     */
    suspend fun removeSessions(handles: Set<String>) {
        context.terminalSessionDataStore.edit { prefs ->
            val current = decodeSessions(prefs[KEY_SESSIONS])
            val remaining = current.filterNot { it.handle in handles }
            prefs[KEY_SESSIONS] = json.encodeToString(remaining)
        }
    }

    /** Ganti nama tampilan sesi, atomik. Tidak error kalau handle tidak ditemukan (no-op). */
    suspend fun renameSession(handle: String, newDisplayName: String) {
        context.terminalSessionDataStore.edit { prefs ->
            val current = decodeSessions(prefs[KEY_SESSIONS])
            val updated = current.map { if (it.handle == handle) it.copy(displayName = newDisplayName) else it }
            prefs[KEY_SESSIONS] = json.encodeToString(updated)
        }
    }

    /**
     * Susun ulang urutan tab sesuai [orderedHandles] (mis. hasil drag-and-drop
     * tab oleh pengguna). TIDAK menghapus apa pun.
     *
     * [orderedHandles] WAJIB berisi set handle yang PERSIS SAMA dengan sesi yang
     * ada saat ini (tidak kurang, tidak lebih, tidak duplikat) — divalidasi di
     * DALAM blok edit{} yang sama supaya validasi dan tulis atomik terhadap race
     * dengan [addSession]/[removeSessions] lain yang mungkin berjalan hampir
     * bersamaan. Kalau tidak cocok, lempar [IllegalArgumentException] — pemanggil
     * wajib retry dengan snapshot terbaru, bukan diam-diam kehilangan data.
     *
     * Untuk menghapus sesi, panggil [removeSessions] secara eksplisit terpisah,
     * lalu reorder dengan snapshot yang sudah sinkron.
     */
    suspend fun reorderSessions(orderedHandles: List<String>) {
        context.terminalSessionDataStore.edit { prefs ->
            val current = decodeSessions(prefs[KEY_SESSIONS])
            val currentHandles = current.map { it.handle }.toSet()
            val requestedHandles = orderedHandles.toSet()

            require(orderedHandles.size == requestedHandles.size) {
                "orderedHandles mengandung duplikat handle: $orderedHandles"
            }
            require(requestedHandles == currentHandles) {
                val missing = currentHandles - requestedHandles
                val unknown = requestedHandles - currentHandles
                "reorderSessions() menerima set handle yang tidak cocok dengan sesi yang ada. " +
                    "Hilang dari orderedHandles: $missing. Tidak dikenal (bukan sesi yang ada): $unknown. " +
                    "reorderSessions() tidak menghapus sesi — panggil removeSessions() eksplisit dulu " +
                    "kalau memang bermaksud menghapus, lalu reorder dengan snapshot terbaru."
            }

            val currentByHandle = current.associateBy { it.handle }
            val reordered = orderedHandles.mapIndexed { index, handle ->
                currentByHandle.getValue(handle).copy(order = index)
            }
            prefs[KEY_SESSIONS] = json.encodeToString(reordered)
        }
    }
}
