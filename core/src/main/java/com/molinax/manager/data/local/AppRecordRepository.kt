package com.molinax.manager.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Repository abstracting AppDatabase data access from UI and ViewModels.
 */
class AppRecordRepository(private val dao: AppRecordDao) {

    val allRecords: Flow<List<AppRecord>> = dao.getAllRecords()

    fun getRecordsByCategory(category: String): Flow<List<AppRecord>> =
        dao.getRecordsByCategory(category)

    suspend fun getRecordById(id: Long): AppRecord? = dao.getRecordById(id)

    suspend fun insert(record: AppRecord): Long = dao.insert(record)

    suspend fun update(record: AppRecord) = dao.update(record)

    suspend fun delete(record: AppRecord) = dao.delete(record)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()
}
