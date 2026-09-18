package com.molinax.manager

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.molinax.manager.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationScaffoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBottomNavigationBarModuleSwitching() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MolinaXMainScreen()
            }
        }

        // Verify Scaffold and Bottom Navigation Bar exist
        composeTestRule.onNodeWithTag("main_scaffold").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_nav_bar").assertIsDisplayed()

        // Verify initial screen is Player
        composeTestRule.onNodeWithTag("nav_tab_player").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screen_player").assertIsDisplayed()

        // Switch to Editor module
        composeTestRule.onNodeWithTag("nav_tab_editor").performClick()
        composeTestRule.onNodeWithTag("screen_editor").assertIsDisplayed()

        // Switch to Terminal module
        composeTestRule.onNodeWithTag("nav_tab_terminal").performClick()
        composeTestRule.onNodeWithTag("screen_terminal").assertIsDisplayed()

        // Switch to Utilities module
        composeTestRule.onNodeWithTag("nav_tab_utilities").performClick()
        composeTestRule.onNodeWithTag("screen_utilities").assertIsDisplayed()

        // Switch back to Player module
        composeTestRule.onNodeWithTag("nav_tab_player").performClick()
        composeTestRule.onNodeWithTag("screen_player").assertIsDisplayed()
    }

    @Test
    fun testModalNavigationDrawerQuickSwitching() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MolinaXMainScreen()
            }
        }

        // Verify Drawer and Menu button are displayed
        composeTestRule.onNodeWithTag("modal_navigation_drawer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_menu_button").assertIsDisplayed()

        // Initial state is Player
        composeTestRule.onNodeWithTag("screen_player").assertIsDisplayed()

        // Open Modal Navigation Drawer
        composeTestRule.onNodeWithTag("drawer_menu_button").performClick()
        composeTestRule.waitForIdle()

        // Verify drawer contents & items are displayed
        composeTestRule.onNodeWithTag("modal_drawer_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_item_editor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_item_terminal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_item_player").assertIsDisplayed()

        // Switch to Sora Code Editor from Drawer
        composeTestRule.onNodeWithTag("drawer_item_editor").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screen_editor").assertIsDisplayed()

        // Open Drawer again and switch to Linux Terminal
        composeTestRule.onNodeWithTag("drawer_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_terminal").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screen_terminal").assertIsDisplayed()

        // Open Drawer again and switch back to MPV Player
        composeTestRule.onNodeWithTag("drawer_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_player").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screen_player").assertIsDisplayed()
    }
}

