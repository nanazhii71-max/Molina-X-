package com.molinax.di

import android.content.Context
import com.molinax.core.RuntimeExecutionBridge
import com.molinax.terminal.TerminalRuntimeBridge
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module that provides native runtime bridges and directory paths.
 */
@Module
@InstallIn(SingletonComponent::class)
object RuntimeModule {

    @Provides
    @Singleton
    @Named("prefixDir")
    fun providePrefixDir(@ApplicationContext context: Context): File {
        val dir = File(context.filesDir, "usr")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Provides
    @Singleton
    @Named("homeDir")
    fun provideHomeDir(@ApplicationContext context: Context): File {
        val dir = File(context.filesDir, "home")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Provides
    @Singleton
    fun provideRuntimeExecutionBridge(
        @Named("prefixDir") prefixDir: File,
        @Named("homeDir") homeDir: File
    ): RuntimeExecutionBridge {
        return TerminalRuntimeBridge(prefixDir, homeDir)
    }
}
