package com.molinax.manager.terminal

import androidx.compose.ui.graphics.Color

object AnsiParser {

    private val STANDARD_COLORS = arrayOf(
        Color(0xFF000000), // 0: Black
        Color(0xFFEF4444), // 1: Red
        Color(0xFF22C55E), // 2: Green
        Color(0xFFEAB308), // 3: Yellow
        Color(0xFF3B82F6), // 4: Blue
        Color(0xFFA855F7), // 5: Magenta
        Color(0xFF06B6D4), // 6: Cyan
        Color(0xFFE2E8F0), // 7: White
        // Bright variants (8-15)
        Color(0xFF64748B), // 8: Bright Black (Gray)
        Color(0xFFF87171), // 9: Bright Red
        Color(0xFF4ADE80), // 10: Bright Green
        Color(0xFFFDE047), // 11: Bright Yellow
        Color(0xFF60A5FA), // 12: Bright Blue
        Color(0xFFC084FC), // 13: Bright Magenta
        Color(0xFF22D3EE), // 14: Bright Cyan
        Color(0xFFFFFFFF)  // 15: Bright White
    )

    private val DEFAULT_TEXT_COLOR = Color(0xFFE2E8F0)

    fun parseLine(raw: String): TerminalLine {
        val spans = mutableListOf<TerminalSpan>()
        val len = raw.length
        var i = 0
        var currentColor = DEFAULT_TEXT_COLOR
        var currentBgColor = Color.Transparent
        var currentBold = false
        var currentUnderline = false
        val buffer = StringBuilder()

        fun flushBuffer() {
            if (buffer.isNotEmpty()) {
                spans.add(
                    TerminalSpan(
                        text = buffer.toString(),
                        color = currentColor,
                        bgColor = currentBgColor,
                        isBold = currentBold,
                        isUnderline = currentUnderline
                    )
                )
                buffer.setLength(0)
            }
        }

        while (i < len) {
            val c = raw[i]
            if (c == '\u001b' && i + 1 < len && raw[i + 1] == '[') {
                // ANSI escape sequence start
                flushBuffer()
                var end = i + 2
                while (end < len && raw[end] !in 'A'..'Z' && raw[end] !in 'a'..'z') {
                    end++
                }
                if (end < len) {
                    val codeChar = raw[end]
                    val paramsStr = raw.substring(i + 2, end)
                    if (codeChar == 'm') {
                        // SGR (Select Graphic Rendition)
                        val params = if (paramsStr.isEmpty()) listOf(0) else paramsStr.split(";").mapNotNull { it.toIntOrNull() }
                        var pIdx = 0
                        while (pIdx < params.size) {
                            when (val p = params[pIdx]) {
                                0 -> {
                                    currentColor = DEFAULT_TEXT_COLOR
                                    currentBgColor = Color.Transparent
                                    currentBold = false
                                    currentUnderline = false
                                }
                                1 -> currentBold = true
                                4 -> currentUnderline = true
                                22 -> currentBold = false
                                24 -> currentUnderline = false
                                in 30..37 -> currentColor = STANDARD_COLORS[p - 30]
                                39 -> currentColor = DEFAULT_TEXT_COLOR
                                in 40..47 -> currentBgColor = STANDARD_COLORS[p - 40]
                                49 -> currentBgColor = Color.Transparent
                                in 90..97 -> currentColor = STANDARD_COLORS[p - 90 + 8]
                                in 100..107 -> currentBgColor = STANDARD_COLORS[p - 100 + 8]
                                38 -> {
                                    // Extended foreground (38;5;n)
                                    if (pIdx + 2 < params.size && params[pIdx + 1] == 5) {
                                        val colorIdx = params[pIdx + 2].coerceIn(0, 255)
                                        currentColor = if (colorIdx < 16) STANDARD_COLORS[colorIdx] else Color(0xFF00D2FF)
                                        pIdx += 2
                                    }
                                }
                            }
                            pIdx++
                        }
                    }
                    i = end + 1
                    continue
                }
            }

            // Normal character or printable
            if (c != '\r') {
                buffer.append(c)
            }
            i++
        }

        flushBuffer()
        if (spans.isEmpty()) {
            spans.add(TerminalSpan(text = ""))
        }
        return TerminalLine(spans)
    }
}
