package com.molinax.manager.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.molinax.manager.editor.storage.StorageAccessFrameworkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.UUID

object DocumentManager {

    suspend fun openFile(file: File, charset: Charset = Charsets.UTF_8): Result<EditorDocument> =
        withContext(Dispatchers.IO) {
            try {
                val text = file.readText(charset)
                val lang = SupportedLanguage.fromFile(file)
                Result.success(
                    EditorDocument(
                        id = UUID.randomUUID().toString(),
                        file = file,
                        uri = null,
                        title = file.name,
                        content = text,
                        isDirty = false,
                        encoding = charset,
                        language = lang
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun openUri(context: Context, uri: Uri, charset: Charset = Charsets.UTF_8): Result<EditorDocument> =
        withContext(Dispatchers.IO) {
            try {
                var displayName = "document.txt"
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) {
                            val name = cursor.getString(nameIdx)
                            if (!name.isNullOrBlank()) displayName = name
                        }
                    }
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Cannot open input stream for Uri: $uri")
                val text = inputStream.use { it.bufferedReader(charset).readText() }
                val lang = SupportedLanguage.fromName(displayName)

                Result.success(
                    EditorDocument(
                        id = UUID.randomUUID().toString(),
                        file = null,
                        uri = uri,
                        title = displayName,
                        content = text,
                        isDirty = false,
                        encoding = charset,
                        language = lang
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun saveDocument(context: Context, document: EditorDocument): Result<EditorDocument> =
        withContext(Dispatchers.IO) {
            val service = StorageAccessFrameworkService(context)
            service.saveDocument(document).map { it.savedDocument }
        }

    suspend fun saveDocument(document: EditorDocument): Result<EditorDocument> =
        withContext(Dispatchers.IO) {
            val targetFile = document.file ?: return@withContext Result.failure(
                IllegalArgumentException("Cannot save without target file. Use saveAs instead.")
            )
            try {
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(document.content, document.encoding)
                Result.success(document.copy(isDirty = false))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun saveAs(document: EditorDocument, targetFile: File): Result<EditorDocument> =
        withContext(Dispatchers.IO) {
            try {
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(document.content, document.encoding)
                val lang = SupportedLanguage.fromFile(targetFile)
                Result.success(
                    document.copy(
                        file = targetFile,
                        uri = null,
                        title = targetFile.name,
                        isDirty = false,
                        language = lang
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun saveToUri(context: Context, document: EditorDocument, uri: Uri): Result<EditorDocument> =
        withContext(Dispatchers.IO) {
            val service = StorageAccessFrameworkService(context)
            service.saveToUri(document, uri).map { it.savedDocument }
        }

    fun createNewDocument(title: String = "Untitled", initialContent: String = ""): EditorDocument {
        return EditorDocument(
            id = UUID.randomUUID().toString(),
            file = null,
            uri = null,
            title = title,
            content = initialContent,
            isDirty = false,
            encoding = Charsets.UTF_8,
            language = SupportedLanguage.fromName(title)
        )
    }
}
