package com.wisedrive.obd2.sample

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BlackBox Instrumented Tests for the Sample App UI
 * Tests user-facing functionality without knowledge of internal implementation
 * These tests run on an Android device/emulator
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // ========== BLACKBOX: App Launch Tests ==========

    @Test
    fun appLaunches_showsTitle() {
        composeTestRule.onNodeWithText("WiseDrive OBD2").assertIsDisplayed()
    }

    @Test
    fun appLaunches_showsTestModeLabel() {
        composeTestRule.onNodeWithText("Test Mode").assertIsDisplayed()
    }

    @Test
    fun appLaunches_showsInitializeButton() {
        composeTestRule.onNodeWithText("Initialize").assertIsDisplayed()
    }

    @Test
    fun appLaunches_showsPermissionsButton() {
        composeTestRule.onNodeWithText("Permissions").assertIsDisplayed()
    }

    @Test
    fun appLaunches_showsSdkStatus() {
        composeTestRule.onNodeWithText("SDK Status").assertIsDisplayed()
    }

    // ========== BLACKBOX: Initialization Tests ==========

    @Test
    fun clickInitialize_updatesStatus() {
        // Click initialize
        composeTestRule.onNodeWithText("Initialize").performClick()
        composeTestRule.waitForIdle()
        
        // After init, Find Devices should be enabled
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("Find Devices")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ========== BLACKBOX: Mock Mode Toggle Tests ==========

    @Test
    fun mockModeToggle_isDisplayed() {
        composeTestRule.onNodeWithText("Test Mode").assertIsDisplayed()
    }

    @Test
    fun mockModeToggle_showsMockModeStatus() {
        // Should show Mock Mode label in header
        composeTestRule.onNodeWithText("Mock Mode").assertIsDisplayed()
    }
}
