package com.molinax.utilities

import com.molinax.core.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileToolsManager {

    suspend fun listFiles(dir: File): List<File> = withContext(Dispatchers.IO) {
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        dir.listFiles()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        )?.toList() ?: emptyList()
    }

    suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun renameFile(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dest = File(file.parentFile, newName)
            file.renameTo(dest)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun copyFile(source: File, destDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val dest = File(destDir, source.name)
            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = true)
            } else {
                source.copyTo(dest, overwrite = true)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun moveFile(source: File, destDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val dest = File(destDir, source.name)
            source.renameTo(dest)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun computeChecksum(file: File, algorithm: String = "SHA-256"): String = withContext(Dispatchers.IO) {
        FileUtils.computeHash(file, algorithm)
    }

    suspend fun makeExecutable(file: File, executable: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            file.setExecutable(executable, false)
        } catch (e: Exception) {
            false
        }
    }
}
