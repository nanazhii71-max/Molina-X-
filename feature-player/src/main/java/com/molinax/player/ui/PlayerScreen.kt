package com.molinax.player.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.molinax.player.*
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val playlist by viewModel.playlist.collectAsState()
    val downloadTasks by viewModel.downloadTasks.collectAsState()
    val isYtDlpInstalled by viewModel.isYtDlpInstalled.collectAsState()

    var showLocalFileDialog by remember { mutableStateOf(false) }
    var showTitleDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    var titleInput by remember { mutableStateOf("") }
    var linkInput by remember { mutableStateOf("") }
    var downloadUrlInput by remember { mutableStateOf("") }

    // SAF Document picker fallback
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val item = PlaylistItem(
                id = it.toString(),
                title = it.lastPathSegment ?: "Selected Media",
                source = it.toString(),
                type = MediaInputType.LOCAL
            )
            viewModel.playerHost.addToPlaylist(item)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        // Top Player Bar
        Surface(
            color = Color(0xFF111827),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color(0xFF00D2FF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playbackState.currentItem?.title ?: "MolinaX Player",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                // 3 Input Methods & Downloader
                IconButton(
                    onClick = { openDocumentLauncher.launch(arrayOf("video/*", "audio/*")) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Open Local File",
                        tint = Color(0xFF00D2FF),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { showTitleDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search by Title",
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { showLinkDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Stream Universal Link",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { showDownloadDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "yt-dlp Downloader",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Video Surface Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                viewModel.playerHost.setSurfaceHolder(holder)
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                viewModel.playerHost.setSurfaceHolder(null)
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Empty state placeholder
            if (playbackState.currentItem == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFF334155),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Select media to begin playback",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Supports Local Files, YouTube Title Search, Universal Links",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Bottom Controls Deck
        Surface(
            color = Color(0xFF111827),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Timeline Scrubber
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(playbackState.currentPositionMs),
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Slider(
                        value = if (playbackState.durationMs > 0) {
                            (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f,
                        onValueChange = { frac ->
                            val target = (frac * playbackState.durationMs).toLong()
                            viewModel.playerHost.seekTo(target)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00D2FF),
                            activeTrackColor = Color(0xFF00D2FF),
                            inactiveTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = formatTime(playbackState.durationMs),
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Playback Control Buttons Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Playlist button
                    IconButton(onClick = { showPlaylistSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Playlist",
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    // Seek backward 10s
                    IconButton(onClick = {
                        val newPos = (playbackState.currentPositionMs - 10000L).coerceAtLeast(0L)
                        viewModel.playerHost.seekTo(newPos)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White
                        )
                    }

                    // Prev track
                    IconButton(onClick = { viewModel.playerHost.playPrevious() }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White
                        )
                    }

                    // Primary Play / Pause
                    FilledIconButton(
                        onClick = { viewModel.playerHost.togglePlayPause() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF00D2FF)
                        ),
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("player_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Next track
                    IconButton(onClick = { viewModel.playerHost.playNext() }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White
                        )
                    }

                    // Seek forward 10s
                    IconButton(onClick = {
                        val newPos = (playbackState.currentPositionMs + 10000L).coerceAtMost(playbackState.durationMs)
                        viewModel.playerHost.seekTo(newPos)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White
                        )
                    }

                    // Settings (Speed / Audio Boost)
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Audio & Video Adjustments",
                            tint = Color(0xFF00D2FF)
                        )
                    }
                }
            }
        }
    }

    // By Title Query Dialog
    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text("Play by Title (YouTube query)") },
            text = {
                Column {
                    Text(
                        text = "Query is resolved via mpv ytdl_hook (ytsearch syntax):",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        placeholder = { Text("e.g. Lofi hip hop radio") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleInput.isNotBlank()) {
                            viewModel.playByTitle(titleInput)
                            titleInput = ""
                            showTitleDialog = false
                        }
                    }
                ) { Text("Search & Play") }
            },
            dismissButton = {
                TextButton(onClick = { showTitleDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Universal Link Dialog
    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Stream Universal Link") },
            text = {
                Column {
                    Text(
                        text = "Enter direct stream URL or any video extractor URL:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        placeholder = { Text("https://example.com/video.mp4") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (linkInput.isNotBlank()) {
                            viewModel.playUniversalLink(linkInput)
                            linkInput = ""
                            showLinkDialog = false
                        }
                    }
                ) { Text("Play Link") }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Downloader Dialog (yt-dlp subprocess via RuntimeExecutionBridge)
    if (showDownloadDialog) {
        ModalBottomSheet(
            onDismissRequest = { showDownloadDialog = false },
            containerColor = Color(0xFF111827)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "yt-dlp Media Downloader",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Downloads videos/audio directly to device storage via RuntimeExecutionBridge",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = downloadUrlInput,
                        onValueChange = { downloadUrlInput = it },
                        placeholder = { Text("Paste video URL...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (downloadUrlInput.isNotBlank()) {
                                viewModel.startDownload(downloadUrlInput)
                                downloadUrlInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                    ) {
                        Text("Download", color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Active Downloads (${downloadTasks.size})",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 14.sp
                )

                if (downloadTasks.isEmpty()) {
                    Text(
                        text = "No active downloads",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(downloadTasks) { task ->
                            Surface(
                                color = Color(0xFF1F2937),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = task.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { task.progressPercent / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFF00E676),
                                        trackColor = Color(0xFF374151)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", task.progressPercent)}%",
                                            color = Color(0xFF00E676),
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "${task.downloadSpeed} | ETA: ${task.eta}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Settings & Equalizer / Speed Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = Color(0xFF111827)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Player Adjustments",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Playback Speed
                Text(text = "Playback Speed: ${playbackState.playbackSpeed}x", color = Color(0xFF00D2FF), fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                        FilterChip(
                            selected = playbackState.playbackSpeed == spd,
                            onClick = { viewModel.playerHost.setPlaybackSpeed(spd) },
                            label = { Text("${spd}x", fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Audio Boost (dB)
                Text(
                    text = "Audio Loudness Boost: +${playbackState.volumeBoostDb} dB",
                    color = Color(0xFF00E676),
                    fontSize = 13.sp
                )
                Slider(
                    value = playbackState.volumeBoostDb.toFloat(),
                    onValueChange = { viewModel.playerHost.setVolumeBoost(it.toInt()) },
                    valueRange = 0f..12f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E676),
                        activeTrackColor = Color(0xFF00E676)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Aspect Ratio
                Text(text = "Aspect Ratio", color = Color(0xFF7C4DFF), fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AspectRatioMode.entries.forEach { mode ->
                        FilterChip(
                            selected = playbackState.aspectRatio == mode,
                            onClick = { viewModel.playerHost.setAspectRatio(mode) },
                            label = { Text(mode.label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Playlist Queue Sheet
    if (showPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
            containerColor = Color(0xFF111827)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Playlist Queue (${playlist.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (playlist.isEmpty()) {
                    Text(
                        text = "Queue is empty",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(playlist) { item ->
                            val isCurrent = item.id == playbackState.currentItem?.id
                            Surface(
                                color = if (isCurrent) Color(0xFF1F2937) else Color(0xFF161F30),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        viewModel.playerHost.playItem(item)
                                        showPlaylistSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (isCurrent) Color(0xFF00D2FF) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = Color.White,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = item.type.name,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
