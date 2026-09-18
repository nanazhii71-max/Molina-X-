package com.molinax.manager.explorer.ui

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molinax.manager.core.FileExplorerHelper
import com.molinax.manager.core.MediaInputPayload
import com.molinax.manager.core.SubsystemNavigator
import kotlinx.coroutines.launch
import java.io.File

/**
 * High-performance Material 3 File Explorer Screen for MolinaX Manager.
 *
 * Utilizes [FileExplorerHelper] to display hierarchical filesystem directories
 * in a reactive [LazyColumn] with categorized iconography, breadcrumb navigation,
 * quick storage destination chips, and deep links into mpv Player, Sora Editor,
 * and Linux Terminal subsystems.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    initialDirectory: File = Environment.getExternalStorageDirectory() ?: File("/"),
    navigator: SubsystemNavigator? = null,
    onFileClick: ((FileExplorerHelper.ExplorerItem) -> Unit)? = null,
    onDirectoryChanged: ((File) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentDir by remember(initialDirectory) { mutableStateOf(initialDirectory) }
    var filter by remember { mutableStateOf(FileExplorerHelper.ExplorerFilter()) }
    var items by remember { mutableStateOf<List<FileExplorerHelper.ExplorerItem>>(emptyList()) }
    var summary by remember { mutableStateOf<FileExplorerHelper.DirectorySummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var selectedItemForAction by remember { mutableStateOf<FileExplorerHelper.ExplorerItem?>(null) }

    val standardDirs = remember(context) { FileExplorerHelper.getStandardDirectories(context) }
    val breadcrumbs = remember(currentDir) { FileExplorerHelper.buildBreadcrumbs(currentDir) }

    // Load directory files and summary whenever currentDir or filter changes
    LaunchedEffect(currentDir, filter) {
        isLoading = true
        items = FileExplorerHelper.listDirectory(currentDir, filter)
        summary = FileExplorerHelper.computeDirectorySummary(currentDir)
        isLoading = false
    }

    fun navigateTo(dir: File) {
        if (dir.exists() && dir.isDirectory) {
            currentDir = dir
            onDirectoryChanged?.invoke(dir)
        }
    }

    fun navigateUp() {
        val parent = currentDir.parentFile
        if (parent != null && parent.canRead()) {
            navigateTo(parent)
        }
    }

    fun reload() {
        coroutineScope.launch {
            isLoading = true
            items = FileExplorerHelper.listDirectory(currentDir, filter)
            summary = FileExplorerHelper.computeDirectorySummary(currentDir)
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .testTag("file_explorer_screen")
    ) {
        // Top Navigation Bar
        Surface(
            color = Color(0xFF111827),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { navigateUp() },
                        enabled = currentDir.parentFile != null,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("nav_up_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Navigate Up",
                            tint = if (currentDir.parentFile != null) Color(0xFF00D2FF) else Color(0xFF4B5563),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = currentDir.name.ifEmpty { "/" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .testTag("current_dir_title")
                    )

                    IconButton(
                        onClick = { isSearchOpen = !isSearchOpen },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("nav_search_toggle")
                    ) {
                        Icon(
                            imageVector = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Toggle Search",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (navigator != null) {
                        IconButton(
                            onClick = { navigator.navigateToTerminal(workingDir = currentDir) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("nav_open_terminal")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Open Terminal Here",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { reload() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("nav_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Directory",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Breadcrumbs bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    breadcrumbs.forEachIndexed { index, breadcrumb ->
                        Text(
                            text = breadcrumb.name,
                            color = if (index == breadcrumbs.lastIndex) Color(0xFF00D2FF) else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (index == breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clickable { navigateTo(breadcrumb.file) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("breadcrumb_${breadcrumb.name}")
                        )
                        if (index < breadcrumbs.lastIndex) {
                            Text(
                                text = "/",
                                color = Color(0xFF4B5563),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Quick Storage Navigation Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(Color(0xFF0F172A))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            standardDirs.forEach { (label, dir) ->
                val isSelected = currentDir.absolutePath == dir.absolutePath
                AssistChip(
                    onClick = { navigateTo(dir) },
                    label = {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF111827),
                        labelColor = if (isSelected) Color(0xFF00D2FF) else Color(0xFF94A3B8)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = null,
                    modifier = Modifier.testTag("quick_dir_$label")
                )
            }
        }

        // Optional Search Bar
        if (isSearchOpen) {
            Surface(
                color = Color(0xFF111827),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = {
                        searchInput = it
                        filter = filter.copy(searchFilter = it)
                    },
                    placeholder = { Text("Filter by filename...", color = Color(0xFF64748B), fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00D2FF),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        cursorColor = Color(0xFF00D2FF)
                    ),
                    trailingIcon = {
                        if (searchInput.isNotEmpty()) {
                            IconButton(onClick = {
                                searchInput = ""
                                filter = filter.copy(searchFilter = "")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { /* Done */ }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explorer_search_input")
                )
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = filter.categoryFilter == null,
                onClick = { filter = filter.copy(categoryFilter = null) },
                label = { Text("All", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF00D2FF),
                    selectedLabelColor = Color.Black,
                    containerColor = Color(0xFF1E293B),
                    labelColor = Color(0xFF94A3B8)
                )
            )

            listOf(
                FileExplorerHelper.FileCategory.DIRECTORY,
                FileExplorerHelper.FileCategory.VIDEO,
                FileExplorerHelper.FileCategory.AUDIO,
                FileExplorerHelper.FileCategory.CODE,
                FileExplorerHelper.FileCategory.DOCUMENT,
                FileExplorerHelper.FileCategory.ARCHIVE
            ).forEach { cat ->
                val selected = filter.categoryFilter == cat
                FilterChip(
                    selected = selected,
                    onClick = { filter = filter.copy(categoryFilter = if (selected) null else cat) },
                    label = { Text(cat.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00D2FF),
                        selectedLabelColor = Color.Black,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }
        }

        // File List in LazyColumn
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00D2FF))
                }
            } else if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("explorer_empty_state"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Directory is empty",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (filter.searchFilter.isNotBlank()) "No files match '${filter.searchFilter}'" else "No files or subdirectories found.",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("file_explorer_lazy_column")
                ) {
                    items(
                        items = items,
                        key = { it.path }
                    ) { item ->
                        FileExplorerRowItem(
                            item = item,
                            navigator = navigator,
                            onClick = {
                                if (item.isDirectory) {
                                    navigateTo(item.file)
                                } else {
                                    if (onFileClick != null) {
                                        onFileClick(item)
                                    } else {
                                        selectedItemForAction = item
                                    }
                                }
                            },
                            onQuickPlay = { file ->
                                navigator?.navigateToPlayer(MediaInputPayload.LocalFile(file))
                            },
                            onQuickEdit = { file ->
                                navigator?.navigateToEditor(file)
                            }
                        )
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                    }
                }
            }
        }

        // Summary Bar
        summary?.let { sum ->
            Surface(
                color = Color(0xFF111827),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${sum.directoryCount} folders • ${sum.fileCount} files",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Text(
                        text = sum.formattedTotalBytes,
                        color = Color(0xFF00D2FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Modal Action Sheet for Selected File
    selectedItemForAction?.let { selected ->
        val file = selected.file
        ModalBottomSheet(
            onDismissRequest = { selectedItemForAction = null },
            containerColor = Color(0xFF111827)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = getCategoryIcon(selected.category),
                        contentDescription = null,
                        tint = getCategoryColor(selected.category),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selected.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${selected.formattedSize} • ${selected.category.label}",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selected.category == FileExplorerHelper.FileCategory.VIDEO || selected.category == FileExplorerHelper.FileCategory.AUDIO) {
                    Button(
                        onClick = {
                            navigator?.navigateToPlayer(MediaInputPayload.LocalFile(file))
                            selectedItemForAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in mpv Player", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (selected.category == FileExplorerHelper.FileCategory.CODE || selected.category == FileExplorerHelper.FileCategory.DOCUMENT || selected.size < 5_000_000) {
                    Button(
                        onClick = {
                            navigator?.navigateToEditor(file)
                            selectedItemForAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Sora Editor", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = {
                        navigator?.navigateToTerminal(workingDir = file.parentFile)
                        selectedItemForAction = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Terminal at Path", color = Color.White)
                }
            }
        }
    }
}

/**
 * Individual file/folder item row in the [LazyColumn].
 */
@Composable
private fun FileExplorerRowItem(
    item: FileExplorerHelper.ExplorerItem,
    navigator: SubsystemNavigator?,
    onClick: () -> Unit,
    onQuickPlay: (File) -> Unit,
    onQuickEdit: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = getCategoryIcon(item.category)
    val tint = getCategoryColor(item.category)

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("explorer_item_${item.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.category.label,
                    tint = tint,
                    modifier = Modifier.size(22.dp).testTag("explorer_item_icon")
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("explorer_item_name")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (item.isDirectory) "Folder" else item.formattedSize,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "•",
                        color = Color(0xFF475569),
                        fontSize = 10.sp
                    )
                    Text(
                        text = item.formattedDate,
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )

                    if (item.isExecutable && !item.isDirectory) {
                        Surface(
                            color = Color(0x3310B981),
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "x",
                                color = Color(0xFF10B981),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Quick Routing Action Buttons
            if (navigator != null) {
                if (item.category == FileExplorerHelper.FileCategory.VIDEO || item.category == FileExplorerHelper.FileCategory.AUDIO) {
                    IconButton(
                        onClick = { onQuickPlay(item.file) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF00D2FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (item.category == FileExplorerHelper.FileCategory.CODE) {
                    IconButton(
                        onClick = { onQuickEdit(item.file) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF7C4DFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun getCategoryIcon(category: FileExplorerHelper.FileCategory): ImageVector = when (category) {
    FileExplorerHelper.FileCategory.DIRECTORY -> Icons.Default.Folder
    FileExplorerHelper.FileCategory.VIDEO -> Icons.Default.Movie
    FileExplorerHelper.FileCategory.AUDIO -> Icons.Default.MusicNote
    FileExplorerHelper.FileCategory.CODE -> Icons.Default.Code
    FileExplorerHelper.FileCategory.ARCHIVE -> Icons.Default.Archive
    FileExplorerHelper.FileCategory.IMAGE -> Icons.Default.Description
    FileExplorerHelper.FileCategory.DOCUMENT -> Icons.Default.Description
    FileExplorerHelper.FileCategory.BINARY -> Icons.Default.Build
    FileExplorerHelper.FileCategory.OTHER -> Icons.Default.Description
}

private fun getCategoryColor(category: FileExplorerHelper.FileCategory): Color = when (category) {
    FileExplorerHelper.FileCategory.DIRECTORY -> Color(0xFFFFB300)
    FileExplorerHelper.FileCategory.VIDEO -> Color(0xFF00D2FF)
    FileExplorerHelper.FileCategory.AUDIO -> Color(0xFFA855F7)
    FileExplorerHelper.FileCategory.CODE -> Color(0xFF10B981)
    FileExplorerHelper.FileCategory.ARCHIVE -> Color(0xFFF97316)
    FileExplorerHelper.FileCategory.IMAGE -> Color(0xFFEC4899)
    FileExplorerHelper.FileCategory.DOCUMENT -> Color(0xFF3B82F6)
    FileExplorerHelper.FileCategory.BINARY -> Color(0xFFF43F5E)
    FileExplorerHelper.FileCategory.OTHER -> Color(0xFF94A3B8)
}
