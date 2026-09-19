package com.molinax.editor.storage

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.molinax.editor.DocumentManager
import com.molinax.editor.EditorDocument
import com.molinax.editor.EditorViewModel
import com.molinax.editor.SupportedLanguage
import com.molinax.editor.ui.EditorScreen
import com.molinax.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileSavingServiceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var service: StorageAccessFrameworkService

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        service = StorageAccessFrameworkService(application)
    }

    @Test
    fun testSaveToFileAndInternalStorage() = runBlocking {
        val doc = EditorDocument(
            id = UUID.randomUUID().toString(),
            file = null,
            uri = null,
            title = "TestScript.kt",
            content = "println(\"Hello Storage Access Framework!\")\n",
            isDirty = true,
            language = SupportedLanguage.KOTLIN
        )

        // 1. Test save to internal storage
        val internalResult = service.saveToInternalStorage(doc, "TestScript.kt", "sora_docs")
        assertTrue("Internal save should succeed", internalResult.isSuccess)
        val saveResult = internalResult.getOrThrow()
        assertEquals(StorageType.INTERNAL_STORAGE, saveResult.storageType)
        assertEquals("TestScript.kt", saveResult.title)
        assertTrue(saveResult.bytesWritten > 0)
        assertFalse(saveResult.savedDocument.isDirty)
        assertNotNull(saveResult.savedDocument.file)
        assertTrue(saveResult.savedDocument.file!!.exists())
        assertEquals(doc.content, saveResult.savedDocument.file!!.readText())

        // 2. Test saving active document through service
        val modifiedDoc = saveResult.savedDocument.copy(
            content = "println(\"Updated content\")\n",
            isDirty = true
        )
        val updatedResult = service.saveDocument(modifiedDoc)
        assertTrue(updatedResult.isSuccess)
        assertEquals("println(\"Updated content\")\n", saveResult.savedDocument.file!!.readText())
    }

    @Test
    fun testSaveToExternalStorage() = runBlocking {
        val doc = EditorDocument(
            id = UUID.randomUUID().toString(),
            file = null,
            uri = null,
            title = "ExternalDoc.py",
            content = "print('Hello External Storage')\n",
            isDirty = true,
            language = SupportedLanguage.PYTHON
        )

        val externalResult = service.saveToExternalStorage(doc, "ExternalDoc.py", "scripts")
        assertTrue("External save should succeed", externalResult.isSuccess)
        val result = externalResult.getOrThrow()
        assertEquals("ExternalDoc.py", result.title)
        assertEquals(SupportedLanguage.PYTHON, result.savedDocument.language)
        assertFalse(result.savedDocument.isDirty)
        assertTrue(result.savedDocument.file!!.exists())
    }

    @Test
    fun testSaveToUriWithStorageAccessFramework() = runBlocking {
        // Create target file to back the URI in Robolectric
        val tempFile = File(application.cacheDir, "saf_target.json").apply {
            createNewFile()
        }
        val fileUri = Uri.fromFile(tempFile)

        val doc = EditorDocument(
            id = UUID.randomUUID().toString(),
            file = null,
            uri = null,
            title = "Config.json",
            content = "{\"key\": \"saf_value\"}",
            isDirty = true,
            language = SupportedLanguage.JSON
        )

        val uriResult = service.saveToUri(doc, fileUri)
        assertTrue("SAF Uri save should succeed", uriResult.isSuccess)
        val result = uriResult.getOrThrow()
        assertEquals(StorageType.SAF_EXTERNAL, result.storageType)
        assertEquals(fileUri, result.savedDocument.uri)
        assertNull(result.savedDocument.file)
        assertFalse(result.savedDocument.isDirty)
        assertEquals(SupportedLanguage.JSON, result.savedDocument.language)
        assertEquals("{\"key\": \"saf_value\"}", tempFile.readText())
    }

    @Test
    fun testEditorViewModelStorageAccessFrameworkWorkflow() {
        val viewModel = EditorViewModel(application, service)
        val activeDoc = viewModel.activeDocument()
        assertNotNull(activeDoc)

        viewModel.updateContent("// Modified for SAF test\nval x = 42\n")
        assertTrue(viewModel.activeDocument()!!.isDirty)

        var successCalled = false
        var errorCaught: Throwable? = null

        composeTestRule.runOnUiThread {
            viewModel.saveToInternal(
                fileName = "view_model_test.kt",
                onSuccess = { successCalled = true },
                onError = { errorCaught = it }
            )
        }

        var attempts = 0
        while (!successCalled && errorCaught == null && attempts < 50) {
            Thread.sleep(50)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            attempts++
        }

        assertNull("No error should be thrown during save: ${errorCaught?.message}", errorCaught)
        assertTrue("ViewModel save callback should be invoked", successCalled)
        assertFalse("Document isDirty should be false after save", viewModel.activeDocument()!!.isDirty)
        assertNotNull(viewModel.saveStatusMessage.value)
        assertTrue(viewModel.saveStatusMessage.value!!.contains("Saved"))
    }

    @Test
    fun testEditorScreenRendersSafAffordancesAndDialog() {
        val viewModel = EditorViewModel(application, service)

        composeTestRule.setContent {
            MyApplicationTheme {
                EditorScreen(viewModel = viewModel)
            }
        }

        // Verify SAF open button and Save / Save As buttons are visible
        composeTestRule.onNodeWithTag("editor_open_saf_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("editor_save_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("editor_save_as_button").assertIsDisplayed()

        // Tap Save As to open dialog
        composeTestRule.onNodeWithTag("editor_save_as_button").performClick()

        // Verify Save As dialog opened with SAF option
        composeTestRule.onNodeWithText("Save Document As", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_save_saf_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Storage Access Framework (SAF)", substring = true).assertIsDisplayed()

        // Switch to Internal tab
        composeTestRule.onNodeWithText("Internal").performClick()
        composeTestRule.onNodeWithTag("internal_file_name_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_save_internal_button").assertIsDisplayed()

        // Switch to Path tab
        composeTestRule.onNodeWithText("Path").performClick()
        composeTestRule.onNodeWithTag("custom_path_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_save_path_button").assertIsDisplayed()
    }
}
