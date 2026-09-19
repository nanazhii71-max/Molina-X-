package com.molinax.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.molinax.data.local.AppRecord
import com.molinax.data.local.AppRecordDao
import com.molinax.data.local.FileMetadata
import com.molinax.data.local.FileMetadataDao
import com.molinax.data.local.Snippet
import com.molinax.data.local.SnippetDao
import com.molinax.data.local.Task
import com.molinax.data.local.TaskDao

/**
 * Room Database abstract class providing foundational local persistence for Molina-X.
 */
@Database(
    entities = [
        AppRecord::class,
        FileMetadata::class,
        Task::class,
        Snippet::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appRecordDao(): AppRecordDao
    abstract fun fileMetadataDao(): FileMetadataDao
    abstract fun taskDao(): TaskDao
    abstract fun snippetDao(): SnippetDao

    companion object {
        const val DATABASE_NAME = "molinax_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
