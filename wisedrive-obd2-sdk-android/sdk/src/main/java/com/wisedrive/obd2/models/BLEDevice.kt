package com.wisedrive.obd2.models

/**
 * Represents a discovered Bluetooth device (Classic or BLE)
 */
data class BLEDevice(
    val id: String,           // MAC address for Classic, UUID for BLE
    val name: String?,        // Device advertised name (e.g., "OBDII", "ELM327")
    val rssi: Int,            // Signal strength in dBm
    val isConnectable: Boolean
)
