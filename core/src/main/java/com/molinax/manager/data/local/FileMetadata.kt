package com.molinax.manager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing metadata for files accessed across Editor, Player, and Utilities modules.
 */
@Entity(
    tableName = "file_metadata",
    indices = [
        Index(value = ["module"]),
        Index(value = ["isFavorite"]),
        Index(value = ["lastAccessedTimestamp"])
    ]
)
data class FileMetadata(
    @PrimaryKey
    val path: String,
    val name: String,
    val sizeBytes: Long = 0L,
    val mimeType: String = "*/*",
    val lastModified: Long = 0L,
    val isDirectory: Boolean = false,
    val isFavorite: Boolean = false,
    val module: String = "UTILITIES",
    val checksum: String? = null,
    val lastAccessedTimestamp: Long = System.currentTimeMillis()
)
