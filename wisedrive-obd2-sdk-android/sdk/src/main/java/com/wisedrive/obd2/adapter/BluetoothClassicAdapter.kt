package com.wisedrive.obd2.adapter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wisedrive.obd2.models.BLEDevice
import com.wisedrive.obd2.util.Logger
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.UUID

/**
 * Bluetooth Classic SPP (Serial Port Profile) Adapter
 * PRIMARY adapter for Android ELM327 communication
 * 
 * Implements 5 connection fallback strategies for maximum device compatibility
 */
@SuppressLint("MissingPermission")
class BluetoothClassicAdapter(private val context: Context) : BluetoothAdapterInterface {

    companion object {
        private const val TAG = "BTClassicAdapter"
        
        // Standard SPP UUID for ELM327 adapters
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        // ELM327 device name patterns
        private val OBD_NAME_PATTERNS = listOf(
            "OBD", "ELM", "VLINK", "VEEPEAK", "BAFX", "SCAN", "KONNWEI",
            "FIXD", "CARISTA", "BLUEDRIVER", "TORQUE", "V-LINK", "LAUNCH"
        )
        
        // Permissions required for Bluetooth
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

    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var connectedDevice: BLEDevice? = null
    private var dataCallback: ((String) -> Unit)? = null
    private var readJob: Job? = null
    private var discoveryReceiver: BroadcastReceiver? = null
    private var isScanning = false

    // Response buffer for collecting data until '>' prompt
    private val responseBuffer = StringBuilder()

    override suspend fun isAvailable(): Boolean {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
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
            ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), 1001)
            false
        }
    }

    override suspend fun startScan(onDeviceFound: (BLEDevice) -> Unit) {
        withContext(Dispatchers.IO) {
            if (!isAvailable()) {
                throw IllegalStateException("Bluetooth is not available or not enabled")
            }

            val foundDevices = mutableSetOf<String>()
            isScanning = true

            // Step 1: Return bonded (paired) devices first
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                if (isOBDDevice(device.name)) {
                    val bleDevice = BLEDevice(
                        id = device.address,
                        name = device.name ?: "Unknown",
                        rssi = -50, // Bonded devices don't have RSSI
                        isConnectable = true
                    )
                    foundDevices.add(device.address)
                    withContext(Dispatchers.Main) {
                        onDeviceFound(bleDevice)
                    }
                    Logger.d(TAG, "Found bonded device: ${device.name} (${device.address})")
                }
            }

            // Step 2: Start discovery for new devices
            discoveryReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device: BluetoothDevice? = 
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                }
                            
                            device?.let {
                                if (!foundDevices.contains(it.address) && isOBDDevice(it.name)) {
                                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                                    val bleDevice = BLEDevice(
                                        id = it.address,
                                        name = it.name ?: "Unknown OBD Device",
                                        rssi = rssi,
                                        isConnectable = true
                                    )
                                    foundDevices.add(it.address)
                                    onDeviceFound(bleDevice)
                                    Logger.d(TAG, "Discovered device: ${it.name} (${it.address})")
                                }
                            }
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            isScanning = false
                            Logger.d(TAG, "Discovery finished")
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(discoveryReceiver, filter)

            // Cancel any existing discovery and start new
            bluetoothAdapter?.cancelDiscovery()
            bluetoothAdapter?.startDiscovery()
        }
    }

    override suspend fun stopScan() {
        withContext(Dispatchers.IO) {
            isScanning = false
            bluetoothAdapter?.cancelDiscovery()
            discoveryReceiver?.let {
                try {
                    context.unregisterReceiver(it)
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed to unregister receiver: ${e.message}")
                }
            }
            discoveryReceiver = null
        }
    }

    /**
     * Connect to ELM327 device using 5 fallback strategies
     */
    override suspend fun connect(deviceId: String) = withContext(Dispatchers.IO) {
        stopScan()

        val device = bluetoothAdapter?.getRemoteDevice(deviceId)
            ?: throw IllegalArgumentException("Device not found: $deviceId")

        Logger.d(TAG, "Attempting to connect to: ${device.name} ($deviceId)")

        var lastException: Exception? = null
        var connected = false
        var strategyUsed = ""

        // Strategy 1: Standard SPP UUID
        if (!connected) {
            try {
                Logger.d(TAG, "Strategy 1: Standard SPP UUID")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect()
                connected = true
                strategyUsed = "Standard SPP UUID"
            } catch (e: Exception) {
                Logger.w(TAG, "Strategy 1 failed: ${e.message}")
                closeSocket()
                lastException = e
            }
        }

        // Strategy 2: Secure RFCOMM via reflection
        if (!connected) {
            try {
                Logger.d(TAG, "Strategy 2: Secure RFCOMM reflection")
                val method: Method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                bluetoothSocket = method.invoke(device, 1) as BluetoothSocket
                bluetoothSocket?.connect()
                connected = true
                strategyUsed = "Secure RFCOMM reflection"
            } catch (e: Exception) {
                Logger.w(TAG, "Strategy 2 failed: ${e.message}")
                closeSocket()
                lastException = e
            }
        }

        // Strategy 3: Insecure RFCOMM
        if (!connected) {
            try {
                Logger.d(TAG, "Strategy 3: Insecure RFCOMM")
                bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect()
                connected = true
                strategyUsed = "Insecure RFCOMM"
            } catch (e: Exception) {
                Logger.w(TAG, "Strategy 3 failed: ${e.message}")
                closeSocket()
                lastException = e
            }
        }

        // Strategy 4: Insecure RFCOMM via reflection
        if (!connected) {
            try {
                Logger.d(TAG, "Strategy 4: Insecure RFCOMM reflection")
                val method: Method = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.java)
                bluetoothSocket = method.invoke(device, 1) as BluetoothSocket
                bluetoothSocket?.connect()
                connected = true
                strategyUsed = "Insecure RFCOMM reflection"
            } catch (e: Exception) {
                Logger.w(TAG, "Strategy 4 failed: ${e.message}")
                closeSocket()
                lastException = e
            }
        }

        // Strategy 5: Try different RFCOMM channels (2-10)
        if (!connected) {
            for (channel in 2..10) {
                try {
                    Logger.d(TAG, "Strategy 5: RFCOMM channel $channel")
                    val method: Method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                    bluetoothSocket = method.invoke(device, channel) as BluetoothSocket
                    bluetoothSocket?.connect()
                    connected = true
                    strategyUsed = "RFCOMM channel $channel"
                    break
                } catch (e: Exception) {
                    Logger.w(TAG, "Strategy 5 channel $channel failed: ${e.message}")
                    closeSocket()
                    lastException = e
                }
            }
        }

        if (!connected) {
            throw IOException("Failed to connect to device after all strategies. Last error: ${lastException?.message}")
        }

        Logger.i(TAG, "Connected using strategy: $strategyUsed")

        // Setup streams
        inputStream = bluetoothSocket?.inputStream
        outputStream = bluetoothSocket?.outputStream

        connectedDevice = BLEDevice(
            id = deviceId,
            name = device.name ?: "ELM327",
            rssi = -50,
            isConnectable = true
        )

        // Start reading data
        startReadLoop()
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            Logger.d(TAG, "Disconnecting...")
            readJob?.cancel()
            readJob = null
            closeSocket()
            connectedDevice = null
            responseBuffer.clear()
        }
    }

    private fun closeSocket() {
        try {
            inputStream?.close()
        } catch (e: Exception) { /* ignore */ }
        try {
            outputStream?.close()
        } catch (e: Exception) { /* ignore */ }
        try {
            bluetoothSocket?.close()
        } catch (e: Exception) { /* ignore */ }
        inputStream = null
        outputStream = null
        bluetoothSocket = null
    }

    override suspend fun write(data: String) = withContext(Dispatchers.IO) {
        val stream = outputStream ?: throw IOException("Not connected")
        
        // Append carriage return as per ELM327 protocol
        val dataWithCR = if (data.endsWith("\r")) data else "$data\r"
        
        Logger.d(TAG, "Writing: $dataWithCR")
        stream.write(dataWithCR.toByteArray(Charsets.US_ASCII))
        stream.flush()
    }

    override suspend fun read(): String = withContext(Dispatchers.IO) {
        val stream = inputStream ?: throw IOException("Not connected")
        val buffer = ByteArray(1024)
        val response = StringBuilder()
        
        // Read until '>' prompt or timeout
        val startTime = System.currentTimeMillis()
        val timeout = 8000L // 8 seconds timeout
        
        while (System.currentTimeMillis() - startTime < timeout) {
            if (stream.available() > 0) {
                val bytesRead = stream.read(buffer)
                if (bytesRead > 0) {
                    val chunk = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                    response.append(chunk)
                    
                    // Check for '>' prompt
                    if (response.contains(">")) {
                        break
                    }
                }
            } else {
                delay(10)
            }
        }
        
        response.toString()
    }

    override fun onData(callback: (String) -> Unit): () -> Unit {
        dataCallback = callback
        return {
            dataCallback = null
        }
    }

    override fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }

    override fun getConnectedDevice(): BLEDevice? {
        return connectedDevice
    }

    private fun startReadLoop() {
        readJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            
            while (isActive && bluetoothSocket?.isConnected == true) {
                try {
                    val stream = inputStream ?: break
                    
                    if (stream.available() > 0) {
                        val bytesRead = stream.read(buffer)
                        if (bytesRead > 0) {
                            val data = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                            responseBuffer.append(data)
                            
                            // Check if we have a complete response (ends with '>')
                            if (responseBuffer.contains(">")) {
                                val response = responseBuffer.toString()
                                responseBuffer.clear()
                                
                                withContext(Dispatchers.Main) {
                                    dataCallback?.invoke(response)
                                }
                            }
                        }
                    } else {
                        delay(10)
                    }
                } catch (e: IOException) {
                    Logger.e(TAG, "Read error: ${e.message}")
                    break
                }
            }
            
            Logger.d(TAG, "Read loop ended")
        }
    }

    private fun isOBDDevice(name: String?): Boolean {
        if (name == null) return false
        val upperName = name.uppercase()
        return OBD_NAME_PATTERNS.any { pattern -> upperName.contains(pattern) }
    }
}
