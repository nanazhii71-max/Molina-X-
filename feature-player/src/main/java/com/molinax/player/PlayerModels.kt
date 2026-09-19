package com.molinax.player

import java.io.File

enum class MediaInputType {
    LOCAL,
    BY_TITLE,
    UNIVERSAL_LINK
}

data class PlaylistItem(
    val id: String,
    val title: String,
    val source: String,
    val type: MediaInputType,
    val durationMs: Long = 0L,
    val localFile: File? = null
)

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val volumeBoostDb: Int = 0,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val currentItem: PlaylistItem? = null,
    val isMuted: Boolean = false
)

enum class AspectRatioMode(val label: String) {
    FIT("Fit (Auto)"),
    FILL("Fill / Crop"),
    SIXTEEN_NINE("16:9"),
    FOUR_THREE("4:3")
}

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val progressPercent: Float = 0f,
    val downloadSpeed: String = "",
    val eta: String = "",
    val isCompleted: Boolean = false,
    val error: String? = null
)
