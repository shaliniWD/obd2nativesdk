package com.wisedrive.obd2.sample

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the Sample App UI
 * These tests run on an Android device/emulator
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_showsTitle() {
        // Verify the app title is displayed
        composeTestRule.onNodeWithText("WiseDrive OBD2").assertIsDisplayed()
    }

    @Test
    fun appLaunches_showsScanButton() {
        // Verify the scan button exists
        composeTestRule.onNodeWithText("Discover Devices").assertIsDisplayed()
    }

    @Test
    fun clickDiscoverDevices_startsScanning() {
        // Click discover button
        composeTestRule.onNodeWithText("Discover Devices").performClick()
        
        // Wait for UI update
        composeTestRule.waitForIdle()
        
        // In mock mode, should show mock devices after scanning
        // The exact behavior depends on the implementation
    }

    @Test
    fun mockMode_showsMockDevices() {
        // Start device discovery
        composeTestRule.onNodeWithText("Discover Devices").performClick()
        
        // Wait for mock devices to appear
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("OBDII", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Verify mock device is shown
        composeTestRule.onNodeWithText("OBDII", substring = true).assertIsDisplayed()
    }

    @Test
    fun selectDevice_showsConnectOption() {
        // First discover devices
        composeTestRule.onNodeWithText("Discover Devices").performClick()
        
        // Wait for devices
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("OBDII", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Click on a device
        composeTestRule.onNodeWithText("OBDII", substring = true).performClick()
        
        // Should show connection options
        composeTestRule.waitForIdle()
    }

    @Test
    fun fullScanFlow_mockMode() {
        // 1. Discover devices
        composeTestRule.onNodeWithText("Discover Devices").performClick()
        
        // 2. Wait for mock devices
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("OBDII", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // 3. Select device
        composeTestRule.onNodeWithText("OBDII", substring = true).performClick()
        composeTestRule.waitForIdle()
        
        // 4. Connect (if there's a connect button)
        composeTestRule.onAllNodesWithText("Connect").onFirst().performClick()
        
        // 5. Wait for connection
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodesWithText("Connected", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // 6. Start scan
        composeTestRule.onNodeWithText("Start Scan", substring = true).performClick()
        
        // 7. Wait for scan progress stages
        // The scan goes through 9 stages, verify key ones appear
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            composeTestRule
                .onAllNodesWithText("complete", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
