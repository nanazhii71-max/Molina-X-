package com.molinax.manager.terminal

import androidx.compose.ui.graphics.Color
import java.io.File

data class TerminalSpan(
    val text: String,
    val color: Color = Color(0xFFE2E8F0),
    val bgColor: Color = Color.Transparent,
    val isBold: Boolean = false,
    val isUnderline: Boolean = false
)

data class TerminalLine(
    val spans: List<TerminalSpan>
) {
    val rawText: String get() = spans.joinToString("") { it.text }

    companion object {
        fun simple(text: String, color: Color = Color(0xFFE2E8F0)): TerminalLine {
            return TerminalLine(listOf(TerminalSpan(text = text, color = color)))
        }
    }
}

data class TerminalSessionInfo(
    val id: String,
    val title: String,
    val cwd: File,
    val pid: Long = -1,
    val isRunning: Boolean = true,
    val exitCode: Int? = null
)
