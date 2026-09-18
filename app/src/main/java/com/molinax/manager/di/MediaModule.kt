package com.molinax.manager.di

import android.content.Context
import com.molinax.manager.media.mpv.MPVPlayerEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module for Media and MPV player services.
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideMPVPlayerEngine(
        @ApplicationContext context: Context
    ): MPVPlayerEngine {
        return MPVPlayerEngine(context)
    }
}
