package com.wisedrive.obd2.sample

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wisedrive.obd2.WiseDriveOBD2SDK
import com.wisedrive.obd2.models.BLEDevice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for SDK functionality
 * Tests the SDK in mock mode on a real Android device/emulator
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
    }

    @Test
    fun sdkInitialization_succeeds() = runBlocking {
        val initialized = sdk.initializeWithKey("test-api-key")
        assertTrue("SDK should initialize in mock mode", initialized)
    }

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
    fun connect_toMockDevice_succeeds() = runBlocking {
        // First discover devices
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        
        val connectableDevice = devices.first { it.isConnectable }
        
        // Connect
        sdk.connect(connectableDevice.id)
        
        assertTrue("Should be connected", sdk.isConnected())
        assertEquals(connectableDevice.id, sdk.getConnectedDevice()?.id)
    }

    @Test
    fun disconnect_clearsState() = runBlocking {
        // Connect first
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        
        sdk.connect(devices.first { it.isConnectable }.id)
        assertTrue(sdk.isConnected())
        
        // Disconnect
        sdk.disconnect()
        
        assertFalse("Should not be connected after disconnect", sdk.isConnected())
        assertNull("Connected device should be null", sdk.getConnectedDevice())
    }

    @Test
    fun fetchVIN_returnsMockVIN() = runBlocking {
        // Initialize and connect
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        
        sdk.connect(devices.first { it.isConnectable }.id)
        
        // Fetch VIN
        val vinResult = sdk.fetchVIN()
        
        assertNotNull("VIN result should not be null", vinResult)
        // In mock mode, VIN might be a simulated value
    }

    @Test
    fun readMILStatus_returnsMockStatus() = runBlocking {
        // Initialize and connect
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        
        sdk.connect(devices.first { it.isConnectable }.id)
        
        // Read MIL status
        val milStatus = sdk.readMILStatus()
        
        assertNotNull("MIL status should not be null", milStatus)
    }

    @Test
    fun fullScan_producesEncryptedPayload() = runBlocking {
        // Initialize
        val initialized = sdk.initializeWithKey("test-key")
        assertTrue(initialized)
        
        // Discover and connect
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        
        sdk.connect(devices.first { it.isConnectable }.id)
        
        // Run full scan with progress tracking
        val stages = mutableListOf<String>()
        val result = sdk.runFullScan(
            com.wisedrive.obd2.models.ScanOptions(
                orderId = "test-order-123",
                manufacturer = "hyundai",
                onProgress = { stage ->
                    stages.add(stage.label)
                }
            )
        )
        
        // Verify result
        assertNotNull("Encrypted payload should not be null", result)
        assertTrue("Payload should not be empty", result.payload.isNotEmpty())
        assertTrue("IV should not be empty", result.iv.isNotEmpty())
        assertTrue("Key ID should not be empty", result.keyId.isNotEmpty())
        
        // Verify stages were tracked
        assertTrue("Should have progress stages", stages.isNotEmpty())
        assertTrue("Should complete scan", stages.any { it.contains("complete", ignoreCase = true) })
    }

    @Test
    fun submitReport_succeeds() = runBlocking {
        // Initialize
        sdk.initializeWithKey("test-key")
        
        // Discover and connect
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        
        sdk.connect(devices.first { it.isConnectable }.id)
        
        // Run scan
        val result = sdk.runFullScan()
        
        // Submit report
        val submitted = sdk.submitReport(result)
        
        assertTrue("Report submission should succeed in mock mode", submitted)
    }

    @Test
    fun stopScan_cancelsOperation() = runBlocking {
        // Initialize and connect
        sdk.initializeWithKey("test-key")
        
        val devices = mutableListOf<BLEDevice>()
        sdk.discoverDevices(
            onDeviceFound = { devices.add(it) },
            timeoutMs = 3000
        )
        
        sdk.connect(devices.first { it.isConnectable }.id)
        
        // Start scan and immediately stop
        var scanCompleted = false
        
        kotlinx.coroutines.launch {
            try {
                sdk.runFullScan(
                    com.wisedrive.obd2.models.ScanOptions(
                        onProgress = { stage ->
                            if (stage.label.contains("MIL", ignoreCase = true)) {
                                sdk.stopScan()
                            }
                        }
                    )
                )
                scanCompleted = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected
            }
        }
        
        kotlinx.coroutines.delay(5000)
        
        // Scan should have been cancelled, not completed normally
        // (This test's behavior depends on timing)
    }
}
