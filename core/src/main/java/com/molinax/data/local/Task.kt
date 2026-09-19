package com.molinax.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing background tasks and jobs across manager modules
 * (e.g., yt-dlp media downloads, apt/dpkg package installations, archive compressions, shell jobs).
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["status"]),
        Index(value = ["type"]),
        Index(value = ["startTime"])
    ]
)
data class Task(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: String,
    val command: String? = null,
    val status: String = "PENDING",
    val progress: Float = 0f,
    val outputLog: String = "",
    val exitCode: Int? = null,
    val destinationPath: String? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null
)
