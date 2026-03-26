package com.wisedrive.obd2.sample

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wisedrive.obd2.WiseDriveOBD2SDK
import com.wisedrive.obd2.models.BLEDevice
import com.wisedrive.obd2.models.ScanOptions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BlackBox Integration Tests for SDK functionality
 * Tests the SDK in mock mode on a real Android device/emulator
 * Tests user-facing API without knowledge of internal implementation
 */
@RunWith(AndroidJUnit4::class)
class SDKIntegrationTest {

    private lateinit var context: Context
    private var sdk: WiseDriveOBD2SDK? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun teardown() {
        runBlocking {
            try {
                sdk?.let {
                    if (it.isConnected()) {
                        it.disconnect()
                    }
                    it.cleanup()
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        sdk = null
    }

    // ========== BLACKBOX: Initialization Tests ==========

    @Test
    fun sdkInitialization_succeeds() = runBlocking {
        sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
        val initialized = sdk!!.initializeWithKey("test-api-key")
        assertTrue("SDK should initialize in mock mode", initialized)
    }

    @Test
    fun sdkInitialization_withEmptyKey_succeeds() = runBlocking {
        sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
        val initialized = sdk!!.initializeWithKey("")
        assertTrue("SDK should initialize even with empty key", initialized)
    }

    // ========== BLACKBOX: Device Discovery Tests ==========

    @Test
    fun deviceDiscovery_returnsMockDevices() = runBlocking {
        sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
        sdk!!.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        
        sdk!!.discoverDevices(
            onDeviceFound = { device -> devices.add(device) },
            timeoutMs = 3000
        )
        
        assertTrue("Should find at least one mock device", devices.isNotEmpty())
    }

    // ========== BLACKBOX: Connection Tests ==========

    @Test
    fun connect_toMockDevice_succeeds() = runBlocking {
        sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
        sdk!!.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk!!.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        
        assertTrue("Should find devices", devices.isNotEmpty())
        
        val connectableDevice = devices.firstOrNull { it.isConnectable }
        assertNotNull("Should have connectable device", connectableDevice)
        
        sdk!!.connect(connectableDevice!!.id)
        assertTrue("Should be connected", sdk!!.isConnected())
    }

    @Test
    fun disconnect_clearsState() = runBlocking {
        sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
        sdk!!.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk!!.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        
        val device = devices.first { it.isConnectable }
        sdk!!.connect(device.id)
        assertTrue(sdk!!.isConnected())
        
        sdk!!.disconnect()
        
        assertFalse("Should not be connected after disconnect", sdk!!.isConnected())
    }

    // ========== BLACKBOX: Scan Tests with Both Fields ==========

    @Test
    fun fullScan_requiresBothFields() = runBlocking {
        sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
        sdk!!.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk!!.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk!!.connect(devices.first { it.isConnectable }.id)
        
        // Test with both fields - should succeed
        val result = sdk!!.runFullScan(
            ScanOptions(
                registrationNumber = "MH12AB1234",
                trackingId = "ORD6894331",
                manufacturer = "hyundai"
            )
        )
        
        assertNotNull("Scan result should not be null", result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun scanOptions_failsWithEmptyRegistration() {
        // This should throw IllegalArgumentException
        ScanOptions(
            registrationNumber = "",
            trackingId = "ORD6894331"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun scanOptions_failsWithEmptyTrackingId() {
        // This should throw IllegalArgumentException
        ScanOptions(
            registrationNumber = "MH12AB1234",
            trackingId = ""
        )
    }

    @Test
    fun fullScan_returnsScanReport() = runBlocking {
        sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
        sdk!!.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk!!.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk!!.connect(devices.first { it.isConnectable }.id)
        
        val result = sdk!!.runFullScan(
            ScanOptions(
                registrationNumber = "KA01XY9999",
                trackingId = "ORD1234567",
                manufacturer = "hyundai",
                year = 2022
            )
        )
        
        // Verify ScanReport structure
        assertNotNull(result.scanId)
        assertNotNull(result.scanTimestamp)
        assertTrue(result.scanDuration >= 0)
    }

    // ========== BLACKBOX: Analytics Tests ==========

    @Test
    fun fullScan_preparesAnalyticsPayload() = runBlocking {
        sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
        
        var payloadReceived: String? = null
        sdk!!.setOnAnalyticsPayloadPrepared { json ->
            payloadReceived = json
        }
        
        sdk!!.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk!!.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk!!.connect(devices.first { it.isConnectable }.id)
        
        sdk!!.runFullScan(
            ScanOptions(
                registrationNumber = "MH12AB1234",
                trackingId = "ORD6894331"
            )
        )
        
        assertNotNull("Analytics payload should be prepared", payloadReceived)
        assertTrue("Payload should contain license_plate", payloadReceived!!.contains("license_plate"))
        assertTrue("Payload should contain tracking_id", payloadReceived!!.contains("tracking_id"))
    }

    @Test
    fun submitReport_succeeds() = runBlocking {
        sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
        sdk!!.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk!!.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk!!.connect(devices.first { it.isConnectable }.id)
        
        val result = sdk!!.runFullScan(
            ScanOptions(
                registrationNumber = "MH12AB1234",
                trackingId = "ORD6894331"
            )
        )
        
        val submitted = sdk!!.submitReport(result)
        assertTrue("Report submission should succeed in mock mode", submitted)
    }

    // ========== BLACKBOX: State Verification Tests ==========

    @Test
    fun isMockMode_returnsTrue() = runBlocking {
        sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
        assertTrue("Should be in mock mode", sdk!!.isMockMode())
    }
}
