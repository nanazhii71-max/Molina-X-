package com.molinax.utilities

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.molinax.core.FileUtils
import com.molinax.core.RuntimeExecutionBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class UtilityTab(val title: String) {
    FILES("File Tools"),
    PACKAGES("Packages"),
    ARCHIVE("Archives"),
    ENCODING("Encoders"),
    SETTINGS("Settings")
}

class UtilitiesViewModel(
    application: Application,
    private val runtimeBridge: RuntimeExecutionBridge
) : AndroidViewModel(application) {

    val packageManager = PackageManagerManager(runtimeBridge)

    private val _activeTab = MutableStateFlow(UtilityTab.FILES)
    val activeTab: StateFlow<UtilityTab> = _activeTab.asStateFlow()

    // File Tools State
    private val _currentDir = MutableStateFlow<File>(
        Environment.getExternalStorageDirectory() ?: application.filesDir
    )
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    private val _fileList = MutableStateFlow<List<File>>(emptyList())
    val fileList: StateFlow<List<File>> = _fileList.asStateFlow()

    private val _selectedFile = MutableStateFlow<File?>(null)
    val selectedFile: StateFlow<File?> = _selectedFile.asStateFlow()

    private val _checksumResult = MutableStateFlow<String?>(null)
    val checksumResult: StateFlow<String?> = _checksumResult.asStateFlow()

    // Encoding State
    private val _encodeInput = MutableStateFlow("")
    val encodeInput: StateFlow<String> = _encodeInput.asStateFlow()

    private val _encodeOutput = MutableStateFlow("")
    val encodeOutput: StateFlow<String> = _encodeOutput.asStateFlow()

    init {
        refreshFiles()
    }

    fun selectTab(tab: UtilityTab) {
        _activeTab.value = tab
    }

    fun navigateToDir(dir: File) {
        if (dir.exists() && dir.isDirectory) {
            _currentDir.value = dir
            refreshFiles()
        }
    }

    fun navigateUp() {
        val parent = _currentDir.value.parentFile
        if (parent != null && parent.canRead()) {
            _currentDir.value = parent
            refreshFiles()
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
            _fileList.value = FileToolsManager.listFiles(_currentDir.value)
        }
    }

    fun selectFile(file: File?) {
        _selectedFile.value = file
        _checksumResult.value = null
    }

    fun deleteSelected(file: File) {
        viewModelScope.launch {
            FileToolsManager.deleteFile(file)
            selectFile(null)
            refreshFiles()
        }
    }

    fun renameSelected(file: File, newName: String) {
        viewModelScope.launch {
            FileToolsManager.renameFile(file, newName)
            selectFile(null)
            refreshFiles()
        }
    }

    fun computeChecksum(file: File, algorithm: String = "SHA-256") {
        viewModelScope.launch {
            _checksumResult.value = "Calculating..."
            _checksumResult.value = FileToolsManager.computeChecksum(file, algorithm)
        }
    }

    fun toggleExecutable(file: File, isExecutable: Boolean) {
        viewModelScope.launch {
            FileToolsManager.makeExecutable(file, isExecutable)
            refreshFiles()
        }
    }

    fun setEncodeInput(text: String) {
        _encodeInput.value = text
    }

    fun runEncoding(type: String) {
        val input = _encodeInput.value
        _encodeOutput.value = when (type) {
            "b64_enc" -> EncodingManager.base64Encode(input)
            "b64_dec" -> EncodingManager.base64Decode(input)
            "url_enc" -> EncodingManager.urlEncode(input)
            "url_dec" -> EncodingManager.urlDecode(input)
            "hex" -> EncodingManager.hexDump(input)
            "json" -> EncodingManager.formatJson(input)
            else -> input
        }
    }
}
