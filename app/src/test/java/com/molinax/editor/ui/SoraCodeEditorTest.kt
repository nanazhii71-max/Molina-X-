package com.molinax.editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import com.molinax.editor.SupportedLanguage
import com.molinax.editor.SyntaxHighlighter
import com.molinax.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoraCodeEditorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSoraCodeEditorRendersCodeAndGutter() {
        val sampleCode = """
            fun main() {
                println("Hello MolinaX")
            }
        """.trimIndent()

        var currentText = sampleCode

        composeTestRule.setContent {
            MyApplicationTheme {
                SoraCodeEditor(
                    text = currentText,
                    onTextChange = { currentText = it },
                    language = SupportedLanguage.KOTLIN,
                    fontSize = 13,
                    showLineNumbers = true
                )
            }
        }

        // Verify editor container and text field exist
        composeTestRule.onNodeWithTag("sora_code_editor_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sora_editor_text_field").assertIsDisplayed()

        // Verify line numbers gutter exists and has line 1 and 3
        composeTestRule.onNodeWithTag("sora_line_numbers_gutter").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sora_line_number_1", useUnmergedTree = true).assertIsDisplayed()

        // Verify language badge displays "Kotlin"
        composeTestRule.onNodeWithTag("sora_language_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Kotlin", substring = true).assertIsDisplayed()
    }

    @Test
    fun testQuickCodingSymbolInsertion() {
        var currentText by mutableStateOf("val x = ")

        composeTestRule.setContent {
            MyApplicationTheme {
                SoraCodeEditor(
                    text = currentText,
                    onTextChange = { currentText = it },
                    language = SupportedLanguage.KOTLIN
                )
            }
        }

        // Tap the bracket symbol "{" in the quick symbols bar
        composeTestRule.onNodeWithTag("sora_symbol_button_{").performClick()

        // Verify "{" was inserted
        assertTrue("Expected { to be inserted", currentText.contains("{"))
    }

    @Test
    fun testTabInsertionInsertsSpaces() {
        var currentText by mutableStateOf("")

        composeTestRule.setContent {
            MyApplicationTheme {
                SoraCodeEditor(
                    text = currentText,
                    onTextChange = { currentText = it },
                    language = SupportedLanguage.PYTHON,
                    tabSize = 4
                )
            }
        }

        // Click TAB indent button
        composeTestRule.onNodeWithTag("sora_tab_indent_button").performClick()

        // Verify 4 spaces were inserted
        assertEquals("    ", currentText)
    }

    @Test
    fun testSyntaxHighlighterAppliesSpansForKotlin() {
        val code = "val count = 42"
        val highlighted = SyntaxHighlighter.highlight(code, SupportedLanguage.KOTLIN)

        assertEquals(code, highlighted.text)
        // Highlighting should produce span styles for keywords and numbers
        assertTrue("Syntax spans should be applied for Kotlin code", highlighted.spanStyles.isNotEmpty())
    }
}
