package com.wisedrive.obd2.adapter

import android.app.Activity
import com.wisedrive.obd2.models.BLEDevice
import kotlinx.coroutines.delay

/**
 * Mock Bluetooth Adapter for testing without hardware
 * Simulates ELM327 responses for all OBD commands
 */
class MockAdapter : BluetoothAdapterInterface {

    companion object {
        private const val TAG = "MockAdapter"
    }

    private var connected = false
    private var connectedDevice: BLEDevice? = null
    private var dataCallback: ((String) -> Unit)? = null
    private var lastCommand: String = ""

    // Mock devices
    private val mockDevices = listOf(
        BLEDevice("00:11:22:33:44:55", "OBDII", -45, true),
        BLEDevice("AA:BB:CC:DD:EE:FF", "ELM327-BT", -60, true),
        BLEDevice("11:22:33:44:55:66", "V-LINK", -72, true),
        BLEDevice("99:88:77:66:55:44", "Unknown Device", -85, false)
    )

    // Mock responses for OBD commands
    private val mockResponses = mapOf(
        // AT Commands - Initialization
        "ATZ" to "ELM327 v1.5\r\r>",
        "ATE0" to "OK\r\r>",
        "ATL1" to "OK\r\r>",
        "ATS1" to "OK\r\r>",
        "ATH1" to "OK\r\r>",
        "ATCAF1" to "OK\r\r>",
        "ATAT2" to "OK\r\r>",
        "ATST FF" to "OK\r\r>",
        "ATAL" to "OK\r\r>",
        "ATCFC1" to "OK\r\r>",
        "ATSP0" to "OK\r\r>",
        "ATDPN" to "A6\r\r>",  // CAN 11-bit 500kbps
        "AT" to "OK\r\r>",
        
        // AT Commands - Header control
        "ATSH 7E0" to "OK\r\r>",
        "ATSH 7E1" to "OK\r\r>",
        "ATSH 7E2" to "OK\r\r>",
        "ATSH 7DF" to "OK\r\r>",
        "ATCRA 7E8" to "OK\r\r>",
        "ATCRA 7E9" to "OK\r\r>",
        "ATCRA 7EA" to "OK\r\r>",
        "ATCRA" to "OK\r\r>",
        
        // Mode 01 - Live Data
        "0100" to "7E8 06 41 00 BE 3E B8 13\r\r>",  // Supported PIDs
        "0101" to "7E8 06 41 01 82 07 65 00\r\r>",  // MIL ON, 2 DTCs
        "0104" to "7E8 03 41 04 64\r\r>",  // Engine Load 39%
        "0105" to "7E8 03 41 05 7B\r\r>",  // Coolant Temp 83°C
        "0106" to "7E8 03 41 06 80\r\r>",  // STFT Bank 1
        "0107" to "7E8 03 41 07 7D\r\r>",  // LTFT Bank 1
        "010B" to "7E8 03 41 0B 65\r\r>",  // Intake MAP 101 kPa
        "010C" to "7E8 04 41 0C 1A F8\r\r>",  // RPM 1726
        "010D" to "7E8 03 41 0D 45\r\r>",  // Speed 69 km/h
        "010E" to "7E8 03 41 0E 88\r\r>",  // Timing Advance 4°
        "010F" to "7E8 03 41 0F 4B\r\r>",  // Intake Air Temp 35°C
        "0110" to "7E8 04 41 10 01 4D\r\r>",  // MAF 3.33 g/s
        "0111" to "7E8 03 41 11 1A\r\r>",  // Throttle 10.2%
        "011F" to "7E8 04 41 1F 00 5A\r\r>",  // Runtime 90 sec
        "0121" to "7E8 04 41 21 00 32\r\r>",  // MIL Distance 50 km
        "012F" to "7E8 03 41 2F 99\r\r>",  // Fuel Level 60%
        "0133" to "7E8 03 41 33 65\r\r>",  // Baro Pressure 101 kPa
        "0142" to "7E8 04 41 42 37 DC\r\r>",  // Control Module Voltage 14.3V
        "0146" to "7E8 03 41 46 4B\r\r>",  // Ambient Temp 35°C
        "015C" to "7E8 03 41 5C 73\r\r>",  // Oil Temp 75°C
        "015E" to "7E8 04 41 5E 00 64\r\r>",  // Fuel Rate 5.0 L/h
        
        // Mode 03 - Stored DTCs
        "03" to "7E8 06 43 02 01 33 01 01\r7E9 04 43 01 07 00\r\r>",  // P0133, P0101 from Engine; P0700 from Trans
        
        // Mode 07 - Pending DTCs
        "07" to "7E8 04 47 01 01 16\r\r>",  // P0116 pending
        
        // Mode 0A - Permanent DTCs
        "0A" to "7E8 02 4A 00\r\r>",  // No permanent DTCs
        
        // Mode 09 - VIN
        "0902" to "014\r0: 49 02 01 4D 41 54\r1: 31 32 33 34 35 36 37\r2: 38 39 30 31 32 33 34\r\r>",  // MAT1234567890123
        
        // UDS - Enhanced DTCs (Service 0x19)
        "1902FF" to "59 02 FF 01 33 28 01 01 68 04 20 28\r\r>",  // P0133, P0101, P0420
        
        // Manufacturer-specific module scan responses
        "ATSH 7B0" to "OK\r\r>",  // ABS
        "ATCRA 7B8" to "OK\r\r>",
        "ATSH 7C0" to "OK\r\r>",  // Airbag
        "ATCRA 7C8" to "OK\r\r>",
        "ATSH 720" to "OK\r\r>",  // BCM
        "ATCRA 728" to "OK\r\r>",
        "ATSH 726" to "OK\r\r>",  // Instrument
        "ATCRA 72E" to "OK\r\r>",
        "ATSH 727" to "OK\r\r>",  // Climate
        "ATCRA 72F" to "OK\r\r>",
        "ATSH 730" to "OK\r\r>",  // Steering
        "ATCRA 738" to "OK\r\r>"
    )

    override suspend fun isAvailable(): Boolean = true

    override suspend fun requestPermissions(activity: Activity): Boolean = true

    override suspend fun startScan(onDeviceFound: (BLEDevice) -> Unit) {
        // Simulate scan delay
        delay(200)
        
        mockDevices.forEach { device ->
            delay(100)
            onDeviceFound(device)
        }
    }

    override suspend fun stopScan() {
        // No-op for mock
    }

    override suspend fun connect(deviceId: String) {
        // Simulate connection delay
        delay(500)
        
        val device = mockDevices.find { it.id == deviceId }
            ?: throw IllegalArgumentException("Device not found: $deviceId")
        
        if (!device.isConnectable) {
            throw Exception("Device is not connectable")
        }
        
        connected = true
        connectedDevice = device
    }

    override suspend fun disconnect() {
        delay(100)
        connected = false
        connectedDevice = null
    }

    override suspend fun write(data: String) {
        if (!connected) throw Exception("Not connected")
        
        // Store command for response generation
        lastCommand = data.trim().uppercase().replace("\r", "")
        
        // Simulate write delay
        delay(50)
        
        // Generate and send response
        val response = getMockResponse(lastCommand)
        
        // Simulate OBD response time
        val responseDelay = when {
            lastCommand.startsWith("AT") -> (100..200).random().toLong()
            lastCommand == "03" || lastCommand == "07" || lastCommand == "0A" -> (300..600).random().toLong()
            lastCommand.startsWith("19") -> (400..800).random().toLong()
            lastCommand.startsWith("09") -> (500..1000).random().toLong()
            else -> (100..300).random().toLong()
        }
        
        delay(responseDelay)
        dataCallback?.invoke(response)
    }

    override suspend fun read(): String {
        if (!connected) throw Exception("Not connected")
        
        // Return mock response based on last command
        return getMockResponse(lastCommand)
    }

    override fun onData(callback: (String) -> Unit): () -> Unit {
        dataCallback = callback
        return {
            dataCallback = null
        }
    }

    override fun isConnected(): Boolean = connected

    override fun getConnectedDevice(): BLEDevice? = connectedDevice

    private fun getMockResponse(command: String): String {
        // Check for exact match first
        mockResponses[command]?.let { return it }
        
        // Check for partial matches (AT commands with parameters)
        for ((key, value) in mockResponses) {
            if (command.startsWith(key.split(" ").first()) && key.contains(" ") && command.contains(" ")) {
                return value
            }
        }
        
        // Default responses for unknown commands
        return when {
            command.startsWith("AT") -> "OK\r\r>"
            command.startsWith("01") -> "7E8 03 41 ${command.substring(2)} 00\r\r>"  // Generic Mode 01 response
            command.startsWith("19") -> "59 02 FF\r\r>"  // No UDS DTCs
            else -> "?\r\r>"  // Unknown command
        }
    }
}
