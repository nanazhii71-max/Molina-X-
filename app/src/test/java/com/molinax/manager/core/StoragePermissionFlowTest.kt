package com.molinax.manager.core

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.molinax.manager.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StoragePermissionFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testStoragePermissionGateDisplaysRationaleOrContent() {
        composeTestRule.setContent {
            MyApplicationTheme {
                StoragePermissionGate {
                    Text(
                        text = "Device Storage Content Active",
                        modifier = Modifier.testTag("file_explorer_content")
                    )
                }
            }
        }

        // In the test runner environment, either the permission is granted (content rendered)
        // or the permission gate is displayed with grant buttons.
        val hasContent = runCatching {
            composeTestRule.onNodeWithTag("file_explorer_content").assertIsDisplayed()
        }.isSuccess

        if (hasContent) {
            composeTestRule.onNodeWithTag("file_explorer_content").assertIsDisplayed()
        } else {
            composeTestRule.onNodeWithTag("storage_permission_gate").assertIsDisplayed()
            composeTestRule.onNodeWithTag("storage_permission_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("grant_storage_permission_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_storage_settings_button").assertIsDisplayed()
        }
    }
}
