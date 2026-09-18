package com.molinax.manager.utilities.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.molinax.manager.core.FileUtils
import com.molinax.manager.core.PermissionHelper
import com.molinax.manager.core.StoragePermissionGate
import com.molinax.manager.core.SubsystemNavigator
import com.molinax.manager.core.MediaInputPayload
import com.molinax.manager.utilities.PackageItem
import com.molinax.manager.utilities.UtilitiesViewModel
import com.molinax.manager.utilities.UtilityTab
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilitiesScreen(
    viewModel: UtilitiesViewModel,
    navigator: SubsystemNavigator,
    modifier: Modifier = Modifier
) {
    val activeTab by viewModel.activeTab.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        // Utilities Top Navigation Bar
        Surface(
            color = Color(0xFF111827),
            tonalElevation = 4.dp
        ) {
            ScrollableTabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = Color(0xFF111827),
                contentColor = Color(0xFF00D2FF),
                edgePadding = 8.dp
            ) {
                UtilityTab.entries.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }
        }

        // Active Utility Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                UtilityTab.FILES -> StoragePermissionGate(
                    onPermissionGranted = { viewModel.refreshFiles() }
                ) {
                    FileToolsTab(viewModel, navigator)
                }
                UtilityTab.PACKAGES -> PackageManagerTab(viewModel)
                UtilityTab.ARCHIVE -> ArchiveTab(viewModel)
                UtilityTab.ENCODING -> EncodingTab(viewModel)
                UtilityTab.SETTINGS -> SettingsTab(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileToolsTab(
    viewModel: UtilitiesViewModel,
    navigator: SubsystemNavigator
) {
    val currentDir by viewModel.currentDir.collectAsState()
    val files by viewModel.fileList.collectAsState()
    val selectedFile by viewModel.selectedFile.collectAsState()
    val checksumResult by viewModel.checksumResult.collectAsState()

    var showActionSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Breadcrumb and Actions
        Surface(
            color = Color(0xFF0F172A),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateUp() },
                    enabled = currentDir.parentFile != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Up",
                        tint = if (currentDir.parentFile != null) Color(0xFF00D2FF) else Color(0xFF475569)
                    )
                }

                Text(
                    text = currentDir.name.ifEmpty { "/" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    maxLines = 1
                )

                IconButton(
                    onClick = { navigator.navigateToTerminal(workingDir = currentDir) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Open Terminal Here",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.refreshFiles() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // File List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(files) { file ->
                val isDir = file.isDirectory
                val isMedia = FileUtils.isMediaFile(file)
                val isCode = FileUtils.isCodeOrTextFile(file)

                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isDir) {
                                viewModel.navigateToDir(file)
                            } else {
                                viewModel.selectFile(file)
                                showActionSheet = true
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isDir -> Icons.Default.Folder
                                isMedia -> Icons.Default.PlayCircle
                                isCode -> Icons.Default.Code
                                else -> Icons.Default.Description
                            },
                            contentDescription = null,
                            tint = when {
                                isDir -> Color(0xFFFFB300)
                                isMedia -> Color(0xFF00D2FF)
                                isCode -> Color(0xFF7C4DFF)
                                else -> Color(0xFF94A3B8)
                            },
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                text = if (isDir) "Directory" else "${FileUtils.formatFileSize(file.length())} • ${FileUtils.formatDate(file.lastModified())}",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }

                        // Auto-Route Quick Action
                        if (isMedia) {
                            FilledIconButton(
                                onClick = { navigator.navigateToPlayer(MediaInputPayload.LocalFile(file)) },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color(0xFF00D2FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else if (isCode) {
                            FilledIconButton(
                                onClick = { navigator.navigateToEditor(file) },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = Color(0xFF7C4DFF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
            }
        }
    }

    // File Actions Sheet
    if (showActionSheet && selectedFile != null) {
        val target = selectedFile!!
        val isMedia = FileUtils.isMediaFile(target)
        val isCode = FileUtils.isCodeOrTextFile(target)

        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            containerColor = Color(0xFF111827)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = target.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${FileUtils.formatFileSize(target.length())} • ${target.absolutePath}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Actions
                if (isMedia) {
                    Button(
                        onClick = {
                            navigator.navigateToPlayer(MediaInputPayload.LocalFile(target))
                            showActionSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open in Player (MediaAutoRoute)", color = Color.Black)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (isCode) {
                    Button(
                        onClick = {
                            navigator.navigateToEditor(target)
                            showActionSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open in Editor")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                OutlinedButton(
                    onClick = {
                        navigator.navigateToTerminal(
                            command = if (target.canExecute()) target.absolutePath else null,
                            workingDir = target.parentFile
                        )
                        showActionSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF00E676))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open in Terminal", color = Color(0xFF00E676))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Checksum Calculator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.computeChecksum(target, "SHA-256") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("SHA-256", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.computeChecksum(target, "MD5") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("MD5", fontSize = 12.sp)
                    }
                }

                if (checksumResult != null) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = checksumResult!!,
                            color = Color(0xFF00E676),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Rename & Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            renameInput = target.name
                            showRenameDialog = true
                            showActionSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Rename")
                    }
                    Button(
                        onClick = {
                            viewModel.deleteSelected(target)
                            showActionSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameSelected(selectedFile!!, renameInput)
                            showRenameDialog = false
                        }
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PackageManagerTab(viewModel: UtilitiesViewModel) {
    val pm = viewModel.packageManager
    val logs by pm.logs.collectAsState()
    val isBusy by pm.isBusy.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var customPackageInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Actions row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { coroutineScope.launch { pm.updateDatabase() } },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF))
            ) {
                Text("apt update", color = Color.Black)
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = { pm.clearLogs() },
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text("Clear Log", color = Color(0xFF94A3B8))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Custom package install field
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customPackageInput,
                onValueChange = { customPackageInput = it },
                placeholder = { Text("Enter package name (e.g. clang, git)...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (customPackageInput.isNotBlank()) {
                        coroutineScope.launch { pm.installPackage(customPackageInput) }
                        customPackageInput = ""
                    }
                },
                enabled = !isBusy && customPackageInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) {
                Text("Install")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Popular Tools Quick Install
        Text("Popular Terminal & Development Packages:", fontSize = 12.sp, color = Color(0xFF94A3B8))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            pm.recommendedPackages.forEach { pkg ->
                SuggestionChip(
                    onClick = {
                        coroutineScope.launch { pm.installPackage(pkg) }
                    },
                    label = { Text("+ $pkg", fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Terminal Log Console
        Surface(
            color = Color(0xFF090D16),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(logs) { log ->
                    Text(
                        text = log,
                        color = if (log.startsWith(">")) Color(0xFF00D2FF) else if (log.startsWith("[ERR]")) Color(0xFFFF5252) else Color(0xFFE2E8F0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ArchiveTab(viewModel: UtilitiesViewModel) {
    val currentDir by viewModel.currentDir.collectAsState()
    val files by viewModel.fileList.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Archive Tools (Zip & Unzip)",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Current Directory: ${currentDir.absolutePath}",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Button(
            onClick = {
                coroutineScope.launch {
                    val zipDest = File(currentDir, "archive-${System.currentTimeMillis()}.zip")
                    val sourceFiles = files.take(10)
                    statusMessage = "Compressing..."
                    com.molinax.manager.utilities.ArchiveManager.createZip(sourceFiles, zipDest).onSuccess {
                        statusMessage = "Created: ${it.name}"
                        viewModel.refreshFiles()
                    }.onFailure {
                        statusMessage = "Error: ${it.message}"
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Archive, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Compress directory files to ZIP", color = Color.Black)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (statusMessage.isNotEmpty()) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusMessage,
                    color = Color(0xFF00E676),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun EncodingTab(viewModel: UtilitiesViewModel) {
    val input by viewModel.encodeInput.collectAsState()
    val output by viewModel.encodeOutput.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Text & Payload Encoders", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { viewModel.setEncodeInput(it) },
            placeholder = { Text("Enter string or payload...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Tool buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(onClick = { viewModel.runEncoding("b64_enc") }) { Text("Base64 Enc") }
            Button(onClick = { viewModel.runEncoding("b64_dec") }) { Text("Base64 Dec") }
            Button(onClick = { viewModel.runEncoding("url_enc") }) { Text("URL Enc") }
            Button(onClick = { viewModel.runEncoding("url_dec") }) { Text("URL Dec") }
            Button(onClick = { viewModel.runEncoding("hex") }) { Text("Hex Dump") }
            Button(onClick = { viewModel.runEncoding("json") }) { Text("Format JSON") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Output:", fontSize = 12.sp, color = Color(0xFF94A3B8))
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Text(
                text = output.ifEmpty { "Result will appear here..." },
                color = if (output.isNotEmpty()) Color(0xFF00E676) else Color(0xFF64748B),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun SettingsTab(viewModel: UtilitiesViewModel) {
    val context = LocalContext.current
    val hasStorage = PermissionHelper.hasStoragePermission(context)
    val hasNotifications = PermissionHelper.hasNotificationPermission(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("System & Permissions Dashboard", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        // Storage Permission Card
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasStorage) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (hasStorage) Color(0xFF00E676) else Color(0xFFFFB300)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("All Files Access (MANAGE_EXTERNAL_STORAGE)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (hasStorage) "Granted" else "Required for file management & editor", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                if (!hasStorage) {
                    Button(
                        onClick = { context.startActivity(PermissionHelper.getManageStorageIntent(context)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF))
                    ) {
                        Text("Grant", color = Color.Black, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Notification Permission Card
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasNotifications) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (hasNotifications) Color(0xFF00E676) else Color(0xFFFFB300)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Post Notifications", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (hasNotifications) "Granted" else "Keeps Terminal and Player running in background", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App Information & Open Source Attribution
        Text("About MolinaX Manager", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Version: 1.0.0 (GPLv3 Open Source)", color = Color(0xFF00D2FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Package: com.molinax.manager", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Text("Target ABI: arm64-v8a, armeabi-v7a", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Text("PREFIX: /data/data/com.molinax.manager/files/usr", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Text("HOME: /data/data/com.molinax.manager/files/home", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("Upstream Attributions:", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text("• mpv-android (GPLv3) - Media Player core & ytdl_hook", color = Color(0xFF94A3B8), fontSize = 11.sp)
                Text("• Termux (GPLv3) - Linux terminal emulator & runtime", color = Color(0xFF94A3B8), fontSize = 11.sp)
                Text("• Sora Editor (Rosemoe / Apache 2.0) - Code editor engine", color = Color(0xFF94A3B8), fontSize = 11.sp)
            }
        }
    }
}
