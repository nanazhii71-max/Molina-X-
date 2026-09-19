package com.molinax.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing reusable shell commands, scripts, code snippets, and templates
 * for the Terminal and Editor modules.
 */
@Entity(
    tableName = "snippets",
    indices = [
        Index(value = ["language"]),
        Index(value = ["category"]),
        Index(value = ["isFavorite"]),
        Index(value = ["lastUsedTimestamp"])
    ]
)
data class Snippet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val language: String = "bash",
    val category: String = "general",
    val isFavorite: Boolean = false,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)
