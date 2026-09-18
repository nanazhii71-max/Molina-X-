package com.molinax.manager.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class SyntaxHighlightTransformation(
    private val language: SupportedLanguage
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = SyntaxHighlighter.highlight(text.text, language)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

object SyntaxHighlighter {

    private val KEYWORD_COLOR = Color(0xFF7C4DFF)       // Electric Violet
    private val STRING_COLOR = Color(0xFF00E676)        // Emerald Green
    private val COMMENT_COLOR = Color(0xFF64748B)       // Slate Gray
    private val NUMBER_COLOR = Color(0xFFFFB300)        // Amber
    private val ANNOTATION_COLOR = Color(0xFF00D2FF)    // Cyan
    private val DEFAULT_TEXT_COLOR = Color(0xFFF1F5F9)  // Off-white

    private val KOTLIN_JAVA_KEYWORDS = setOf(
        "package", "import", "class", "interface", "fun", "val", "var", "override",
        "private", "public", "protected", "internal", "object", "companion", "data",
        "sealed", "enum", "if", "else", "when", "for", "while", "return", "true",
        "false", "null", "this", "super", "try", "catch", "finally", "throw", "suspend",
        "abstract", "static", "final", "void", "new", "switch", "case", "default"
    )

    private val PYTHON_KEYWORDS = setOf(
        "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while",
        "return", "yield", "try", "except", "finally", "raise", "with", "True", "False",
        "None", "lambda", "global", "nonlocal", "pass", "break", "continue", "async", "await"
    )

    private val SHELL_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until",
        "do", "done", "in", "function", "select", "time", "return", "exit", "echo", "export"
    )

    private val C_CPP_KEYWORDS = setOf(
        "int", "char", "float", "double", "void", "struct", "class", "enum", "union",
        "const", "static", "unsigned", "signed", "auto", "register", "volatile", "extern",
        "return", "sizeof", "if", "else", "for", "while", "do", "switch", "case", "default",
        "break", "continue", "goto", "typedef", "include", "define", "ifndef", "endif"
    )

    fun highlight(text: String, language: SupportedLanguage): AnnotatedString {
        if (language == SupportedLanguage.PLAIN_TEXT || text.length > 50_000) {
            return AnnotatedString(text)
        }

        val keywords = when (language) {
            SupportedLanguage.KOTLIN, SupportedLanguage.JAVA -> KOTLIN_JAVA_KEYWORDS
            SupportedLanguage.PYTHON -> PYTHON_KEYWORDS
            SupportedLanguage.SHELL -> SHELL_KEYWORDS
            SupportedLanguage.C_CPP -> C_CPP_KEYWORDS
            else -> emptySet()
        }

        return buildAnnotatedString {
            append(text)
            val len = text.length
            var i = 0

            while (i < len) {
                val c = text[i]

                // Line comment check (// or #)
                if ((c == '/' && i + 1 < len && text[i + 1] == '/') ||
                    (c == '#' && (language == SupportedLanguage.PYTHON || language == SupportedLanguage.SHELL || language == SupportedLanguage.MARKDOWN))
                ) {
                    val start = i
                    while (i < len && text[i] != '\n') i++
                    addStyle(SpanStyle(color = COMMENT_COLOR), start, i)
                    continue
                }

                // Block comment (/* ... */)
                if (c == '/' && i + 1 < len && text[i + 1] == '*') {
                    val start = i
                    i += 2
                    while (i + 1 < len && !(text[i] == '*' && text[i + 1] == '/')) i++
                    if (i + 1 < len) i += 2
                    addStyle(SpanStyle(color = COMMENT_COLOR), start, i)
                    continue
                }

                // Strings ("..." or '...')
                if (c == '"' || c == '\'') {
                    val quote = c
                    val start = i
                    i++
                    while (i < len && text[i] != quote && text[i] != '\n') {
                        if (text[i] == '\\' && i + 1 < len) i++ // skip escaped
                        i++
                    }
                    if (i < len && text[i] == quote) i++
                    addStyle(SpanStyle(color = STRING_COLOR), start, i)
                    continue
                }

                // Annotations (@Name)
                if (c == '@' && i + 1 < len && text[i + 1].isLetter()) {
                    val start = i
                    i++
                    while (i < len && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                    addStyle(SpanStyle(color = ANNOTATION_COLOR, fontWeight = FontWeight.Bold), start, i)
                    continue
                }

                // Numbers
                if (c.isDigit()) {
                    val start = i
                    while (i < len && (text[i].isDigit() || text[i] == '.' || text[i] in "xXfFlL")) i++
                    addStyle(SpanStyle(color = NUMBER_COLOR), start, i)
                    continue
                }

                // Words / Identifiers
                if (c.isLetter() || c == '_') {
                    val start = i
                    while (i < len && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                    val word = text.substring(start, i)
                    if (keywords.contains(word)) {
                        addStyle(SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.Bold), start, i)
                    }
                    continue
                }

                i++
            }
        }
    }
}
