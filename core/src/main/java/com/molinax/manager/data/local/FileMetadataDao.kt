package com.molinax.manager.data.local

import androidx.room.Dao
import androidx.room.Query
import com.molinax.manager.data.BaseDao
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for FileMetadata.
 */
@Dao
interface FileMetadataDao : BaseDao<FileMetadata> {

    @Query("SELECT * FROM file_metadata ORDER BY lastAccessedTimestamp DESC")
    fun getAllFileMetadata(): Flow<List<FileMetadata>>

    @Query("SELECT * FROM file_metadata WHERE module = :module ORDER BY lastAccessedTimestamp DESC")
    fun getByModule(module: String): Flow<List<FileMetadata>>

    @Query("SELECT * FROM file_metadata WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavorites(): Flow<List<FileMetadata>>

    @Query("SELECT * FROM file_metadata WHERE path = :path")
    suspend fun getByPath(path: String): FileMetadata?

    @Query("DELETE FROM file_metadata WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("UPDATE file_metadata SET isFavorite = :isFavorite WHERE path = :path")
    suspend fun updateFavoriteStatus(path: String, isFavorite: Boolean)

    @Query("UPDATE file_metadata SET lastAccessedTimestamp = :timestamp WHERE path = :path")
    suspend fun updateLastAccessed(path: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM file_metadata")
    suspend fun clearAll()
}
