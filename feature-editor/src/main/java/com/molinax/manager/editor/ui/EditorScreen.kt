package com.molinax.manager.editor.ui

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molinax.manager.core.FileUtils
import com.molinax.manager.editor.EditorViewModel
import com.molinax.manager.editor.SyntaxHighlighter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.documents.collectAsState()
    val activeIndex by viewModel.activeDocIndex.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val replaceQuery by viewModel.replaceQuery.collectAsState()
    val matchCase by viewModel.matchCase.collectAsState()
    val useRegex by viewModel.useRegex.collectAsState()
    val saveStatusMessage by viewModel.saveStatusMessage.collectAsState()

    val activeDoc = viewModel.activeDocument()

    var showSaveAsDialog by remember { mutableStateOf(false) }
    var saveAsPathInput by remember { mutableStateOf("") }
    var internalFileNameInput by remember { mutableStateOf("") }
    var showPreferencesDialog by remember { mutableStateOf(false) }
    var showFileBrowserSheet by remember { mutableStateOf(false) }

    // Storage Access Framework (SAF) File Creation Contract
    val suggestedMimeType = activeDoc?.let { viewModel.getSuggestedMimeType(it) } ?: "text/plain"
    val safCreateDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(suggestedMimeType)
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveAsUri(uri)
        }
    }

    // Storage Access Framework (SAF) Document Opening Contract
    val safOpenDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.openUri(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        // Document Tabs Row
        Surface(
            color = Color(0xFF111827),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                documents.forEachIndexed { index, doc ->
                    val isSelected = index == activeIndex
                    Surface(
                        color = if (isSelected) Color(0xFF1F2937) else Color(0xFF131B2E),
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable { viewModel.selectTab(index) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (doc.isUriBacked) {
                                Surface(
                                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = "SAF",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${doc.title}${if (doc.isDirty) " *" else ""}",
                                color = if (isSelected) Color(0xFF00D2FF) else Color(0xFF94A3B8),
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tab",
                                tint = if (isSelected) Color.White else Color(0xFF64748B),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { viewModel.closeTab(index) }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { viewModel.newTab() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Tab",
                        tint = Color(0xFF00D2FF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Action Toolbar
        Surface(
            color = Color(0xFF0F172A),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File browser drawer button
                IconButton(
                    onClick = { showFileBrowserSheet = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Open File Explorer",
                        tint = Color(0xFF00D2FF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Storage Access Framework Open Document
                IconButton(
                    onClick = { safOpenDocumentLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("editor_open_saf_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = "Open via Storage Access Framework",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Save (Direct to file/Uri or triggers SAF if new)
                IconButton(
                    onClick = {
                        if (activeDoc?.isSaved == true) {
                            viewModel.saveActiveDocument()
                        } else {
                            safCreateDocumentLauncher.launch(activeDoc?.title ?: "document.txt")
                        }
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("editor_save_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save Document",
                        tint = if (activeDoc?.isDirty == true) Color(0xFF00E676) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Save As (SAF, Internal, or Path)
                IconButton(
                    onClick = {
                        saveAsPathInput = activeDoc?.file?.absolutePath
                            ?: File(Environment.getExternalStorageDirectory(), activeDoc?.title ?: "document.txt").absolutePath
                        internalFileNameInput = activeDoc?.title ?: "document.txt"
                        showSaveAsDialog = true
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("editor_save_as_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAs,
                        contentDescription = "Save As",
                        tint = Color(0xFF00D2FF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Undo
                IconButton(
                    onClick = { viewModel.undo() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Redo
                IconButton(
                    onClick = { viewModel.redo() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Redo,
                        contentDescription = "Redo",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Search & Replace Toggle
                IconButton(
                    onClick = { viewModel.toggleSearch() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FindReplace,
                        contentDescription = "Search & Replace",
                        tint = if (isSearchVisible) Color(0xFF00D2FF) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Language badge
                Text(
                    text = activeDoc?.language?.displayName ?: "Plain Text",
                    color = Color(0xFF7C4DFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Preferences
                IconButton(
                    onClick = { showPreferencesDialog = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Editor Settings",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Expandable Search & Replace Bar
        if (isSearchVisible) {
            Surface(
                color = Color(0xFF1E293B),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Find in document...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = matchCase,
                            onClick = { viewModel.toggleMatchCase() },
                            label = { Text("Aa", fontSize = 11.sp) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterChip(
                            selected = useRegex,
                            onClick = { viewModel.toggleRegex() },
                            label = { Text(".*", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { viewModel.setReplaceQuery(it) },
                            placeholder = { Text("Replace with...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = { viewModel.replaceAll() },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Replace All", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Save Status Notification Banner
        AnimatedVisibility(
            visible = saveStatusMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = Color(0xFF1E293B),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_save_status_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = saveStatusMessage ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearSaveStatusMessage() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Sora Code Editor Canvas (Monospaced, Syntax-Aware, Line Gutter, Accessory Bar)
        SoraCodeEditor(
            text = activeDoc?.content ?: "",
            onTextChange = { viewModel.updateContent(it) },
            language = activeDoc?.language ?: com.molinax.manager.editor.SupportedLanguage.PLAIN_TEXT,
            fontSize = preferences.fontSize,
            showLineNumbers = preferences.showLineNumbers,
            wordWrap = preferences.wordWrap,
            tabSize = preferences.tabSize,
            onFontSizeChange = { newSize ->
                viewModel.setPreferences { it.copy(fontSize = newSize) }
            },
            onWordWrapChange = { newWrap ->
                viewModel.setPreferences { it.copy(wordWrap = newWrap) }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }

    // Save As Dialog (SAF, Internal Storage, Custom Path)
    if (showSaveAsDialog) {
        var selectedMode by remember { mutableStateOf(0) } // 0: SAF, 1: Internal, 2: Manual Path

        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SaveAs,
                        contentDescription = null,
                        tint = Color(0xFF00D2FF),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Document As", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Mode Selector Tabs
                    TabRow(
                        selectedTabIndex = selectedMode,
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFF00D2FF)
                    ) {
                        Tab(
                            selected = selectedMode == 0,
                            onClick = { selectedMode = 0 },
                            text = { Text("SAF Picker", fontSize = 11.sp) }
                        )
                        Tab(
                            selected = selectedMode == 1,
                            onClick = { selectedMode = 1 },
                            text = { Text("Internal", fontSize = 11.sp) }
                        )
                        Tab(
                            selected = selectedMode == 2,
                            onClick = { selectedMode = 2 },
                            text = { Text("Path", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when (selectedMode) {
                        0 -> {
                            // Storage Access Framework Mode
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.FolderSpecial,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Storage Access Framework (SAF)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFF1F5F9)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Uses Android's system document picker to save user-edited files anywhere on internal or external storage (SD cards, Documents, Downloads, USB).",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        lineHeight = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val name = activeDoc?.title ?: "document.txt"
                                            showSaveAsDialog = false
                                            safCreateDocumentLauncher.launch(name)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0284C7)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("dialog_save_saf_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Launch System Storage Picker")
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Internal App Storage Mode
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "App Internal Storage (files/documents)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF1F5F9)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Saves file to the application's isolated sandboxed storage.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = internalFileNameInput,
                                        onValueChange = { internalFileNameInput = it },
                                        label = { Text("File name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("internal_file_name_input")
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            if (internalFileNameInput.isNotBlank()) {
                                                viewModel.saveToInternal(internalFileNameInput.trim()) {
                                                    showSaveAsDialog = false
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("dialog_save_internal_button")
                                    ) {
                                        Text("Save to Internal Storage")
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Direct File Path Mode
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Absolute File Path",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF1F5F9)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Specify exact system file path (for terminal / root usage).",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = saveAsPathInput,
                                        onValueChange = { saveAsPathInput = it },
                                        label = { Text("File path") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("custom_path_input")
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            if (saveAsPathInput.isNotBlank()) {
                                                viewModel.saveAs(File(saveAsPathInput.trim())) {
                                                    showSaveAsDialog = false
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("dialog_save_path_button")
                                    ) {
                                        Text("Save to Path")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveAsDialog = false }) {
                    Text("Close", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // Preferences Dialog
    if (showPreferencesDialog) {
        AlertDialog(
            onDismissRequest = { showPreferencesDialog = false },
            title = { Text("Editor Preferences") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show Line Numbers")
                        Switch(
                            checked = preferences.showLineNumbers,
                            onCheckedChange = { chk ->
                                viewModel.setPreferences { it.copy(showLineNumbers = chk) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Word Wrap")
                        Switch(
                            checked = preferences.wordWrap,
                            onCheckedChange = { chk ->
                                viewModel.setPreferences { it.copy(wordWrap = chk) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Font Size: ${preferences.fontSize}sp")
                    Slider(
                        value = preferences.fontSize.toFloat(),
                        onValueChange = { sz ->
                            viewModel.setPreferences { it.copy(fontSize = sz.toInt()) }
                        },
                        valueRange = 10f..24f,
                        steps = 13
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPreferencesDialog = false }) { Text("Done") }
            }
        )
    }

    // File Browser Sheet for opening project files
    if (showFileBrowserSheet) {
        var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
        val files = remember(currentDir) {
            currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.toList()
                ?: emptyList()
        }

        ModalBottomSheet(
            onDismissRequest = { showFileBrowserSheet = false },
            containerColor = Color(0xFF111827)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Project File Explorer",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currentDir.absolutePath,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (currentDir.parentFile != null) {
                    TextButton(
                        onClick = { currentDir.parentFile?.let { currentDir = it } },
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(".. (Up one level)", fontSize = 12.sp, color = Color(0xFF00D2FF))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    items(files) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (file.isDirectory) {
                                        currentDir = file
                                    } else {
                                        viewModel.openFile(file)
                                        showFileBrowserSheet = false
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (file.isDirectory) Color(0xFFFFB300) else Color(0xFF00D2FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = file.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (!file.isDirectory) {
                                Text(
                                    text = FileUtils.formatFileSize(file.length()),
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
