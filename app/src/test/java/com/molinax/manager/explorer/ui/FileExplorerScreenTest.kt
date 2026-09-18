package com.molinax.manager.explorer.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.molinax.manager.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileExplorerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFileExplorerDisplaysItemsAndNavigatesDirectories() {
        val rootDir = tempFolder.newFolder("explorer_test_root")
        val subDir = File(rootDir, "documents_folder").apply { mkdir() }
        val codeFile = File(rootDir, "main_script.py").apply { writeText("print('MolinaX')") }
        val mediaFile = File(rootDir, "audio_track.mp3").apply { writeText("ID3...") }

        // Also add a file inside the subfolder
        val nestedDoc = File(subDir, "nested_note.txt").apply { writeText("Nested Content") }

        var lastClickedItem: String? = null
        var lastNavigatedDir: File? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                FileExplorerScreen(
                    initialDirectory = rootDir,
                    onFileClick = { item -> lastClickedItem = item.name },
                    onDirectoryChanged = { dir -> lastNavigatedDir = dir }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify root directory contents are displayed
        composeTestRule.onNodeWithTag("file_explorer_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("file_explorer_lazy_column").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explorer_item_documents_folder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explorer_item_main_script.py").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explorer_item_audio_track.mp3").assertIsDisplayed()

        // Test file click
        composeTestRule.onNodeWithTag("explorer_item_main_script.py").performClick()
        assertEquals("main_script.py", lastClickedItem)

        // Test navigation into directory
        composeTestRule.onNodeWithTag("explorer_item_documents_folder").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("explorer_item_nested_note.txt").fetchSemanticsNodes().isNotEmpty()
        }

        // After clicking directory, current view should show nested_note.txt
        assertEquals("documents_folder", lastNavigatedDir?.name)
        composeTestRule.onNodeWithTag("explorer_item_nested_note.txt").assertIsDisplayed()

        // Test navigating up
        composeTestRule.onNodeWithTag("nav_up_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("explorer_item_documents_folder").fetchSemanticsNodes().isNotEmpty()
        }

        // Back at root
        composeTestRule.onNodeWithTag("explorer_item_documents_folder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explorer_item_main_script.py").assertIsDisplayed()
    }

    @Test
    fun testFileExplorerEmptyState() {
        val emptyDir = tempFolder.newFolder("empty_folder")

        composeTestRule.setContent {
            MyApplicationTheme {
                FileExplorerScreen(
                    initialDirectory = emptyDir
                )
            }
        }

        composeTestRule.onNodeWithTag("explorer_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("Directory is empty").assertIsDisplayed()
    }
}
