package com.molinax.manager.media.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.molinax.manager.media.mpv.MPVPlaybackState
import com.molinax.manager.media.mpv.MPVPlayerEngine
import com.molinax.manager.player.AspectRatioMode
import java.util.Locale

/**
 * Video player surface Composable powered by MPV Android.
 *
 * Initializes the video rendering Surface via [SurfaceView] and [SurfaceHolder.Callback],
 * binding lifecycle events directly to [MPVPlayerEngine].
 *
 * Provides a rich, responsive overlay deck for comprehensive playback state management:
 * - Real-time progress timeline scrubber with duration formatting
 * - Play/Pause transport controls and 10s seek skips
 * - Speed multipliers (0.5x - 2.0x)
 * - Audio loudness boost (+0dB to +18dB)
 * - Dynamic aspect ratio switching (Fit, Fill, 16:9, 4:3)
 * - Auto-hiding control overlays on surface tap
 */
@Composable
fun MpvVideoSurface(
    engine: MPVPlayerEngine,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    onTitleClick: (() -> Unit)? = null
) {
    val playbackState by engine.playbackState.collectAsState()

    MpvVideoSurface(
        state = playbackState,
        onSurfaceCreated = { holder -> engine.attachSurface(holder) },
        onSurfaceDestroyed = { engine.detachSurface() },
        onTogglePlayPause = { engine.togglePlayPause() },
        onSeekTo = { posMs -> engine.seekTo(posMs) },
        onSeekRelative = { deltaSec -> engine.seekRelative(deltaSec) },
        onSpeedChange = { speed -> engine.setPlaybackSpeed(speed) },
        onVolumeBoostChange = { boostDb -> engine.setVolumeBoost(boostDb) },
        onAspectRatioChange = { mode -> engine.setAspectRatio(mode) },
        showControls = showControls,
        onTitleClick = onTitleClick,
        modifier = modifier
    )
}

@Composable
fun MpvVideoSurface(
    state: MPVPlaybackState,
    onSurfaceCreated: (SurfaceHolder) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVolumeBoostChange: (Int) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    onTitleClick: (() -> Unit)? = null
) {
    var areControlsVisible by remember { mutableStateOf(showControls) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var draggedPositionMs by remember { mutableLongStateOf(0L) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showBoostDialog by remember { mutableStateOf(false) }

    val currentPos = if (isDraggingSlider) draggedPositionMs else state.currentPositionMs
    val duration = state.durationMs.coerceAtLeast(1L)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("mpv_video_surface_container")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                areControlsVisible = !areControlsVisible
            },
        contentAlignment = Alignment.Center
    ) {
        // Native SurfaceView for MPV video output
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            onSurfaceCreated(holder)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            // Surface dimensions updated
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            onSurfaceDestroyed()
                        }
                    })
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("mpv_surface_view")
        )

        // Buffering Indicator
        if (state.isBuffering) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .testTag("mpv_buffering_indicator"),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF00D2FF),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Empty state placeholder if no media is active
        if (state.currentMediaSource == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(24.dp)
                    .testTag("mpv_empty_state")
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color(0xFF334155),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "MPV Video Surface Ready",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hardware Accelerated (GPU • mediacodec • ytdl)",
                    color = Color(0xFF00D2FF),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Playback Control Overlays
        if (areControlsVisible && state.currentMediaSource != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .testTag("mpv_controls_overlay")
            ) {
                // Top Bar (Media Title & Quick Badges)
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .testTag("mpv_top_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "MPV",
                                color = Color(0xFF00D2FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = state.currentMediaTitle ?: "MolinaX Media Player",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = onTitleClick != null) { onTitleClick?.invoke() }
                        )

                        // Aspect Ratio Toggle Chip
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .clickable {
                                    val nextMode = when (state.aspectRatio) {
                                        AspectRatioMode.FIT -> AspectRatioMode.FILL
                                        AspectRatioMode.FILL -> AspectRatioMode.SIXTEEN_NINE
                                        AspectRatioMode.SIXTEEN_NINE -> AspectRatioMode.FOUR_THREE
                                        AspectRatioMode.FOUR_THREE -> AspectRatioMode.FIT
                                    }
                                    onAspectRatioChange(nextMode)
                                }
                                .testTag("mpv_aspect_ratio_button")
                        ) {
                            Text(
                                text = state.aspectRatio.label,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Center Action Controls (Seek -10s, Play/Pause, Seek +10s)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSeekRelative(-10) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f), CircleShape)
                            .testTag("mpv_rewind_10s_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Seek -10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFF00D2FF), CircleShape)
                            .testTag("mpv_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = { onSeekRelative(10) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f), CircleShape)
                            .testTag("mpv_forward_10s_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Seek +10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Transport Deck (Scrubber, Speed, Boost, Timers)
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.92f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .testTag("mpv_bottom_deck")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Progress Slider & Timers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentPos),
                                color = Color(0xFF00D2FF),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("mpv_current_time_text")
                            )

                            Slider(
                                value = currentPos.toFloat().coerceIn(0f, duration.toFloat()),
                                onValueChange = { newValue ->
                                    isDraggingSlider = true
                                    draggedPositionMs = newValue.toLong()
                                },
                                onValueChangeFinished = {
                                    onSeekTo(draggedPositionMs)
                                    isDraggingSlider = false
                                },
                                valueRange = 0f..duration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00D2FF),
                                    activeTrackColor = Color(0xFF00D2FF),
                                    inactiveTrackColor = Color(0xFF334155)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .testTag("mpv_timeline_slider")
                            )

                            Text(
                                text = formatTime(state.durationMs),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag("mpv_duration_time_text")
                            )
                        }

                        // Bottom Toolbar (Speed selector, Audio boost, Aspect ratio)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Playback Speed Chips
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    val isSelected = (state.playbackSpeed - speed) in -0.05f..0.05f
                                    Surface(
                                        color = if (isSelected) Color(0xFF00D2FF) else Color(0xFF1E293B),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .clickable { onSpeedChange(speed) }
                                            .testTag("mpv_speed_chip_${speed}x")
                                    ) {
                                        Text(
                                            text = "${speed}x",
                                            color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            // Audio Loudness Boost Button
                            Surface(
                                color = if (state.volumeBoostDb > 0) Color(0xFFFFB300) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .clickable {
                                        // Cycle boost +0dB -> +6dB -> +12dB -> +18dB -> +0dB
                                        val nextBoost = when (state.volumeBoostDb) {
                                            0 -> 6
                                            6 -> 12
                                            12 -> 18
                                            else -> 0
                                        }
                                        onVolumeBoostChange(nextBoost)
                                    }
                                    .testTag("mpv_volume_boost_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Audio Boost",
                                        tint = if (state.volumeBoostDb > 0) Color.Black else Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (state.volumeBoostDb > 0) "+${state.volumeBoostDb}dB" else "Boost",
                                        color = if (state.volumeBoostDb > 0) Color.Black else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
