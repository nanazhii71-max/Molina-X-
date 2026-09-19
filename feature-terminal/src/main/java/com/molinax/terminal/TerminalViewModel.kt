package com.molinax.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    val prefixDir: File = File(application.filesDir, "usr")
    val homeDir: File = File(application.filesDir, "home")

    val runtimeBridge: TerminalRuntimeBridge = TerminalRuntimeBridge(prefixDir, homeDir)

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeSessionIndex = MutableStateFlow(0)
    val activeSessionIndex: StateFlow<Int> = _activeSessionIndex.asStateFlow()

    private val _fontSize = MutableStateFlow(13)
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    init {
        // Ensure paths exist
        if (!prefixDir.exists()) prefixDir.mkdirs()
        if (!homeDir.exists()) homeDir.mkdirs()
        File(prefixDir, "tmp").mkdirs()
        File(prefixDir, "bin").mkdirs()

        // Create initial terminal session
        createSession("Session 1")
    }

    fun createSession(title: String? = null, initialCommand: String? = null, workingDir: File? = null) {
        val count = _sessions.value.size + 1
        val sessionTitle = title ?: "Session $count"
        val session = TerminalSession(
            id = UUID.randomUUID().toString(),
            title = sessionTitle,
            prefixDir = prefixDir,
            homeDir = homeDir,
            initialDir = workingDir,
            initialCommand = initialCommand,
            scope = viewModelScope
        )
        val current = _sessions.value.toMutableList()
        current.add(session)
        _sessions.value = current
        _activeSessionIndex.value = current.size - 1

        // Start foreground service to protect background execution
        TerminalService.start(getApplication())
    }

    fun selectSession(index: Int) {
        if (index in _sessions.value.indices) {
            _activeSessionIndex.value = index
        }
    }

    fun closeSession(index: Int) {
        val current = _sessions.value.toMutableList()
        if (index in current.indices) {
            val session = current.removeAt(index)
            session.destroy()
            _sessions.value = current
            if (current.isEmpty()) {
                createSession("Session 1")
            } else {
                _activeSessionIndex.value = _activeSessionIndex.value.coerceIn(0, current.size - 1)
            }
        }
    }

    fun activeSession(): TerminalSession? {
        val list = _sessions.value
        val idx = _activeSessionIndex.value
        return if (idx in list.indices) list[idx] else null
    }

    fun writeToActive(text: String) {
        activeSession()?.write(text)
    }

    fun writeCommandToActive(command: String) {
        activeSession()?.writeCommand(command)
    }

    fun adjustFontSize(delta: Int) {
        _fontSize.value = (_fontSize.value + delta).coerceIn(10, 24)
    }

    override fun onCleared() {
        super.onCleared()
        _sessions.value.forEach { it.destroy() }
    }
}
