package com.molinax.manager.data.local

import androidx.room.Dao
import androidx.room.Query
import com.molinax.manager.data.BaseDao
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local application records, extending generic BaseDao.
 */
@Dao
interface AppRecordDao : BaseDao<AppRecord> {
    @Query("SELECT * FROM app_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<AppRecord>>

    @Query("SELECT * FROM app_records WHERE category = :category ORDER BY timestamp DESC")
    fun getRecordsByCategory(category: String): Flow<List<AppRecord>>

    @Query("SELECT * FROM app_records WHERE id = :id")
    suspend fun getRecordById(id: Long): AppRecord?

    @Query("DELETE FROM app_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM app_records")
    suspend fun clearAll()
}
