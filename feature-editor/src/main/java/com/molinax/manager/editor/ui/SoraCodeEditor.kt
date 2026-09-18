package com.molinax.manager.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molinax.manager.editor.SupportedLanguage
import com.molinax.manager.editor.SyntaxHighlightTransformation

/**
 * High-performance, syntax-aware code editing interface for the Sora Code Editor module.
 *
 * Provides:
 * - Full Monospaced typography support ([FontFamily.Monospace])
 * - Real-time syntax highlighting via [SyntaxHighlightTransformation]
 * - Gutter with line numbers and active line highlighting
 * - Cursor position tracking (Line & Column)
 * - Sora quick-coding symbol accessory bar for mobile coding ergonomics
 * - Word wrap and font size adjustments
 */
@Composable
fun SoraCodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    language: SupportedLanguage = SupportedLanguage.PLAIN_TEXT,
    fontSize: Int = 13,
    showLineNumbers: Boolean = true,
    wordWrap: Boolean = false,
    tabSize: Int = 4,
    readOnly: Boolean = false,
    onFontSizeChange: ((Int) -> Unit)? = null,
    onWordWrapChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val text = value.text
    val lines = remember(text) { text.lines().ifEmpty { listOf("") } }

    // Compute cursor line & column
    val cursorOffset = value.selection.start.coerceIn(0, text.length)
    val textUpToCursor = remember(text, cursorOffset) { text.take(cursorOffset) }
    val currentLineIndex = remember(textUpToCursor) { textUpToCursor.count { it == '\n' } }
    val currentLineNumber = currentLineIndex + 1
    val currentColNumber = remember(textUpToCursor, cursorOffset) {
        val lastNewline = textUpToCursor.lastIndexOf('\n')
        if (lastNewline == -1) cursorOffset + 1 else cursorOffset - lastNewline
    }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    val syntaxTransformation = remember(language) {
        SyntaxHighlightTransformation(language)
    }

    val codingSymbols = remember {
        listOf(
            "TAB", "{", "}", "(", ")", "[", "]", "\"", "'", "`",
            ":", ";", "=", "->", "$", "<", ">", "/", "\\", "_",
            "-", "+", "*", "!", "&", "|", "?", "#", "@", "."
        )
    }

    fun insertTextAtCursor(insertion: String) {
        val actualInsert = if (insertion == "TAB") " ".repeat(tabSize) else insertion
        val start = value.selection.start.coerceIn(0, text.length)
        val end = value.selection.end.coerceIn(0, text.length)
        val minPos = minOf(start, end)
        val maxPos = maxOf(start, end)

        val newText = text.substring(0, minPos) + actualInsert + text.substring(maxPos)
        val newSelection = TextRange(minPos + actualInsert.length)
        onValueChange(value.copy(text = newText, selection = newSelection))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .testTag("sora_code_editor_container")
    ) {
        // Status & Language Header Bar
        Surface(
            color = Color(0xFF111827),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().testTag("sora_status_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language indicator pill
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("sora_language_badge")
                ) {
                    Text(
                        text = language.displayName,
                        color = Color(0xFF00D2FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Cursor position
                Text(
                    text = "Ln $currentLineNumber, Col $currentColNumber",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("sora_cursor_position_text")
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Line count stats
                Text(
                    text = "${lines.size} lines",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.weight(1f))

                // Word wrap toggle
                if (onWordWrapChange != null) {
                    Surface(
                        color = if (wordWrap) Color(0xFF1E293B) else Color(0xFF0F172A),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .clickable { onWordWrapChange(!wordWrap) }
                            .testTag("sora_word_wrap_toggle")
                    ) {
                        Text(
                            text = if (wordWrap) "Wrap" else "No-Wrap",
                            color = if (wordWrap) Color(0xFF10B981) else Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Font size decrement
                if (onFontSizeChange != null) {
                    IconButton(
                        onClick = { if (fontSize > 10) onFontSizeChange(fontSize - 1) },
                        modifier = Modifier.size(26.dp).testTag("sora_font_decrease_button")
                    ) {
                        Text("A-", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "${fontSize}pt",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    IconButton(
                        onClick = { if (fontSize < 24) onFontSizeChange(fontSize + 1) },
                        modifier = Modifier.size(26.dp).testTag("sora_font_increase_button")
                    ) {
                        Text("A+", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Code Editor Canvas (Gutter + Text Field)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF090D16))
        ) {
            val lineHeight = (fontSize * 1.5).sp

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
            ) {
                // Line Numbers Gutter
                if (showLineNumbers) {
                    Column(
                        modifier = Modifier
                            .background(Color(0xFF0B1120))
                            .padding(vertical = 8.dp)
                            .testTag("sora_line_numbers_gutter"),
                        horizontalAlignment = Alignment.End
                    ) {
                        lines.indices.forEach { idx ->
                            val isCurrentLine = idx == currentLineIndex
                            Text(
                                text = "${idx + 1}",
                                color = if (isCurrentLine) Color(0xFF00D2FF) else Color(0xFF475569),
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                                lineHeight = lineHeight,
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp)
                                    .testTag("sora_line_number_${idx + 1}")
                            )
                        }
                    }

                    // Vertical border between gutter and editor
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color(0xFF1E293B))
                    )
                }

                // Monospaced Code Text Field Container
                val horizontalModifier = if (!wordWrap) {
                    Modifier.horizontalScroll(horizontalScrollState)
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(horizontalModifier)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        readOnly = readOnly,
                        textStyle = TextStyle(
                            color = Color(0xFFF1F5F9),
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            lineHeight = lineHeight
                        ),
                        cursorBrush = SolidColor(Color(0xFF00D2FF)),
                        visualTransformation = syntaxTransformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(min = 360.dp)
                            .testTag("sora_editor_text_field")
                            .testTag("editor_text_canvas")
                    )
                }
            }
        }

        // Sora Quick-Coding Symbols Accessory Bar
        Surface(
            color = Color(0xFF111827),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth().testTag("sora_quick_symbols_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                codingSymbols.forEach { sym ->
                    val isTab = sym == "TAB"
                    Surface(
                        color = if (isTab) Color(0xFF1E293B) else Color(0xFF161F30),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .clickable { insertTextAtCursor(sym) }
                            .height(32.dp)
                            .testTag(if (isTab) "sora_tab_indent_button" else "sora_symbol_button_$sym")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = if (isTab) 10.dp else 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sym,
                                color = if (isTab) Color(0xFF10B981) else Color(0xFF00D2FF),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Convenience overload for [SoraCodeEditor] using String content state.
 */
@Composable
fun SoraCodeEditor(
    text: String,
    onTextChange: (String) -> Unit,
    language: SupportedLanguage = SupportedLanguage.PLAIN_TEXT,
    fontSize: Int = 13,
    showLineNumbers: Boolean = true,
    wordWrap: Boolean = false,
    tabSize: Int = 4,
    readOnly: Boolean = false,
    onFontSizeChange: ((Int) -> Unit)? = null,
    onWordWrapChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember(text) {
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }

    // Keep textFieldValue in sync if text is updated externally
    LaunchedEffect(text) {
        if (textFieldValue.text != text) {
            textFieldValue = textFieldValue.copy(
                text = text,
                selection = TextRange(text.length.coerceAtMost(textFieldValue.selection.start))
            )
        }
    }

    SoraCodeEditor(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            if (newValue.text != text) {
                onTextChange(newValue.text)
            }
        },
        language = language,
        fontSize = fontSize,
        showLineNumbers = showLineNumbers,
        wordWrap = wordWrap,
        tabSize = tabSize,
        readOnly = readOnly,
        onFontSizeChange = onFontSizeChange,
        onWordWrapChange = onWordWrapChange,
        modifier = modifier
    )
}
