package com.molinax.manager.di

import android.content.Context
import com.molinax.manager.data.AppDatabase
import com.molinax.manager.data.DataRepository
import com.molinax.manager.data.local.AppRecordDao
import com.molinax.manager.data.local.AppRecordRepository
import com.molinax.manager.data.local.FileMetadataDao
import com.molinax.manager.data.local.SnippetDao
import com.molinax.manager.data.local.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides database and persistence singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideAppRecordDao(database: AppDatabase): AppRecordDao {
        return database.appRecordDao()
    }

    @Provides
    @Singleton
    fun provideAppRecordRepository(dao: AppRecordDao): AppRecordRepository {
        return AppRecordRepository(dao)
    }

    @Provides
    @Singleton
    fun provideFileMetadataDao(database: AppDatabase): FileMetadataDao {
        return database.fileMetadataDao()
    }

    @Provides
    @Singleton
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    @Singleton
    fun provideSnippetDao(database: AppDatabase): SnippetDao {
        return database.snippetDao()
    }

    @Provides
    @Singleton
    fun provideDataRepository(database: AppDatabase): DataRepository {
        return DataRepository(database)
    }
}
