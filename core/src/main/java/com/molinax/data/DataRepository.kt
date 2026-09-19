package com.molinax.data

import com.molinax.data.local.FileMetadata
import com.molinax.data.local.FileMetadataDao
import com.molinax.data.local.Snippet
import com.molinax.data.local.SnippetDao
import com.molinax.data.local.Task
import com.molinax.data.local.TaskDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataRepository providing a unified data access layer for FileMetadata, Task,
 * and Snippet entities via the Hilt-injected AppDatabase.
 *
 * Ensures clean separation of concerns between Room persistence and higher-level
 * modules (Editor, Terminal, Player, Utilities).
 */
@Singleton
class DataRepository @Inject constructor(
    private val database: AppDatabase
) {

    private val fileMetadataDao: FileMetadataDao
        get() = database.fileMetadataDao()

    private val taskDao: TaskDao
        get() = database.taskDao()

    private val snippetDao: SnippetDao
        get() = database.snippetDao()

    // ==========================================
    // FileMetadata Domain
    // ==========================================

    val allFiles: Flow<List<FileMetadata>>
        get() = fileMetadataDao.getAllFileMetadata()

    val favoriteFiles: Flow<List<FileMetadata>>
        get() = fileMetadataDao.getFavorites()

    fun getFilesByModule(module: String): Flow<List<FileMetadata>> {
        return fileMetadataDao.getByModule(module)
    }

    suspend fun getFileByPath(path: String): FileMetadata? {
        return fileMetadataDao.getByPath(path)
    }

    suspend fun insertFile(fileMetadata: FileMetadata): Long {
        return fileMetadataDao.insert(fileMetadata)
    }

    suspend fun insertFiles(files: List<FileMetadata>): List<Long> {
        return fileMetadataDao.insertAll(files)
    }

    suspend fun updateFile(fileMetadata: FileMetadata) {
        fileMetadataDao.update(fileMetadata)
    }

    suspend fun deleteFile(fileMetadata: FileMetadata) {
        fileMetadataDao.delete(fileMetadata)
    }

    suspend fun deleteFileByPath(path: String) {
        fileMetadataDao.deleteByPath(path)
    }

    suspend fun setFileFavorite(path: String, isFavorite: Boolean) {
        fileMetadataDao.updateFavoriteStatus(path, isFavorite)
    }

    suspend fun recordFileAccess(path: String, timestamp: Long = System.currentTimeMillis()) {
        fileMetadataDao.updateLastAccessed(path, timestamp)
    }

    suspend fun clearAllFiles() {
        fileMetadataDao.clearAll()
    }

    // ==========================================
    // Task Domain
    // ==========================================

    val allTasks: Flow<List<Task>>
        get() = taskDao.getAllTasks()

    fun getTasksByStatus(status: String): Flow<List<Task>> {
        return taskDao.getTasksByStatus(status)
    }

    fun getTasksByType(type: String): Flow<List<Task>> {
        return taskDao.getTasksByType(type)
    }

    suspend fun getTaskById(id: String): Task? {
        return taskDao.getTaskById(id)
    }

    suspend fun insertTask(task: Task): Long {
        return taskDao.insert(task)
    }

    suspend fun insertTasks(tasks: List<Task>): List<Long> {
        return taskDao.insertAll(tasks)
    }

    suspend fun updateTask(task: Task) {
        taskDao.update(task)
    }

    suspend fun updateTaskExecution(
        id: String,
        status: String,
        progress: Float,
        outputLog: String,
        exitCode: Int?,
        endTime: Long? = null
    ) {
        taskDao.updateTaskExecution(id, status, progress, outputLog, exitCode, endTime)
    }

    suspend fun deleteTask(task: Task) {
        taskDao.delete(task)
    }

    suspend fun deleteTaskById(id: String) {
        taskDao.deleteById(id)
    }

    suspend fun clearFinishedTasks() {
        taskDao.clearFinishedTasks()
    }

    suspend fun clearAllTasks() {
        taskDao.clearAll()
    }

    // ==========================================
    // Snippet Domain
    // ==========================================

    val allSnippets: Flow<List<Snippet>>
        get() = snippetDao.getAllSnippets()

    val favoriteSnippets: Flow<List<Snippet>>
        get() = snippetDao.getFavoriteSnippets()

    fun getSnippetsByCategory(category: String): Flow<List<Snippet>> {
        return snippetDao.getSnippetsByCategory(category)
    }

    fun getSnippetsByLanguage(language: String): Flow<List<Snippet>> {
        return snippetDao.getSnippetsByLanguage(language)
    }

    suspend fun getSnippetById(id: Long): Snippet? {
        return snippetDao.getSnippetById(id)
    }

    suspend fun insertSnippet(snippet: Snippet): Long {
        return snippetDao.insert(snippet)
    }

    suspend fun insertSnippets(snippets: List<Snippet>): List<Long> {
        return snippetDao.insertAll(snippets)
    }

    suspend fun updateSnippet(snippet: Snippet) {
        snippetDao.update(snippet)
    }

    suspend fun deleteSnippet(snippet: Snippet) {
        snippetDao.delete(snippet)
    }

    suspend fun deleteSnippetById(id: Long) {
        snippetDao.deleteById(id)
    }

    suspend fun setSnippetFavorite(id: Long, isFavorite: Boolean) {
        snippetDao.updateFavoriteStatus(id, isFavorite)
    }

    suspend fun markSnippetUsed(id: Long, timestamp: Long = System.currentTimeMillis()) {
        snippetDao.markSnippetUsed(id, timestamp)
    }

    suspend fun clearAllSnippets() {
        snippetDao.clearAll()
    }
}
