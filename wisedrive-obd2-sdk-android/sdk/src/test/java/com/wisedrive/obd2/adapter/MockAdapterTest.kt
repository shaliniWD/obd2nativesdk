package com.wisedrive.obd2.adapter

import com.wisedrive.obd2.models.BLEDevice
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for MockAdapter
 */
class MockAdapterTest {

    private val mockAdapter = MockAdapter()

    @Test
    fun adapterIsAlwaysAvailable() {
        runBlocking {
            assertTrue(mockAdapter.isAvailable())
        }
    }

    @Test
    fun scanReturnsMockDevices() {
        runBlocking {
            val devices = mutableListOf<BLEDevice>()
            
            mockAdapter.startScan { device ->
                devices.add(device)
            }
            
            // Should receive 4 mock devices
            assertEquals(4, devices.size)
            assertTrue(devices.any { it.name == "OBDII" })
            assertTrue(devices.any { it.name == "ELM327-BT" })
        }
    }

    @Test
    fun connectToValidDeviceSucceeds() {
        runBlocking {
            assertFalse(mockAdapter.isConnected())
            
            mockAdapter.connect("00:11:22:33:44:55")
            
            assertTrue(mockAdapter.isConnected())
            assertNotNull(mockAdapter.getConnectedDevice())
            assertEquals("OBDII", mockAdapter.getConnectedDevice()?.name)
        }
    }

    @Test
    fun connectToInvalidDeviceFails() {
        runBlocking {
            try {
                mockAdapter.connect("XX:XX:XX:XX:XX:XX")
                fail("Should throw exception for unknown device")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("not found") == true)
            }
        }
    }

    @Test
    fun connectToNonConnectableDeviceFails() {
        runBlocking {
            try {
                mockAdapter.connect("99:88:77:66:55:44") // "Unknown Device" is not connectable
                fail("Should throw exception for non-connectable device")
            } catch (e: Exception) {
                assertTrue(e.message?.contains("not connectable") == true)
            }
        }
    }

    @Test
    fun disconnectClearsConnectionState() {
        runBlocking {
            mockAdapter.connect("00:11:22:33:44:55")
            assertTrue(mockAdapter.isConnected())
            
            mockAdapter.disconnect()
            
            assertFalse(mockAdapter.isConnected())
            assertNull(mockAdapter.getConnectedDevice())
        }
    }

    @Test
    fun writeWithoutConnectionThrows() {
        runBlocking {
            try {
                mockAdapter.write("ATZ")
                fail("Should throw when not connected")
            } catch (e: Exception) {
                assertTrue(e.message?.contains("Not connected") == true)
            }
        }
    }

    @Test
    fun readWithoutConnectionThrows() {
        runBlocking {
            try {
                mockAdapter.read()
                fail("Should throw when not connected")
            } catch (e: Exception) {
                assertTrue(e.message?.contains("Not connected") == true)
            }
        }
    }

    @Test
    fun writeAndReadATCommand() {
        runBlocking {
            mockAdapter.connect("00:11:22:33:44:55")
            
            var response: String? = null
            mockAdapter.onData { data ->
                response = data
            }
            
            mockAdapter.write("ATZ")
            
            // Wait a bit for async response
            Thread.sleep(300)
            
            assertNotNull(response)
            assertTrue(response?.contains("ELM327") == true)
        }
    }

    @Test
    fun readReturnsMockResponseForOBDCommands() {
        runBlocking {
            mockAdapter.connect("00:11:22:33:44:55")
            
            // First write then read
            mockAdapter.write("0105") // Coolant temp
            val response = mockAdapter.read()
            
            assertTrue(response.contains("41 05"))
        }
    }

    @Test
    fun dataCallbackCanBeUnregistered() {
        runBlocking {
            var callCount = 0
            val unsubscribe = mockAdapter.onData { callCount++ }
            
            mockAdapter.connect("00:11:22:33:44:55")
            mockAdapter.write("ATZ")
            Thread.sleep(300)
            
            val firstCount = callCount
            
            unsubscribe()
            
            mockAdapter.write("ATE0")
            Thread.sleep(300)
            
            // Should not have increased after unsubscribe
            assertEquals(firstCount, callCount)
        }
    }
}
