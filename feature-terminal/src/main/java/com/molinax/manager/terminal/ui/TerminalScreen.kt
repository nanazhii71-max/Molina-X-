package com.molinax.manager.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molinax.manager.terminal.DistroManager
import com.molinax.manager.terminal.TerminalLine
import com.molinax.manager.terminal.TerminalViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsState()
    val activeIndex by viewModel.activeSessionIndex.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val activeSession = viewModel.activeSession()

    val lines = activeSession?.lines?.collectAsState()?.value ?: emptyList()
    val isRunning = activeSession?.isRunning?.collectAsState()?.value ?: false

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var inputCommand by remember { mutableStateOf("") }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var showDistroSheet by remember { mutableStateOf(false) }
    var showSessionsSheet by remember { mutableStateOf(false) }

    // Auto-scroll on new lines
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        // Top Terminal Bar
        Surface(
            color = Color(0xFF111827),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Session dropdown button
                FilterChip(
                    selected = true,
                    onClick = { showSessionsSheet = true },
                    label = {
                        Text(
                            text = activeSession?.title ?: "Terminal",
                            color = Color(0xFF00D2FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isRunning) Color(0xFF00E676) else Color(0xFFFF5252),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Switch session",
                            tint = Color(0xFF00D2FF),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1F2937)
                    ),
                    modifier = Modifier.testTag("terminal_session_chip")
                )

                Spacer(modifier = Modifier.weight(1f))

                // Distro hook button
                IconButton(
                    onClick = { showDistroSheet = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("terminal_distro_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "proot-distro hooks",
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Font size down
                IconButton(
                    onClick = { viewModel.adjustFontSize(-1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom out",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Font size up
                IconButton(
                    onClick = { viewModel.adjustFontSize(1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom in",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Clear screen
                IconButton(
                    onClick = { activeSession?.clearScreen() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Clear",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // New session
                IconButton(
                    onClick = { viewModel.createSession() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("terminal_new_session_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New session",
                        tint = Color(0xFF00D2FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Terminal Output Screen
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("terminal_output_list")
            ) {
                items(lines) { line ->
                    TerminalLineRow(line = line, fontSize = fontSize)
                }
            }
        }

        // Extra Keys Toolbar (Termux pattern)
        Surface(
            color = Color(0xFF111827),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ESC key
                KeyButton(label = "ESC") {
                    viewModel.writeToActive("\u001b")
                }

                // TAB key
                KeyButton(label = "TAB") {
                    viewModel.writeToActive("\t")
                }

                // CTRL toggle
                KeyButton(
                    label = "CTRL",
                    isActive = ctrlActive
                ) {
                    ctrlActive = !ctrlActive
                }

                // ALT toggle
                KeyButton(
                    label = "ALT",
                    isActive = altActive
                ) {
                    altActive = !altActive
                }

                // Ctrl+C
                KeyButton(label = "^C") {
                    activeSession?.sendCtrlC()
                }

                // Ctrl+D
                KeyButton(label = "^D") {
                    activeSession?.sendCtrlD()
                }

                // Symbols
                KeyButton(label = "-") { viewModel.writeToActive("-") }
                KeyButton(label = "/") { viewModel.writeToActive("/") }
                KeyButton(label = "|") { viewModel.writeToActive("|") }
                KeyButton(label = "~") { viewModel.writeToActive("~") }
                KeyButton(label = "$") { viewModel.writeToActive("$") }

                // Arrow keys
                KeyButton(label = "↑") { viewModel.writeToActive("\u001b[A") }
                KeyButton(label = "↓") { viewModel.writeToActive("\u001b[B") }
                KeyButton(label = "←") { viewModel.writeToActive("\u001b[D") }
                KeyButton(label = "→") { viewModel.writeToActive("\u001b[C") }
            }
        }

        // Terminal Command Input Field
        Surface(
            color = Color(0xFF0F172A),
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$",
                    color = Color(0xFF00E676),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                )

                OutlinedTextField(
                    value = inputCommand,
                    onValueChange = { inputCommand = it },
                    placeholder = {
                        Text(
                            text = if (isRunning) "Type shell command..." else "Process terminated. Tap + for new session",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    enabled = isRunning,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00D2FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color(0xFFF1F5F9),
                        unfocusedTextColor = Color(0xFFF1F5F9),
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B)
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputCommand.isNotBlank()) {
                                if (ctrlActive) {
                                    // Handle CTRL combinations
                                    when (inputCommand.lowercase()) {
                                        "c" -> activeSession?.sendCtrlC()
                                        "d" -> activeSession?.sendCtrlD()
                                        "z" -> activeSession?.sendCtrlZ()
                                        "l" -> activeSession?.clearScreen()
                                        else -> viewModel.writeCommandToActive(inputCommand)
                                    }
                                    ctrlActive = false
                                } else {
                                    viewModel.writeCommandToActive(inputCommand)
                                }
                                inputCommand = ""
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_input_field")
                )

                IconButton(
                    onClick = {
                        if (inputCommand.isNotBlank()) {
                            viewModel.writeCommandToActive(inputCommand)
                            inputCommand = ""
                        }
                    },
                    enabled = isRunning && inputCommand.isNotBlank(),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .testTag("terminal_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (inputCommand.isNotBlank()) Color(0xFF00D2FF) else Color(0xFF475569)
                    )
                }
            }
        }
    }

    // Sessions bottom sheet
    if (showSessionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSessionsSheet = false },
            containerColor = Color(0xFF111827)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Terminal Sessions (${sessions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            viewModel.createSession()
                            showSessionsSheet = false
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Session", color = Color(0xFF00D2FF))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                sessions.forEachIndexed { index, session ->
                    val isSelected = index == activeIndex
                    Surface(
                        color = if (isSelected) Color(0xFF1F2937) else Color(0xFF161F30),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                viewModel.selectSession(index)
                                showSessionsSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF00D2FF) else Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title,
                                    color = Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "ID: ${session.id.take(8)} | CWD: ${session.homeDir.name}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.closeSession(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // proot-distro Hook Bottom Sheet
    if (showDistroSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDistroSheet = false },
            containerColor = Color(0xFF111827)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Linux Distributions (proot-distro hook)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Run complete Linux userspace rootfs containers on top of MolinaX PREFIX",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                DistroManager.SUPPORTED_DISTROS.forEach { distro ->
                    val isInstalled = DistroManager.isDistroInstalled(viewModel.prefixDir, distro.id)
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = distro.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = distro.description,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                onClick = {
                                    val cmd = if (isInstalled) {
                                        DistroManager.getDistroLoginCommand(distro.id)
                                    } else {
                                        DistroManager.getDistroInstallCommand(distro.id)
                                    }
                                    viewModel.writeCommandToActive(cmd)
                                    showDistroSheet = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isInstalled) Color(0xFF00E676) else Color(0xFF7C4DFF)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = if (isInstalled) "Login" else "Install",
                                    fontSize = 12.sp,
                                    color = Color.Black
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

@Composable
private fun TerminalLineRow(line: TerminalLine, fontSize: Int) {
    val annotatedString = buildAnnotatedString {
        line.spans.forEach { span ->
            pushStyle(
                SpanStyle(
                    color = span.color,
                    background = span.bgColor,
                    fontWeight = if (span.isBold) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (span.isUnderline) TextDecoration.Underline else null
                )
            )
            append(span.text)
            pop()
        }
    }

    Text(
        text = annotatedString,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize.sp,
        lineHeight = (fontSize + 4).sp
    )
}

@Composable
private fun KeyButton(
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = if (isActive) Color(0xFF00D2FF) else Color(0xFF1E293B),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isActive) Color.Black else Color(0xFFE2E8F0),
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}
