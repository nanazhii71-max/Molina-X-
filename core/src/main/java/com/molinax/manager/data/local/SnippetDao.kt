package com.molinax.manager.data.local

import androidx.room.Dao
import androidx.room.Query
import com.molinax.manager.data.BaseDao
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Snippet entities.
 */
@Dao
interface SnippetDao : BaseDao<Snippet> {

    @Query("SELECT * FROM snippets ORDER BY lastUsedTimestamp DESC")
    fun getAllSnippets(): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets WHERE category = :category ORDER BY lastUsedTimestamp DESC")
    fun getSnippetsByCategory(category: String): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets WHERE language = :language ORDER BY lastUsedTimestamp DESC")
    fun getSnippetsByLanguage(language: String): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteSnippets(): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun getSnippetById(id: Long): Snippet?

    @Query("UPDATE snippets SET lastUsedTimestamp = :timestamp WHERE id = :id")
    suspend fun markSnippetUsed(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE snippets SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM snippets")
    suspend fun clearAll()
}
