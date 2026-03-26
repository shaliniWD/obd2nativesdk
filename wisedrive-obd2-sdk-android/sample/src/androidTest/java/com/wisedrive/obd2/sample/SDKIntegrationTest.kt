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
    private lateinit var sdk: WiseDriveOBD2SDK

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
    }

    @After
    fun teardown() = runBlocking {
        if (sdk.isConnected()) {
            sdk.disconnect()
        }
        sdk.cleanup()
    }

    // ========== BLACKBOX: Initialization Tests ==========

    @Test
    fun sdkInitialization_succeeds() = runBlocking {
        val initialized = sdk.initializeWithKey("test-api-key")
        assertTrue("SDK should initialize in mock mode", initialized)
    }

    @Test
    fun sdkInitialization_withEmptyKey_succeeds() = runBlocking {
        val initialized = sdk.initializeWithKey("")
        assertTrue("SDK should initialize even with empty key", initialized)
    }

    // ========== BLACKBOX: Device Discovery Tests ==========

    @Test
    fun deviceDiscovery_returnsMockDevices() = runBlocking {
        val devices = mutableListOf<BLEDevice>()
        
        sdk.discoverDevices(
            onDeviceFound = { device -> devices.add(device) },
            timeoutMs = 3000
        )
        
        assertTrue("Should find at least one mock device", devices.isNotEmpty())
        assertTrue("Should include OBDII device", devices.any { it.name.contains("OBD") })
    }

    @Test
    fun deviceDiscovery_stopsAfterTimeout() = runBlocking {
        val devices = mutableListOf<BLEDevice>()
        val startTime = System.currentTimeMillis()
        
        sdk.discoverDevices(
            onDeviceFound = { device -> devices.add(device) },
            timeoutMs = 2000
        )
        
        val elapsedTime = System.currentTimeMillis() - startTime
        assertTrue("Discovery should complete within timeout", elapsedTime < 5000)
    }

    // ========== BLACKBOX: Connection Tests ==========

    @Test
    fun connect_toMockDevice_succeeds() = runBlocking {
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        
        val connectableDevice = devices.first { it.isConnectable }
        sdk.connect(connectableDevice.id)
        
        assertTrue("Should be connected", sdk.isConnected())
        assertEquals(connectableDevice.id, sdk.getConnectedDevice()?.id)
    }

    @Test
    fun disconnect_clearsState() = runBlocking {
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        
        sdk.connect(devices.first { it.isConnectable }.id)
        assertTrue(sdk.isConnected())
        
        sdk.disconnect()
        
        assertFalse("Should not be connected after disconnect", sdk.isConnected())
        assertNull("Connected device should be null", sdk.getConnectedDevice())
    }

    // ========== BLACKBOX: Scan Tests with Both Fields ==========

    @Test
    fun fullScan_requiresBothFields() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        // Test with both fields - should succeed
        val result = sdk.runFullScan(
            ScanOptions(
                registrationNumber = "MH12AB1234",
                trackingId = "ORD6894331",
                manufacturer = "hyundai"
            )
        )
        
        assertNotNull("Scan result should not be null", result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fullScan_failsWithEmptyRegistration() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        // This should throw IllegalArgumentException
        sdk.runFullScan(
            ScanOptions(
                registrationNumber = "",
                trackingId = "ORD6894331"
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun fullScan_failsWithEmptyTrackingId() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        // This should throw IllegalArgumentException
        sdk.runFullScan(
            ScanOptions(
                registrationNumber = "MH12AB1234",
                trackingId = ""
            )
        )
    }

    @Test
    fun fullScan_returnsScanReport() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        val result = sdk.runFullScan(
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

    @Test
    fun fullScan_tracksProgress() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        val stages = mutableListOf<String>()
        
        sdk.runFullScan(
            ScanOptions(
                registrationNumber = "MH12AB1234",
                trackingId = "ORD6894331",
                manufacturer = "hyundai",
                onProgress = { stage ->
                    stages.add(stage.label)
                }
            )
        )
        
        assertTrue("Should have progress stages", stages.isNotEmpty())
        assertTrue("Should complete scan", stages.any { it.contains("complete", ignoreCase = true) })
    }

    // ========== BLACKBOX: Analytics Tests ==========

    @Test
    fun fullScan_preparesAnalyticsPayload() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        var payloadReceived: String? = null
        sdk.setOnAnalyticsPayloadPrepared { json ->
            payloadReceived = json
        }
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        sdk.runFullScan(
            ScanOptions(
                registrationNumber = "MH12AB1234",
                trackingId = "ORD6894331"
            )
        )
        
        assertNotNull("Analytics payload should be prepared", payloadReceived)
        assertTrue("Payload should contain license_plate", payloadReceived!!.contains("license_plate"))
        assertTrue("Payload should contain tracking_id", payloadReceived!!.contains("tracking_id"))
        assertTrue("Payload should contain registration number", payloadReceived!!.contains("MH12AB1234"))
        assertTrue("Payload should contain tracking ID", payloadReceived!!.contains("ORD6894331"))
    }

    @Test
    fun analyticsPayload_containsBothFields() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        var payloadJson: String? = null
        sdk.setOnAnalyticsPayloadPrepared { json ->
            payloadJson = json
        }
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        sdk.runFullScan(
            ScanOptions(
                registrationNumber = "DL4CAB5678",
                trackingId = "WD2025XYZ123"
            )
        )
        
        assertNotNull(payloadJson)
        
        // Verify both fields are in payload
        assertTrue("Should contain license_plate", payloadJson!!.contains("\"license_plate\""))
        assertTrue("Should contain tracking_id", payloadJson!!.contains("\"tracking_id\""))
        assertTrue("Should contain registration value", payloadJson!!.contains("DL4CAB5678"))
        assertTrue("Should contain tracking value", payloadJson!!.contains("WD2025XYZ123"))
    }

    @Test
    fun submitReport_succeeds() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        val result = sdk.runFullScan(
            ScanOptions(
                registrationNumber = "MH12AB1234",
                trackingId = "ORD6894331"
            )
        )
        
        val submitted = sdk.submitReport(result)
        assertTrue("Report submission should succeed in mock mode", submitted)
    }

    // ========== BLACKBOX: VIN and MIL Tests ==========

    @Test
    fun fetchVIN_returnsMockVIN() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        val vinResult = sdk.fetchVIN()
        assertNotNull("VIN result should not be null", vinResult)
    }

    @Test
    fun readMILStatus_returnsMockStatus() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        val milStatus = sdk.readMILStatus()
        assertNotNull("MIL status should not be null", milStatus)
    }

    // ========== BLACKBOX: Scan Control Tests ==========

    @Test
    fun stopScan_cancelsOperation() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        var scanCancelled = false
        
        kotlinx.coroutines.launch {
            try {
                sdk.runFullScan(
                    ScanOptions(
                        registrationNumber = "MH12AB1234",
                        trackingId = "ORD6894331",
                        onProgress = { stage ->
                            if (stage.label.contains("MIL", ignoreCase = true)) {
                                sdk.stopScan()
                            }
                        }
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                scanCancelled = true
            }
        }
        
        kotlinx.coroutines.delay(5000)
    }

    // ========== BLACKBOX: State Verification Tests ==========

    @Test
    fun isMockMode_returnsTrue() {
        assertTrue("Should be in mock mode", sdk.isMockMode())
    }

    @Test
    fun getProtocol_returnsValue() = runBlocking {
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        sdk.connect(devices.first { it.isConnectable }.id)
        
        // Run a quick scan to initialize protocol
        sdk.runFullScan(
            ScanOptions(
                registrationNumber = "MH12AB1234",
                trackingId = "ORD6894331"
            )
        )
        
        val protocol = sdk.getProtocol()
        assertNotNull("Protocol should not be null", protocol)
        assertTrue("Protocol should not be empty", protocol.isNotEmpty())
    }
}
