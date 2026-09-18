package com.molinax.manager.core

import java.io.File

/**
 * Inter-subsystem navigation contracts.
 * App Host handles routing between Player, Editor, Terminal, and Utilities without
 * direct coupling between the feature modules.
 */
sealed class SubsystemDestination(val route: String) {
    data object Player : SubsystemDestination("player")
    data object Editor : SubsystemDestination("editor")
    data object Terminal : SubsystemDestination("terminal")
    data object Utilities : SubsystemDestination("utilities")
}

sealed class MediaInputPayload {
    data class LocalFile(val file: File) : MediaInputPayload()
    data class TitleQuery(val title: String) : MediaInputPayload()
    data class UniversalUrl(val url: String) : MediaInputPayload()
}

interface SubsystemNavigator {
    fun navigateToPlayer(payload: MediaInputPayload? = null)
    fun navigateToEditor(file: File? = null)
    fun navigateToTerminal(command: String? = null, workingDir: File? = null)
    fun navigateToUtilities(toolTag: String? = null, initialPath: File? = null)
}
