package com.molinax

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.molinax.core.MediaInputPayload
import com.molinax.core.SubsystemDestination
import com.molinax.core.SubsystemNavigator
import com.molinax.editor.EditorViewModel
import com.molinax.editor.ui.EditorScreen
import com.molinax.player.PlayerViewModel
import com.molinax.player.ui.PlayerScreen
import com.molinax.terminal.TerminalViewModel
import com.molinax.terminal.ui.TerminalScreen
import com.molinax.ui.theme.MyApplicationTheme
import com.molinax.utilities.UtilitiesViewModel
import com.molinax.utilities.UtilityTab
import com.molinax.utilities.ui.UtilitiesScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MolinaXMainScreen()
            }
        }
    }
}

enum class NavigationTab(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: SubsystemDestination
) {
    PLAYER("MPV Player", "High-performance video & audio playback", Icons.Default.PlayCircle, SubsystemDestination.Player),
    EDITOR("Sora Code Editor", "Multi-tab IDE with syntax highlighting & SAF", Icons.Default.Code, SubsystemDestination.Editor),
    TERMINAL("Linux Terminal", "Embedded proot shell & development tools", Icons.Default.Terminal, SubsystemDestination.Terminal),
    UTILITIES("System Utilities", "File explorer, network, packages & diagnostics", Icons.Default.Build, SubsystemDestination.Utilities)
}

@Composable
fun MolinaXMainScreen() {
    val terminalViewModel: TerminalViewModel = viewModel()
    val editorViewModel: EditorViewModel = viewModel()

    val runtimeBridge = terminalViewModel.runtimeBridge

    val playerViewModel: PlayerViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                val app = terminalViewModel.getApplication<android.app.Application>()
                return PlayerViewModel(app, runtimeBridge) as T
            }
        }
    )

    val utilitiesViewModel: UtilitiesViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                val app = terminalViewModel.getApplication<android.app.Application>()
                return UtilitiesViewModel(app, runtimeBridge) as T
            }
        }
    )

    var currentTab by remember { mutableStateOf(NavigationTab.PLAYER) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Handle back button to close drawer if open
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    val navigator = remember {
        object : SubsystemNavigator {
            override fun navigateToPlayer(payload: MediaInputPayload?) {
                payload?.let { playerViewModel.handleExternalPayload(it) }
                currentTab = NavigationTab.PLAYER
            }

            override fun navigateToEditor(file: File?) {
                file?.let { editorViewModel.openFile(it) }
                currentTab = NavigationTab.EDITOR
            }

            override fun navigateToTerminal(command: String?, workingDir: File?) {
                if (command != null || workingDir != null) {
                    terminalViewModel.createSession(
                        title = workingDir?.name ?: "Task",
                        initialCommand = command,
                        workingDir = workingDir
                    )
                }
                currentTab = NavigationTab.TERMINAL
            }

            override fun navigateToUtilities(toolTag: String?, initialPath: File?) {
                initialPath?.let { utilitiesViewModel.navigateToDir(it) }
                toolTag?.let { tag ->
                    val targetTab = UtilityTab.entries.firstOrNull { it.name.equals(tag, ignoreCase = true) }
                    targetTab?.let { utilitiesViewModel.selectTab(it) }
                }
                currentTab = NavigationTab.UTILITIES
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        modifier = Modifier.testTag("modal_navigation_drawer"),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0F172A),
                drawerContentColor = Color(0xFFF1F5F9),
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .testTag("modal_drawer_sheet")
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF00D2FF).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF00D2FF), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = Color(0xFF00D2FF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "MolinaX",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Subsystem Hub",
                                    color = Color(0xFF00D2FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Switch smoothly between core development and media environments.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "PRIMARY SUBSYSTEMS",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                // Drawer Navigation Items (Sora Code Editor, MPV Player, Linux Terminal, Utilities)
                NavigationTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    val itemTag = "drawer_item_${tab.name.lowercase()}"
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) Color(0xFF00D2FF) else Color(0xFF94A3B8),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Column {
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) Color(0xFF00D2FF) else Color(0xFFF1F5F9),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                                Text(
                                    text = tab.subtitle,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        },
                        selected = isSelected,
                        onClick = {
                            currentTab = tab
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF1E293B),
                            unselectedContainerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag(itemTag)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)

                // Drawer Footer Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "System Ready • All modules active",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("main_scaffold"),
            topBar = {
                Surface(
                    color = Color(0xFF0F172A),
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            },
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Navigation Drawer",
                                tint = Color(0xFF00D2FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = currentTab.icon,
                            contentDescription = null,
                            tint = Color(0xFF00D2FF),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTab.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Molina-X",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }

                        // Quick action indicator
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E293B)
                        ) {
                            Text(
                                text = currentTab.name,
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF111827),
                    contentColor = Color(0xFF00D2FF),
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title.replace("Sora Code ", "").replace("MPV ", "").replace("Linux ", "").replace("System ", ""),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF00D2FF),
                                selectedTextColor = Color(0xFF00D2FF),
                                indicatorColor = Color(0xFF1F2937),
                                unselectedIconColor = Color(0xFF94A3B8),
                                unselectedTextColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF090D16))
            ) {
                when (currentTab) {
                    NavigationTab.PLAYER -> PlayerScreen(
                        viewModel = playerViewModel,
                        modifier = Modifier.testTag("screen_player")
                    )
                    NavigationTab.EDITOR -> EditorScreen(
                        viewModel = editorViewModel,
                        modifier = Modifier.testTag("screen_editor")
                    )
                    NavigationTab.TERMINAL -> TerminalScreen(
                        viewModel = terminalViewModel,
                        modifier = Modifier.testTag("screen_terminal")
                    )
                    NavigationTab.UTILITIES -> UtilitiesScreen(
                        viewModel = utilitiesViewModel,
                        navigator = navigator,
                        modifier = Modifier.testTag("screen_utilities")
                    )
                }
            }
        }
    }
}

