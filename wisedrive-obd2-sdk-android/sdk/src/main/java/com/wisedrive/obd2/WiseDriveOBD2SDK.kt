package com.wisedrive.obd2

import android.app.Activity
import android.content.Context
import com.google.gson.Gson
import com.wisedrive.obd2.adapter.*
import com.wisedrive.obd2.constants.*
import com.wisedrive.obd2.models.*
import com.wisedrive.obd2.network.*
import com.wisedrive.obd2.protocol.*
import com.wisedrive.obd2.security.*
import com.wisedrive.obd2.util.Logger
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * WiseDrive OBD2 SDK - Main Entry Point
 * 
 * Production-ready Android SDK for OBD-II vehicle diagnostics
 * Supports Bluetooth Classic (SPP) and BLE ELM327 adapters
 * 
 * Usage:
 * ```
 * // Initialize
 * val sdk = WiseDriveOBD2SDK.initialize(context)
 * sdk.initializeWithKey(apiKey, baseUrl)
 * 
 * // Scan for devices
 * sdk.discoverDevices { device -> ... }
 * 
 * // Connect and scan
 * sdk.connect(deviceId)
 * val result = sdk.runFullScan(ScanOptions(
 *     registrationNumber = "MH12AB1234",
 *     manufacturer = "hyundai"
 * ))
 * 
 * // Submit report (client's backend)
 * sdk.submitReport(result)
 * ```
 */
class WiseDriveOBD2SDK private constructor(
    private val context: Context,
    private val useMock: Boolean = false
) {
    companion object {
        private const val TAG = "WiseDriveOBD2SDK"
        
        @Volatile
        private var instance: WiseDriveOBD2SDK? = null

        /**
         * Initialize SDK singleton
         */
        fun initialize(context: Context, useMock: Boolean = false): WiseDriveOBD2SDK {
            return instance ?: synchronized(this) {
                instance ?: WiseDriveOBD2SDK(context.applicationContext, useMock).also {
                    instance = it
                }
            }
        }

        /**
         * Get existing SDK instance
         */
        fun getInstance(): WiseDriveOBD2SDK {
            return instance ?: throw IllegalStateException(
                "WiseDriveOBD2SDK not initialized. Call WiseDriveOBD2SDK.initialize(context) first."
            )
        }
    }

    private val adapter: BluetoothAdapterInterface = if (useMock) {
        MockAdapter()
    } else {
        BluetoothClassicAdapter(context)
    }
    
    private val elm327: ELM327Service = ELM327Service(adapter)
    private val securityManager = SDKSecurityManager()
    private val apiClient: APIClientInterface = if (useMock) MockAPIClient() else APIClient()
    private val gson = Gson()
    
    // Internal analytics handler
    private lateinit var wiseDriveAnalytics: WiseDriveAnalytics
    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Last scan report for submitReport
    private var lastScanReport: ScanReport? = null
    private var lastApiPayload: APIPayload? = null
    
    private var stopRequested = false
    private var isInitialized = false

    // ─── INITIALIZATION ──────────────────────────────────────

    /**
     * Initialize SDK with API key and fetch encryption key from backend
     * MUST be called before any scan operations
     */
    suspend fun initializeWithKey(
        apiKey: String,
        baseUrl: String = "https://wisedrive.com:81"
    ): Boolean = withContext(Dispatchers.IO) {
        Logger.i(TAG, "Initializing SDK...")
        
        // Run integrity check (skip in mock mode for development)
        if (!useMock) {
            val integrity = IntegrityChecker.verifyEnvironment(context)
            if (!integrity.isSecure) {
                Logger.e(TAG, "Integrity check failed: ${integrity.failedChecks}")
                // In production, throw SecurityException
                // For development, log warning but continue
                Logger.w(TAG, "Continuing despite integrity failures (development mode)")
            }
        }
        
        // Fetch encryption key from backend
        val client: APIClientInterface = if (useMock) MockAPIClient() else APIClient(baseUrl, apiKey)
        val keyData = client.fetchEncryptionKey()
        
        if (keyData == null) {
            Logger.e(TAG, "Failed to fetch encryption key")
            return@withContext false
        }
        
        val (base64Key, keyId, expiresAt) = keyData
        val initialized = securityManager.initialize(base64Key, keyId, expiresAt)
        
        if (initialized) {
            isInitialized = true
            wiseDriveAnalytics = WiseDriveAnalytics()
            Logger.i(TAG, "SDK initialized successfully")
        }
        
        initialized
    }

    /**
     * Enable/disable debug logging
     */
    fun setLoggingEnabled(enabled: Boolean) {
        Logger.setLoggingEnabled(enabled)
    }

    // ─── PERMISSIONS ──────────────────────────────────────────

    /**
     * Request required Bluetooth permissions
     */
    suspend fun requestPermissions(activity: Activity): Boolean {
        return adapter.requestPermissions(activity)
    }

    // ─── DEVICE DISCOVERY ─────────────────────────────────────

    /**
     * Discover available OBD devices
     */
    suspend fun discoverDevices(
        onDeviceFound: (BLEDevice) -> Unit,
        timeoutMs: Long = 8000
    ) {
        Logger.i(TAG, "Starting device discovery...")
        adapter.startScan(onDeviceFound)
        
        // Auto-stop after timeout
        delay(timeoutMs)
        stopDiscovery()
    }

    /**
     * Stop device discovery
     */
    suspend fun stopDiscovery() {
        adapter.stopScan()
        Logger.d(TAG, "Device discovery stopped")
    }

    // ─── CONNECTION ───────────────────────────────────────────

    /**
     * Connect to an OBD device
     */
    suspend fun connect(deviceId: String) {
        Logger.i(TAG, "Connecting to device: $deviceId")
        adapter.connect(deviceId)
        Logger.i(TAG, "Connected successfully")
    }

    /**
     * Disconnect from current device
     */
    suspend fun disconnect() {
        adapter.disconnect()
        Logger.i(TAG, "Disconnected")
    }

    /**
     * Check if connected
     */
    fun isConnected(): Boolean = adapter.isConnected()

    /**
     * Get connected device info
     */
    fun getConnectedDevice(): BLEDevice? = adapter.getConnectedDevice()

    // ─── FULL SCAN ────────────────────────────────────────────

    /**
     * Run full diagnostic scan
     * 
     * Returns plain ScanReport to the client app.
     * Encrypted data is automatically sent to WiseDrive analytics endpoint.
     * 
     * @param options Scan options with MANDATORY registrationNumber
     * @return ScanReport - Plain JSON scan report for client use
     */
    suspend fun runFullScan(options: ScanOptions): ScanReport = 
        withContext(Dispatchers.IO) {
            if (!securityManager.isInitialized()) {
                throw SecurityException("SDK not initialized with encryption key. Call initializeWithKey() first.")
            }
            
            stopRequested = false
            val scanId = UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val scanTimestamp = dateFormat.format(Date())
            
            Logger.i(TAG, "Starting full scan: $scanId for registration: ${options.registrationNumber}")
            
            // Progress helper
            fun reportProgress(id: StageId, label: String, status: StageStatus, detail: String? = null) {
                options.onProgress?.invoke(ScanStage(id, label, status, detail))
            }
            
            try {
                // Stage 1: Initialize ELM327
                reportProgress(StageId.INIT, "Initializing ELM327", StageStatus.RUNNING)
                elm327.initialize()
                val protocol = elm327.getProtocol()
                reportProgress(StageId.INIT, "Initializing ELM327", StageStatus.COMPLETED, protocol)
                
                if (stopRequested) throw CancellationException("Scan stopped by user")
                
                // Stage 2: Fetch VIN
                reportProgress(StageId.VIN, "Fetching VIN", StageStatus.RUNNING)
                var vin: String? = null
                try {
                    val vinResult = elm327.fetchVIN()
                    vin = vinResult.vin
                    reportProgress(StageId.VIN, "Fetching VIN", StageStatus.COMPLETED, vin ?: "Not available")
                } catch (e: Exception) {
                    reportProgress(StageId.VIN, "Fetching VIN", StageStatus.SKIPPED, e.message)
                }
                
                if (stopRequested) throw CancellationException("Scan stopped by user")
                
                // Stage 3: MIL Status
                reportProgress(StageId.MIL_STATUS, "Checking MIL status", StageStatus.RUNNING)
                var milOn = false
                var dtcCount = 0
                try {
                    val milStatus = elm327.readMILStatus()
                    milOn = milStatus.milOn
                    dtcCount = milStatus.dtcCount
                    val detail = "MIL ${if (milStatus.milOn) "ON" else "OFF"}, ${milStatus.dtcCount} DTC(s) expected"
                    reportProgress(StageId.MIL_STATUS, "Checking MIL status", StageStatus.COMPLETED, detail)
                } catch (e: Exception) {
                    reportProgress(StageId.MIL_STATUS, "Checking MIL status", StageStatus.ERROR, e.message)
                }
                
                if (stopRequested) throw CancellationException("Scan stopped by user")
                
                // Stage 4: Stored DTCs (Mode 03)
                reportProgress(StageId.DTC_STORED, "Scanning stored DTCs", StageStatus.RUNNING)
                val storedDTCs = mutableListOf<DTCBasic>()
                try {
                    val storedResult = elm327.scanDTCs("03")
                    val parsed = DTCParser.parseOBDResponse(storedResult.rawResponse, "03", options.manufacturer)
                    parsed.dtcs.forEach { 
                        storedDTCs.add(DTCBasic(it.code, it.categoryName, it.description, it.ecuSource))
                    }
                    reportProgress(StageId.DTC_STORED, "Scanning stored DTCs", StageStatus.COMPLETED, "${storedDTCs.size} found")
                } catch (e: Exception) {
                    reportProgress(StageId.DTC_STORED, "Scanning stored DTCs", StageStatus.ERROR, e.message)
                }
                
                if (stopRequested) throw CancellationException("Scan stopped by user")
                
                // Stage 5: Pending DTCs (Mode 07)
                reportProgress(StageId.DTC_PENDING, "Scanning pending DTCs", StageStatus.RUNNING)
                val pendingDTCs = mutableListOf<DTCBasic>()
                try {
                    val pendingResult = elm327.scanDTCs("07")
                    val parsed = DTCParser.parseOBDResponse(pendingResult.rawResponse, "07", options.manufacturer)
                    parsed.dtcs.forEach {
                        pendingDTCs.add(DTCBasic(it.code, it.categoryName, it.description, it.ecuSource))
                    }
                    reportProgress(StageId.DTC_PENDING, "Scanning pending DTCs", StageStatus.COMPLETED, "${pendingDTCs.size} found")
                } catch (e: Exception) {
                    reportProgress(StageId.DTC_PENDING, "Scanning pending DTCs", StageStatus.ERROR, e.message)
                }
                
                if (stopRequested) throw CancellationException("Scan stopped by user")
                
                // Stage 6: Permanent DTCs (Mode 0A)
                reportProgress(StageId.DTC_PERMANENT, "Scanning permanent DTCs", StageStatus.RUNNING)
                val permanentDTCs = mutableListOf<DTCBasic>()
                try {
                    val permanentResult = elm327.scanDTCs("0A")
                    val parsed = DTCParser.parseOBDResponse(permanentResult.rawResponse, "0A", options.manufacturer)
                    parsed.dtcs.forEach {
                        permanentDTCs.add(DTCBasic(it.code, it.categoryName, it.description, it.ecuSource))
                    }
                    reportProgress(StageId.DTC_PERMANENT, "Scanning permanent DTCs", StageStatus.COMPLETED, "${permanentDTCs.size} found")
                } catch (e: Exception) {
                    reportProgress(StageId.DTC_PERMANENT, "Scanning permanent DTCs", StageStatus.ERROR, e.message)
                }
                
                if (stopRequested) throw CancellationException("Scan stopped by user")
                
                // Stage 7: Manufacturer ECU modules
                reportProgress(StageId.MANUFACTURER, "Scanning ECU modules", StageStatus.RUNNING)
                try {
                    val mfgResult = elm327.scanManufacturerModules(options.manufacturer) { progress ->
                        reportProgress(StageId.MANUFACTURER, "Scanning ${progress.component}", StageStatus.RUNNING, 
                            "${progress.index + 1}/${progress.total}")
                    }
                    
                    // Add manufacturer DTCs to stored list (deduplicate)
                    val existingCodes = storedDTCs.map { it.code }.toSet()
                    mfgResult.dtcs.forEach { mfgDtc ->
                        if (mfgDtc.code !in existingCodes) {
                            val description = DTCDescriptions.getDescription(mfgDtc.code)
                            storedDTCs.add(DTCBasic(mfgDtc.code, DTCParser.getCategoryName(mfgDtc.code.first()), description, mfgDtc.ecuSource))
                        }
                    }
                    
                    val detail = "${mfgResult.dtcs.size} DTCs across ${mfgResult.modulesScanned.size} modules"
                    reportProgress(StageId.MANUFACTURER, "Scanning ECU modules", StageStatus.COMPLETED, detail)
                } catch (e: Exception) {
                    reportProgress(StageId.MANUFACTURER, "Scanning ECU modules", StageStatus.ERROR, e.message)
                }
                
                if (stopRequested) throw CancellationException("Scan stopped by user")
                
                // Stage 8: Live Data
                reportProgress(StageId.LIVE_DATA, "Reading live data", StageStatus.RUNNING)
                val liveReadings = mutableListOf<LiveDataReading>()
                try {
                    for (pid in OBDPIDs.PRIORITY_PIDS) {
                        if (stopRequested) break
                        
                        try {
                            val result = elm327.scanLiveData(pid)
                            val reading = LiveDataParser.parseLiveDataResponse(pid, result.rawResponse)
                            reading?.let { liveReadings.add(it) }
                        } catch (e: Exception) {
                            Logger.w(TAG, "Failed to read PID $pid: ${e.message}")
                        }
                    }
                    reportProgress(StageId.LIVE_DATA, "Reading live data", StageStatus.COMPLETED, "${liveReadings.size} readings")
                } catch (e: Exception) {
                    reportProgress(StageId.LIVE_DATA, "Reading live data", StageStatus.ERROR, e.message)
                }
                
                // Stage 9: Complete
                val scanDuration = System.currentTimeMillis() - startTime
                reportProgress(StageId.COMPLETE, "Scan complete", StageStatus.COMPLETED)
                
                // Build vehicle info
                val vehicle = VehicleInfo(
                    manufacturer = options.manufacturer?.let { Manufacturers.getDisplayName(it) },
                    manufacturerId = options.manufacturer,
                    year = options.year,
                    vin = vin
                )
                
                // Create internal scan report
                val scanReport = LiveDataParser.createScanResultJSON(
                    scanId = scanId,
                    scanTimestamp = scanTimestamp,
                    scanDuration = scanDuration,
                    protocol = protocol,
                    vehicle = vehicle,
                    storedDTCs = storedDTCs,
                    pendingDTCs = pendingDTCs,
                    permanentDTCs = permanentDTCs,
                    liveData = liveReadings,
                    scanCycles = 1,
                    registrationNumber = options.registrationNumber
                )
                
                // Transform to API payload format for analytics
                val apiPayload = ReportTransformer.transform(scanReport, options.registrationNumber)
                
                // Store for later submitReport() call
                lastScanReport = scanReport
                lastApiPayload = apiPayload
                
                // Send encrypted data to WiseDrive analytics (background with retry)
                wiseDriveAnalytics.sendAnalytics(apiPayload, sdkScope)
                
                Logger.i(TAG, "Scan complete. Duration: ${scanDuration}ms, DTCs: ${storedDTCs.size + pendingDTCs.size + permanentDTCs.size}")
                
                // Return plain ScanReport to client
                scanReport
                
            } catch (e: CancellationException) {
                Logger.w(TAG, "Scan cancelled: ${e.message}")
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Scan failed: ${e.message}")
                throw e
            }
        }

    /**
     * Stop current scan
     */
    fun stopScan() {
        stopRequested = true
        Logger.i(TAG, "Scan stop requested")
    }

    /**
     * Submit report to client's backend
     * Also ensures WiseDrive analytics has been sent
     * 
     * @param scanReport The scan report to submit
     * @return Boolean indicating success
     */
    suspend fun submitReport(scanReport: ScanReport): Boolean {
        // Ensure analytics is submitted to WiseDrive
        wiseDriveAnalytics.onClientSubmit()
        
        // Client can use scanReport as needed - it's plain JSON
        // This method is for client's own backend submission
        Logger.i(TAG, "Report ready for client submission: ${scanReport.scanId}")
        return true
    }

    /**
     * Get the last API payload (for client's backend submission)
     */
    fun getLastApiPayload(): APIPayload? = lastApiPayload

    // ─── DIRECT ELM327 ACCESS (Advanced) ──────────────────────

    /**
     * Fetch VIN
     */
    suspend fun fetchVIN(): VINResult = elm327.fetchVIN()

    /**
     * Read MIL Status
     */
    suspend fun readMILStatus(): MILStatusResult = elm327.readMILStatus()

    /**
     * Read Enhanced DTCs using UDS
     */
    suspend fun readEnhancedDTCs(ecuAddress: String? = null): EnhancedDTCResult = 
        elm327.readEnhancedDTCs(ecuAddress)

    /**
     * Read Enhanced DTCs with BUFFER FULL recovery
     */
    suspend fun readEnhancedDTCsAdvanced(ecuAddress: String? = null): EnhancedDTCResult = 
        elm327.readEnhancedDTCsAdvanced(ecuAddress)

    /**
     * Scan manufacturer ECU modules
     */
    suspend fun scanManufacturerModules(
        manufacturerId: String?, 
        onProgress: ((ManufacturerScanProgress) -> Unit)? = null
    ): ManufacturerScanResult = elm327.scanManufacturerModules(manufacturerId, onProgress)

    /**
     * Scan all common modules
     */
    suspend fun scanAllModules(): List<ModuleScanResult> = elm327.scanAllModules()

    /**
     * Get raw ECU responses
     */
    fun getECUResponses(): List<RawECUResponse> = elm327.getECUResponses()

    /**
     * Clear raw response history
     */
    fun clearResponses() = elm327.clearResponses()

    /**
     * Get current protocol
     */
    fun getProtocol(): String = elm327.getProtocol()

    /**
     * Check if WiseDrive analytics has been submitted
     */
    fun isAnalyticsSubmitted(): Boolean = 
        if (::wiseDriveAnalytics.isInitialized) wiseDriveAnalytics.isAnalyticsSubmitted() else false

    /**
     * Clean up resources
     */
    fun cleanup() {
        if (::wiseDriveAnalytics.isInitialized) {
            wiseDriveAnalytics.cancel()
        }
        sdkScope.cancel()
    }
}
