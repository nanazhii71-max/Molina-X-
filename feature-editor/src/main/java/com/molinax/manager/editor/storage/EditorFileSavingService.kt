package com.molinax.manager.editor.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import com.molinax.manager.editor.EditorDocument
import com.molinax.manager.editor.SupportedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.charset.Charset

/**
 * Storage destination type where the document was saved.
 */
enum class StorageType {
    SAF_EXTERNAL,
    INTERNAL_STORAGE,
    EXTERNAL_APP_STORAGE,
    DIRECT_FILE
}

/**
 * Detailed result metadata returned after a document save operation.
 */
data class SaveResult(
    val title: String,
    val targetUriOrPath: String,
    val bytesWritten: Long,
    val storageType: StorageType,
    val savedDocument: EditorDocument
)

/**
 * Interface contract for saving user-edited files from Sora Code Editor
 * to internal or external storage via Storage Access Framework (SAF) and local storage.
 */
interface EditorFileSavingService {

    /**
     * Saves an existing document to its bound Uri or File.
     */
    suspend fun saveDocument(document: EditorDocument): Result<SaveResult>

    /**
     * Saves document content to a Storage Access Framework Uri (obtained via ACTION_CREATE_DOCUMENT).
     */
    suspend fun saveToUri(document: EditorDocument, targetUri: Uri): Result<SaveResult>

    /**
     * Saves document content to a specific File on internal or external storage.
     */
    suspend fun saveToFile(document: EditorDocument, targetFile: File): Result<SaveResult>

    /**
     * Saves document to the application's secure internal storage directory.
     */
    suspend fun saveToInternalStorage(
        document: EditorDocument,
        fileName: String,
        subDir: String = "documents"
    ): Result<SaveResult>

    /**
     * Saves document to the application's external documents directory.
     */
    suspend fun saveToExternalStorage(
        document: EditorDocument,
        fileName: String,
        subDir: String = "documents"
    ): Result<SaveResult>

    /**
     * Resolves the display name of a SAF content Uri.
     */
    fun resolveUriDisplayName(uri: Uri): String?

    /**
     * Recommends the appropriate MIME type for the document for SAF file creation.
     */
    fun getSuggestedMimeType(document: EditorDocument): String
}

/**
 * Production implementation of [EditorFileSavingService] leveraging Android's
 * Storage Access Framework (SAF) and internal/external storage APIs.
 */
class StorageAccessFrameworkService(
    private val context: Context
) : EditorFileSavingService {

    private val tag = "SAFService"

    override suspend fun saveDocument(document: EditorDocument): Result<SaveResult> =
        withContext(Dispatchers.IO) {
            when {
                document.uri != null -> saveToUri(document, document.uri)
                document.file != null -> saveToFile(document, document.file)
                else -> Result.failure(
                    IllegalArgumentException("Document has no target Uri or File. Use Storage Access Framework (saveToUri) or saveToFile.")
                )
            }
        }

    override suspend fun saveToUri(document: EditorDocument, targetUri: Uri): Result<SaveResult> =
        withContext(Dispatchers.IO) {
            try {
                // Attempt to persist URI permissions if supported
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(targetUri, takeFlags)
                } catch (e: Exception) {
                    // Not all URIs support persistable grants (e.g. some third-party file pickers or transient grants)
                    Log.d(tag, "Persistable permission not applied for $targetUri: ${e.message}")
                }

                // Query display name from SAF provider
                val displayName = resolveUriDisplayName(targetUri) ?: document.title

                // Write text content with specified encoding
                val bytes = document.content.toByteArray(document.encoding)
                val outputStream = context.contentResolver.openOutputStream(targetUri, "wt")
                    ?: context.contentResolver.openOutputStream(targetUri, "w")
                    ?: context.contentResolver.openOutputStream(targetUri)
                    ?: throw IOException("Cannot open output stream for SAF Uri: $targetUri")

                outputStream.use { stream ->
                    stream.write(bytes)
                    stream.flush()
                }

                val language = SupportedLanguage.fromName(displayName)
                val updatedDocument = document.copy(
                    uri = targetUri,
                    file = null, // Document is now SAF Uri-backed
                    title = displayName,
                    isDirty = false,
                    language = language
                )

                Log.i(tag, "Successfully saved ${bytes.size} bytes to SAF Uri: $targetUri ($displayName)")

                Result.success(
                    SaveResult(
                        title = displayName,
                        targetUriOrPath = targetUri.toString(),
                        bytesWritten = bytes.size.toLong(),
                        storageType = StorageType.SAF_EXTERNAL,
                        savedDocument = updatedDocument
                    )
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to save document to SAF Uri $targetUri: ${e.message}", e)
                Result.failure(e)
            }
        }

    override suspend fun saveToFile(document: EditorDocument, targetFile: File): Result<SaveResult> =
        withContext(Dispatchers.IO) {
            try {
                targetFile.parentFile?.mkdirs()
                val bytes = document.content.toByteArray(document.encoding)
                targetFile.writeBytes(bytes)

                val language = SupportedLanguage.fromFile(targetFile)
                val updatedDocument = document.copy(
                    file = targetFile,
                    uri = null,
                    title = targetFile.name,
                    isDirty = false,
                    language = language
                )

                val isExternal = targetFile.absolutePath.startsWith(
                    context.getExternalFilesDir(null)?.absolutePath ?: "/storage"
                )

                val storageType = if (isExternal) {
                    StorageType.EXTERNAL_APP_STORAGE
                } else if (targetFile.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    StorageType.INTERNAL_STORAGE
                } else {
                    StorageType.DIRECT_FILE
                }

                Log.i(tag, "Successfully saved ${bytes.size} bytes to file: ${targetFile.absolutePath}")

                Result.success(
                    SaveResult(
                        title = targetFile.name,
                        targetUriOrPath = targetFile.absolutePath,
                        bytesWritten = bytes.size.toLong(),
                        storageType = storageType,
                        savedDocument = updatedDocument
                    )
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to save document to file ${targetFile.absolutePath}: ${e.message}", e)
                Result.failure(e)
            }
        }

    override suspend fun saveToInternalStorage(
        document: EditorDocument,
        fileName: String,
        subDir: String
    ): Result<SaveResult> = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, subDir).apply { mkdirs() }
        val targetFile = File(targetDir, fileName)
        saveToFile(document, targetFile)
    }

    override suspend fun saveToExternalStorage(
        document: EditorDocument,
        fileName: String,
        subDir: String
    ): Result<SaveResult> = withContext(Dispatchers.IO) {
        val baseExternalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.getExternalFilesDir(null)
            ?: File(context.filesDir, "external_fallback")

        val targetDir = File(baseExternalDir, subDir).apply { mkdirs() }
        val targetFile = File(targetDir, fileName)
        saveToFile(document, targetFile)
    }

    override fun resolveUriDisplayName(uri: Uri): String? {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not resolve display name for uri: $uri, error=${e.message}")
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    override fun getSuggestedMimeType(document: EditorDocument): String {
        return document.language.defaultMimeType
    }
}
