package com.wisedrive.obd2.adapter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wisedrive.obd2.models.BLEDevice
import com.wisedrive.obd2.util.Logger
import kotlinx.coroutines.*
import java.util.UUID

/**
 * BLE (Bluetooth Low Energy) GATT Adapter for ELM327 BLE adapters
 * SECONDARY adapter - some newer ELM327 adapters use BLE instead of Classic SPP
 */
@SuppressLint("MissingPermission")
class BLEAdapter(private val context: Context) : BluetoothAdapterInterface {

    companion object {
        private const val TAG = "BLEAdapter"
        
        // Common ELM327 BLE Service UUIDs
        private val SERVICE_UUIDS = listOf(
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),  // Most common
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),  // Alternate
            UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"),  // Some adapters
            UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")   // Generic Access
        )
        
        // Common ELM327 BLE Characteristic UUIDs
        private val WRITE_CHAR_UUIDS = listOf(
            UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f")
        )
        
        private val NOTIFY_CHAR_UUIDS = listOf(
            UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f")
        )
        
        // Client Characteristic Configuration Descriptor UUID
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        
        // Permissions
        private val PERMISSIONS_ANDROID_12_PLUS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        
        private val PERMISSIONS_LEGACY = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val bluetoothManager: BluetoothManager? = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedDevice: BLEDevice? = null
    private var dataCallback: ((String) -> Unit)? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning = false
    private var connectionContinuation: CancellableContinuation<Unit>? = null

    private val responseBuffer = StringBuilder()

    override suspend fun isAvailable(): Boolean {
        return bluetoothAdapter != null && 
               bluetoothAdapter.isEnabled && 
               context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)
    }

    override suspend fun requestPermissions(activity: Activity): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PERMISSIONS_ANDROID_12_PLUS
        } else {
            PERMISSIONS_LEGACY
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (missingPermissions.isEmpty()) {
            true
        } else {
            ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), 1002)
            false
        }
    }

    override suspend fun startScan(onDeviceFound: (BLEDevice) -> Unit) {
        withContext(Dispatchers.IO) {
            if (!isAvailable()) {
                throw IllegalStateException("BLE is not available or not enabled")
            }

            val foundDevices = mutableSetOf<String>()
            isScanning = true

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    val address = device.address
                    
                    if (!foundDevices.contains(address)) {
                        val name = device.name ?: result.scanRecord?.deviceName
                        if (isOBDDevice(name)) {
                            foundDevices.add(address)
                            val bleDevice = BLEDevice(
                                id = address,
                                name = name ?: "Unknown BLE Device",
                                rssi = result.rssi,
                                isConnectable = true
                            )
                            onDeviceFound(bleDevice)
                            Logger.d(TAG, "Found BLE device: $name ($address)")
                        }
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { result ->
                        onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Logger.e(TAG, "BLE scan failed with error code: $errorCode")
                    isScanning = false
                }
            }

            // Build scan filters for ELM327 service UUIDs
            val filters = SERVICE_UUIDS.map { uuid ->
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(uuid))
                    .build()
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Start scanning with filters (if supported) or without
            try {
                bleScanner?.startScan(filters, settings, scanCallback)
            } catch (e: Exception) {
                // Fallback to scan without filters
                bleScanner?.startScan(scanCallback)
            }
        }
    }

    override suspend fun stopScan() {
        isScanning = false
        scanCallback?.let {
            try {
                bleScanner?.stopScan(it)
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to stop scan: ${e.message}")
            }
        }
        scanCallback = null
    }

    override suspend fun connect(deviceId: String) {
        // Stop any ongoing scan first (outside the continuation)
        stopScan()
        
        suspendCancellableCoroutine { continuation ->
            connectionContinuation = continuation

            val device = bluetoothAdapter?.getRemoteDevice(deviceId)
                ?: throw IllegalArgumentException("Device not found: $deviceId")

        Logger.d(TAG, "Connecting to BLE device: ${device.name} ($deviceId)")

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Logger.i(TAG, "Connected to GATT server")
                        bluetoothGatt = gatt
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Logger.i(TAG, "Disconnected from GATT server")
                        cleanup()
                        if (connectionContinuation?.isActive == true) {
                            connectionContinuation?.resumeWith(Result.failure(Exception("Disconnected")))
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Logger.d(TAG, "Services discovered")
                    
                    // Find write and notify characteristics
                    for (service in gatt.services) {
                        Logger.d(TAG, "Service: ${service.uuid}")
                        
                        for (characteristic in service.characteristics) {
                            Logger.d(TAG, "  Characteristic: ${characteristic.uuid}")
                            
                            // Find writable characteristic
                            if (writeCharacteristic == null && 
                                (WRITE_CHAR_UUIDS.contains(characteristic.uuid) ||
                                 (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0)) {
                                writeCharacteristic = characteristic
                                Logger.d(TAG, "Found write characteristic: ${characteristic.uuid}")
                            }
                            
                            // Find notifiable characteristic
                            if (notifyCharacteristic == null &&
                                (NOTIFY_CHAR_UUIDS.contains(characteristic.uuid) ||
                                 (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)) {
                                notifyCharacteristic = characteristic
                                Logger.d(TAG, "Found notify characteristic: ${characteristic.uuid}")
                            }
                        }
                    }

                    if (writeCharacteristic != null) {
                        // Enable notifications if available
                        notifyCharacteristic?.let { char ->
                            gatt.setCharacteristicNotification(char, true)
                            
                            // Write to CCCD to enable notifications
                            val descriptor = char.getDescriptor(CCCD_UUID)
                            descriptor?.let {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                } else {
                                    @Suppress("DEPRECATION")
                                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(it)
                                }
                            }
                        }

                        connectedDevice = BLEDevice(
                            id = deviceId,
                            name = device.name ?: "BLE ELM327",
                            rssi = -50,
                            isConnectable = true
                        )
                        
                        connectionContinuation?.resumeWith(Result.success(Unit))
                        connectionContinuation = null
                    } else {
                        connectionContinuation?.resumeWith(
                            Result.failure(Exception("No writable characteristic found"))
                        )
                        connectionContinuation = null
                    }
                } else {
                    connectionContinuation?.resumeWith(
                        Result.failure(Exception("Service discovery failed: $status"))
                    )
                    connectionContinuation = null
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                val data = String(value, Charsets.US_ASCII)
                handleIncomingData(data)
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                @Suppress("DEPRECATION")
                val data = String(characteristic.value, Charsets.US_ASCII)
                handleIncomingData(data)
            }
        }

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }

        continuation.invokeOnCancellation {
            cleanup()
        }
        }
    }

    private fun handleIncomingData(data: String) {
        responseBuffer.append(data)
        
        // Check for complete response (ends with '>')
        if (responseBuffer.contains(">")) {
            val response = responseBuffer.toString()
            responseBuffer.clear()
            dataCallback?.invoke(response)
        }
    }

    override suspend fun disconnect() {
        cleanup()
    }

    private fun cleanup() {
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Logger.w(TAG, "Error closing GATT: ${e.message}")
        }
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        connectedDevice = null
        responseBuffer.clear()
    }

    override suspend fun write(data: String) {
        withContext(Dispatchers.IO) {
            val gatt = bluetoothGatt ?: throw Exception("Not connected")
            val characteristic = writeCharacteristic ?: throw Exception("No write characteristic")

            val dataWithCR = if (data.endsWith("\r")) data else "$data\r"
            val bytes = dataWithCR.toByteArray(Charsets.US_ASCII)

            Logger.d(TAG, "Writing: $dataWithCR")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = bytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        }
    }

    override suspend fun read(): String = withContext(Dispatchers.IO) {
        // For BLE, data comes via notifications
        // This method waits for data with timeout
        val startTime = System.currentTimeMillis()
        val timeout = 8000L
        
        while (System.currentTimeMillis() - startTime < timeout) {
            if (responseBuffer.contains(">")) {
                val response = responseBuffer.toString()
                responseBuffer.clear()
                return@withContext response
            }
            delay(50)
        }
        
        // Return whatever we have
        val response = responseBuffer.toString()
        responseBuffer.clear()
        response
    }

    override fun onData(callback: (String) -> Unit): () -> Unit {
        dataCallback = callback
        return {
            dataCallback = null
        }
    }

    override fun isConnected(): Boolean {
        return bluetoothGatt != null && connectedDevice != null
    }

    override fun getConnectedDevice(): BLEDevice? {
        return connectedDevice
    }

    private fun isOBDDevice(name: String?): Boolean {
        if (name == null) return false
        val upperName = name.uppercase()
        val patterns = listOf(
            "OBD", "ELM", "VLINK", "VEEPEAK", "BAFX", "SCAN", "KONNWEI",
            "FIXD", "CARISTA", "BLUEDRIVER", "TORQUE", "V-LINK", "LAUNCH"
        )
        return patterns.any { upperName.contains(it) }
    }
}
