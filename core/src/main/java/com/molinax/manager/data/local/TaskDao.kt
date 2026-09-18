package com.molinax.manager.data.local

import androidx.room.Dao
import androidx.room.Query
import com.molinax.manager.data.BaseDao
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Task entities.
 */
@Dao
interface TaskDao : BaseDao<Task> {

    @Query("SELECT * FROM tasks ORDER BY startTime DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY startTime DESC")
    fun getTasksByStatus(status: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE type = :type ORDER BY startTime DESC")
    fun getTasksByType(type: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): Task?

    @Query("UPDATE tasks SET status = :status, progress = :progress, outputLog = :outputLog, exitCode = :exitCode, endTime = :endTime WHERE id = :id")
    suspend fun updateTaskExecution(
        id: String,
        status: String,
        progress: Float,
        outputLog: String,
        exitCode: Int?,
        endTime: Long?
    )

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tasks WHERE status = 'COMPLETED' OR status = 'FAILED'")
    suspend fun clearFinishedTasks()

    @Query("DELETE FROM tasks")
    suspend fun clearAll()
}
