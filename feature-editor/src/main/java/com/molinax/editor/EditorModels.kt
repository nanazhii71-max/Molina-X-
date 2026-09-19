package com.molinax.editor

import android.net.Uri
import java.io.File
import java.nio.charset.Charset

enum class SupportedLanguage(val displayName: String, val extensions: List<String>, val defaultMimeType: String = "text/plain") {
    KOTLIN("Kotlin", listOf("kt", "kts"), "text/x-kotlin"),
    JAVA("Java", listOf("java"), "text/x-java-source"),
    PYTHON("Python", listOf("py"), "text/x-python"),
    SHELL("Bash / Shell", listOf("sh", "bash", "zsh"), "text/x-shellscript"),
    C_CPP("C / C++", listOf("c", "cpp", "h", "hpp"), "text/x-c"),
    JSON("JSON", listOf("json"), "application/json"),
    XML_HTML("XML / HTML", listOf("xml", "html", "htm", "svg"), "text/html"),
    MARKDOWN("Markdown", listOf("md", "markdown"), "text/markdown"),
    PLAIN_TEXT("Plain Text", listOf("txt", "log", "conf", "env"), "text/plain");

    companion object {
        fun fromFile(file: File?): SupportedLanguage {
            if (file == null) return PLAIN_TEXT
            return fromName(file.name)
        }

        fun fromName(name: String): SupportedLanguage {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return PLAIN_TEXT
            return entries.firstOrNull { it.extensions.contains(ext) } ?: PLAIN_TEXT
        }

        fun mimeTypeForName(name: String): String {
            val lang = fromName(name)
            return lang.defaultMimeType
        }
    }
}

data class EditorDocument(
    val id: String,
    val file: File? = null,
    val uri: Uri? = null,
    val title: String,
    val content: String = "",
    val isDirty: Boolean = false,
    val encoding: Charset = Charsets.UTF_8,
    val language: SupportedLanguage = SupportedLanguage.PLAIN_TEXT
) {
    val isSaved: Boolean get() = file != null || uri != null
    val isUriBacked: Boolean get() = uri != null
    val isFileBacked: Boolean get() = file != null
}

data class EditorPreferences(
    val fontSize: Int = 13,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val tabSize: Int = 4,
    val autoIndent: Boolean = true
)
