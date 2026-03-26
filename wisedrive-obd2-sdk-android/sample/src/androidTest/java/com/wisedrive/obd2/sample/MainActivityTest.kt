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
        // Verify the app title is displayed
        composeTestRule.onNodeWithText("WiseDrive OBD2").assertIsDisplayed()
    }

    @Test
    fun appLaunches_showsMockModeToggle() {
        // Mock mode toggle should be visible
        composeTestRule.onNodeWithText("Test Mode").assertIsDisplayed()
    }

    @Test
    fun appLaunches_showsInitializeButton() {
        // Initialize button should be visible
        composeTestRule.onNodeWithText("Initialize").assertIsDisplayed()
    }

    @Test
    fun appLaunches_showsPermissionsButton() {
        // Permissions button should be visible
        composeTestRule.onNodeWithText("Permissions").assertIsDisplayed()
    }

    // ========== BLACKBOX: Input Field Tests ==========

    @Test
    fun afterInitAndConnect_showsBothInputFields() {
        // Initialize SDK
        composeTestRule.onNodeWithText("Initialize").performClick()
        composeTestRule.waitForIdle()
        
        // Find devices
        composeTestRule.onNodeWithText("Find Devices").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodesWithText("OBDII", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Select and connect
        composeTestRule.onNodeWithText("OBDII", substring = true).performClick()
        composeTestRule.waitForIdle()
        
        // Now check for both input fields
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("Registration Number", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText("Registration Number", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Tracking ID", substring = true).assertIsDisplayed()
    }

    @Test
    fun registrationField_acceptsInput() {
        initializeAndConnect()
        
        // Clear and enter registration number
        composeTestRule.onNodeWithText("Registration Number", substring = true)
            .performClick()
        composeTestRule.onNodeWithText("MH12AB1234", substring = true)
            .performTextClearance()
        composeTestRule.onNodeWithText("Registration Number", substring = true)
            .performTextInput("KA01XY9999")
        
        // Verify the input was accepted
        composeTestRule.onNodeWithText("KA01XY9999").assertExists()
    }

    @Test
    fun trackingIdField_acceptsInput() {
        initializeAndConnect()
        
        // Clear and enter tracking ID
        composeTestRule.onNodeWithText("Tracking ID", substring = true)
            .performClick()
        composeTestRule.onNodeWithText("ORD6894331", substring = true)
            .performTextClearance()
        composeTestRule.onNodeWithText("Tracking ID", substring = true)
            .performTextInput("ORD9999999")
        
        // Verify the input was accepted
        composeTestRule.onNodeWithText("ORD9999999").assertExists()
    }

    @Test
    fun scanButton_disabledWhenRegistrationEmpty() {
        initializeAndConnect()
        
        // Clear registration number
        composeTestRule.onNodeWithText("MH12AB1234", substring = true)
            .performTextClearance()
        
        // Scan button should be disabled (not clickable)
        composeTestRule.onNodeWithText("Start Full Scan").assertIsNotEnabled()
    }

    @Test
    fun scanButton_disabledWhenTrackingIdEmpty() {
        initializeAndConnect()
        
        // Clear tracking ID
        composeTestRule.onNodeWithText("ORD6894331", substring = true)
            .performTextClearance()
        
        // Scan button should be disabled
        composeTestRule.onNodeWithText("Start Full Scan").assertIsNotEnabled()
    }

    @Test
    fun scanButton_enabledWhenBothFieldsFilled() {
        initializeAndConnect()
        
        // Both fields have default values, button should be enabled
        composeTestRule.onNodeWithText("Start Full Scan").assertIsEnabled()
    }

    // ========== BLACKBOX: Device Discovery Tests ==========

    @Test
    fun clickFindDevices_showsDeviceList() {
        // Initialize first
        composeTestRule.onNodeWithText("Initialize").performClick()
        composeTestRule.waitForIdle()
        
        // Click find devices
        composeTestRule.onNodeWithText("Find Devices").performClick()
        
        // Wait for mock devices to appear
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodesWithText("OBDII", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Verify mock device is shown
        composeTestRule.onNodeWithText("OBDII", substring = true).assertIsDisplayed()
    }

    @Test
    fun mockMode_showsMockDevicesWithSignalStrength() {
        composeTestRule.onNodeWithText("Initialize").performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithText("Find Devices").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodesWithText("dBm", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Signal strength indicator should be visible
        composeTestRule.onNodeWithText("dBm", substring = true).assertIsDisplayed()
    }

    // ========== BLACKBOX: Full Scan Flow Tests ==========

    @Test
    fun fullScanFlow_mockMode_withBothFields() {
        initializeAndConnect()
        
        // Verify both fields have values
        composeTestRule.onNodeWithText("MH12AB1234").assertExists()
        composeTestRule.onNodeWithText("ORD6894331").assertExists()
        
        // Start scan
        composeTestRule.onNodeWithText("Start Full Scan").performClick()
        
        // Wait for scan to complete
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            composeTestRule
                .onAllNodesWithText("Scan Complete", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Results screen should show scan report
        composeTestRule.onNodeWithText("Scan Report").assertIsDisplayed()
    }

    @Test
    fun fullScanFlow_showsAnalyticsStatus() {
        initializeAndConnect()
        
        composeTestRule.onNodeWithText("Start Full Scan").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            composeTestRule
                .onAllNodesWithText("WiseDrive Analytics", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Analytics status should be displayed
        composeTestRule.onNodeWithText("WiseDrive Analytics").assertIsDisplayed()
    }

    @Test
    fun fullScanFlow_showsAnalyticsPayload() {
        initializeAndConnect()
        
        composeTestRule.onNodeWithText("Start Full Scan").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            composeTestRule
                .onAllNodesWithText("Analytics Payload", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Payload section should be visible
        composeTestRule.onNodeWithText("Analytics Payload").assertIsDisplayed()
    }

    // ========== BLACKBOX: Logs Tests ==========

    @Test
    fun logsButton_opensLogsScreen() {
        // Click logs button
        composeTestRule.onNode(hasContentDescription("Logs")).performClick()
        
        // Should show logs screen
        composeTestRule.onNodeWithText("Logs").assertIsDisplayed()
    }

    @Test
    fun logsScreen_showsClearButton() {
        composeTestRule.onNode(hasContentDescription("Logs")).performClick()
        
        // Clear button should be visible
        composeTestRule.onNode(hasContentDescription("Clear")).assertIsDisplayed()
    }

    // ========== BLACKBOX: Results Screen Tests ==========

    @Test
    fun resultsScreen_showsSubmitButton() {
        initializeAndConnect()
        
        composeTestRule.onNodeWithText("Start Full Scan").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            composeTestRule
                .onAllNodesWithText("Confirm Submission", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText("Confirm Submission").assertIsDisplayed()
    }

    @Test
    fun resultsScreen_showsNewScanButton() {
        initializeAndConnect()
        
        composeTestRule.onNodeWithText("Start Full Scan").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            composeTestRule
                .onAllNodesWithText("New Scan", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText("New Scan").assertIsDisplayed()
    }

    @Test
    fun resultsScreen_newScan_returnsToHome() {
        initializeAndConnect()
        
        composeTestRule.onNodeWithText("Start Full Scan").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            composeTestRule
                .onAllNodesWithText("New Scan", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText("New Scan").performClick()
        composeTestRule.waitForIdle()
        
        // Should return to home with input fields
        composeTestRule.onNodeWithText("Registration Number", substring = true).assertIsDisplayed()
    }

    // ========== Helper Functions ==========

    private fun initializeAndConnect() {
        // Initialize SDK
        composeTestRule.onNodeWithText("Initialize").performClick()
        composeTestRule.waitForIdle()
        
        // Find devices
        composeTestRule.onNodeWithText("Find Devices").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodesWithText("OBDII", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Select device
        composeTestRule.onNodeWithText("OBDII", substring = true).performClick()
        
        // Wait for connection
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodesWithText("Connected", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
