package com.molinax.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing persistent application data records (configurations, session logs, notes, or history).
 */
@Entity(tableName = "app_records")
data class AppRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis()
)
