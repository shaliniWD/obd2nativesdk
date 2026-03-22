package com.wisedrive.obd2.adapter

import android.app.Activity
import com.wisedrive.obd2.models.BLEDevice

/**
 * Bluetooth adapter interface for OBD communication
 */
interface BluetoothAdapterInterface {
    
    /**
     * Check if Bluetooth is available on the device
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * Request required Bluetooth permissions
     */
    suspend fun requestPermissions(activity: Activity): Boolean
    
    /**
     * Start scanning for OBD devices
     */
    suspend fun startScan(onDeviceFound: (BLEDevice) -> Unit)
    
    /**
     * Stop scanning for devices
     */
    suspend fun stopScan()
    
    /**
     * Connect to a device by ID
     */
    suspend fun connect(deviceId: String)
    
    /**
     * Disconnect from current device
     */
    suspend fun disconnect()
    
    /**
     * Write data to the connected device
     */
    suspend fun write(data: String)
    
    /**
     * Read data from the connected device
     */
    suspend fun read(): String
    
    /**
     * Register a callback for incoming data
     * @return Unsubscribe function
     */
    fun onData(callback: (String) -> Unit): () -> Unit
    
    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean
    
    /**
     * Get the currently connected device
     */
    fun getConnectedDevice(): BLEDevice?
}
