package com.molinax.manager.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.molinax.manager.editor.EditorHost
import com.molinax.manager.player.PlayerHost
import com.molinax.manager.terminal.TerminalHost
import com.molinax.manager.utilities.UtilitiesHost
import kotlinx.serialization.Serializable

@Serializable
sealed interface MolinaXTab : NavKey {
    @Serializable data object Player : MolinaXTab
    @Serializable data object Editor : MolinaXTab
    @Serializable data object Terminal : MolinaXTab
    @Serializable data object Utilities : MolinaXTab
}

private data class TabItem(
    val key: MolinaXTab,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val tabs = listOf(
    TabItem(MolinaXTab.Player, "Player", Icons.Filled.PlayArrow),
    TabItem(MolinaXTab.Editor, "Editor", Icons.Filled.Edit),
    TabItem(MolinaXTab.Terminal, "Terminal", Icons.Filled.Home),
    TabItem(MolinaXTab.Utilities, "Utilities", Icons.Filled.Build)
)

@Composable
fun NavigationHost() {
    val backStack = remember { mutableStateListOf<MolinaXTab>(MolinaXTab.Player) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val current = backStack.lastOrNull()
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.key,
                        onClick = {
                            if (backStack.lastOrNull() != tab.key) {
                                backStack.clear()
                                backStack.add(tab.key)
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            entryProvider = entryProvider {
                entry<MolinaXTab.Player> { PlayerHost() }
                entry<MolinaXTab.Editor> { EditorHost() }
                entry<MolinaXTab.Terminal> { TerminalHost() }
                entry<MolinaXTab.Utilities> { UtilitiesHost() }
            }
        )
    }
}
