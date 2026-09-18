package com.molinax.manager.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.molinax.manager.editor.storage.EditorFileSavingService
import com.molinax.manager.editor.storage.StorageAccessFrameworkService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Stack

class EditorViewModel @JvmOverloads constructor(
    application: Application,
    val fileSavingService: EditorFileSavingService = StorageAccessFrameworkService(application)
) : AndroidViewModel(application) {

    private val _documents = MutableStateFlow<List<EditorDocument>>(emptyList())
    val documents: StateFlow<List<EditorDocument>> = _documents.asStateFlow()

    private val _activeDocIndex = MutableStateFlow(0)
    val activeDocIndex: StateFlow<Int> = _activeDocIndex.asStateFlow()

    private val _preferences = MutableStateFlow(EditorPreferences())
    val preferences: StateFlow<EditorPreferences> = _preferences.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _replaceQuery = MutableStateFlow("")
    val replaceQuery: StateFlow<String> = _replaceQuery.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _matchCase = MutableStateFlow(false)
    val matchCase: StateFlow<Boolean> = _matchCase.asStateFlow()

    private val _useRegex = MutableStateFlow(false)
    val useRegex: StateFlow<Boolean> = _useRegex.asStateFlow()

    private val _saveStatusMessage = MutableStateFlow<String?>(null)
    val saveStatusMessage: StateFlow<String?> = _saveStatusMessage.asStateFlow()

    private val undoStack = mutableMapOf<String, Stack<String>>()
    private val redoStack = mutableMapOf<String, Stack<String>>()

    init {
        // Initial blank document
        val initialDoc = DocumentManager.createNewDocument("untitled-1.txt", "// Welcome to MolinaX Editor\n\nfun main() {\n    println(\"Hello, MolinaX!\")\n}\n")
        _documents.value = listOf(initialDoc)
    }

    fun activeDocument(): EditorDocument? {
        val list = _documents.value
        val idx = _activeDocIndex.value
        return if (idx in list.indices) list[idx] else null
    }

    fun selectTab(index: Int) {
        if (index in _documents.value.indices) {
            _activeDocIndex.value = index
        }
    }

    fun newTab(title: String = "Untitled") {
        val count = _documents.value.size + 1
        val newDoc = DocumentManager.createNewDocument("$title-$count.txt")
        val current = _documents.value.toMutableList()
        current.add(newDoc)
        _documents.value = current
        _activeDocIndex.value = current.size - 1
    }

    fun closeTab(index: Int) {
        val current = _documents.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            if (current.isEmpty()) {
                current.add(DocumentManager.createNewDocument("untitled-1.txt"))
            }
            _documents.value = current
            _activeDocIndex.value = _activeDocIndex.value.coerceIn(0, current.size - 1)
        }
    }

    fun openFile(file: File) {
        // Check if already open
        val existingIdx = _documents.value.indexOfFirst { it.file?.absolutePath == file.absolutePath }
        if (existingIdx != -1) {
            _activeDocIndex.value = existingIdx
            return
        }

        viewModelScope.launch {
            DocumentManager.openFile(file).onSuccess { doc ->
                val current = _documents.value.toMutableList()
                current.add(doc)
                _documents.value = current
                _activeDocIndex.value = current.size - 1
            }
        }
    }

    fun openUri(uri: Uri) {
        val existingIdx = _documents.value.indexOfFirst { it.uri == uri }
        if (existingIdx != -1) {
            _activeDocIndex.value = existingIdx
            return
        }

        viewModelScope.launch {
            DocumentManager.openUri(getApplication(), uri).onSuccess { doc ->
                val current = _documents.value.toMutableList()
                current.add(doc)
                _documents.value = current
                _activeDocIndex.value = current.size - 1
            }
        }
    }

    fun updateContent(newContent: String) {
        val currentDoc = activeDocument() ?: return
        if (currentDoc.content == newContent) return

        // Push to undo stack
        val docUndo = undoStack.getOrPut(currentDoc.id) { Stack() }
        docUndo.push(currentDoc.content)

        val updatedDoc = currentDoc.copy(content = newContent, isDirty = true)
        updateActiveDocument(updatedDoc)
    }

    fun undo() {
        val currentDoc = activeDocument() ?: return
        val docUndo = undoStack[currentDoc.id] ?: return
        if (docUndo.isNotEmpty()) {
            val previousContent = docUndo.pop()
            val docRedo = redoStack.getOrPut(currentDoc.id) { Stack() }
            docRedo.push(currentDoc.content)

            updateActiveDocument(currentDoc.copy(content = previousContent, isDirty = true))
        }
    }

    fun redo() {
        val currentDoc = activeDocument() ?: return
        val docRedo = redoStack[currentDoc.id] ?: return
        if (docRedo.isNotEmpty()) {
            val nextContent = docRedo.pop()
            val docUndo = undoStack.getOrPut(currentDoc.id) { Stack() }
            docUndo.push(currentDoc.content)

            updateActiveDocument(currentDoc.copy(content = nextContent, isDirty = true))
        }
    }

    fun saveActiveDocument(
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val currentDoc = activeDocument() ?: return
        viewModelScope.launch {
            fileSavingService.saveDocument(currentDoc)
                .onSuccess { result ->
                    updateActiveDocument(result.savedDocument)
                    _saveStatusMessage.value = "Saved ${result.title} (${result.bytesWritten} bytes)"
                    onSuccess?.invoke()
                }
                .onFailure { error ->
                    _saveStatusMessage.value = "Failed to save: ${error.message}"
                    onError?.invoke(error)
                }
        }
    }

    fun saveAsUri(
        targetUri: Uri,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val currentDoc = activeDocument() ?: return
        viewModelScope.launch {
            fileSavingService.saveToUri(currentDoc, targetUri)
                .onSuccess { result ->
                    updateActiveDocument(result.savedDocument)
                    _saveStatusMessage.value = "Saved via Storage Access Framework: ${result.title}"
                    onSuccess?.invoke()
                }
                .onFailure { error ->
                    _saveStatusMessage.value = "SAF Save failed: ${error.message}"
                    onError?.invoke(error)
                }
        }
    }

    fun saveAs(
        targetFile: File,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val currentDoc = activeDocument() ?: return
        viewModelScope.launch {
            fileSavingService.saveToFile(currentDoc, targetFile)
                .onSuccess { result ->
                    updateActiveDocument(result.savedDocument)
                    _saveStatusMessage.value = "Saved to file: ${result.title}"
                    onSuccess?.invoke()
                }
                .onFailure { error ->
                    _saveStatusMessage.value = "Failed to save to ${targetFile.name}: ${error.message}"
                    onError?.invoke(error)
                }
        }
    }

    fun saveToInternal(
        fileName: String,
        subDir: String = "documents",
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val currentDoc = activeDocument() ?: return
        viewModelScope.launch {
            fileSavingService.saveToInternalStorage(currentDoc, fileName, subDir)
                .onSuccess { result ->
                    updateActiveDocument(result.savedDocument)
                    _saveStatusMessage.value = "Saved to internal storage: ${result.title}"
                    onSuccess?.invoke()
                }
                .onFailure { error ->
                    _saveStatusMessage.value = "Internal save failed: ${error.message}"
                    onError?.invoke(error)
                }
        }
    }

    fun saveToExternal(
        fileName: String,
        subDir: String = "documents",
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val currentDoc = activeDocument() ?: return
        viewModelScope.launch {
            fileSavingService.saveToExternalStorage(currentDoc, fileName, subDir)
                .onSuccess { result ->
                    updateActiveDocument(result.savedDocument)
                    _saveStatusMessage.value = "Saved to external storage: ${result.title}"
                    onSuccess?.invoke()
                }
                .onFailure { error ->
                    _saveStatusMessage.value = "External save failed: ${error.message}"
                    onError?.invoke(error)
                }
        }
    }

    fun clearSaveStatusMessage() {
        _saveStatusMessage.value = null
    }

    fun getSuggestedMimeType(document: EditorDocument): String {
        return fileSavingService.getSuggestedMimeType(document)
    }

    fun toggleSearch() {
        _isSearchVisible.value = !_isSearchVisible.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setReplaceQuery(query: String) {
        _replaceQuery.value = query
    }

    fun toggleMatchCase() {
        _matchCase.value = !_matchCase.value
    }

    fun toggleRegex() {
        _useRegex.value = !_useRegex.value
    }

    fun replaceAll() {
        val currentDoc = activeDocument() ?: return
        val query = _searchQuery.value
        val replacement = _replaceQuery.value
        if (query.isEmpty()) return

        val newContent = try {
            if (_useRegex.value) {
                val options = if (_matchCase.value) emptySet() else setOf(RegexOption.IGNORE_CASE)
                currentDoc.content.replace(Regex(query, options), replacement)
            } else {
                currentDoc.content.replace(query, replacement, ignoreCase = !_matchCase.value)
            }
        } catch (e: Exception) {
            currentDoc.content
        }

        updateContent(newContent)
    }

    fun setPreferences(transform: (EditorPreferences) -> EditorPreferences) {
        _preferences.value = transform(_preferences.value)
    }

    private fun updateActiveDocument(doc: EditorDocument) {
        val current = _documents.value.toMutableList()
        val idx = _activeDocIndex.value
        if (idx in current.indices) {
            current[idx] = doc
            _documents.value = current
        }
    }
}
