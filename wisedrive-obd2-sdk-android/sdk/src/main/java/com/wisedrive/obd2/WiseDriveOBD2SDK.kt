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
    private val gson = Gson()
    
    // Internal analytics handler (uses same mock mode as SDK)
    private lateinit var wiseDriveAnalytics: WiseDriveAnalytics
    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Last scan report for submitReport
    private var lastScanReport: ScanReport? = null
    private var lastApiPayload: APIPayload? = null
    
    // Callbacks for analytics logging
    private var onAnalyticsPayloadPrepared: ((String) -> Unit)? = null
    private var onAnalyticsSubmissionResult: ((Boolean, String) -> Unit)? = null
    
    private var stopRequested = false
    private var isInitialized = false

    // ─── ANALYTICS CALLBACKS ──────────────────────────────────

    /**
     * Set callback to receive the analytics payload JSON when prepared
     * Useful for debugging and logging what data is being sent
     */
    fun setOnAnalyticsPayloadPrepared(callback: (String) -> Unit) {
        onAnalyticsPayloadPrepared = callback
        if (::wiseDriveAnalytics.isInitialized) {
            wiseDriveAnalytics.setOnPayloadPrepared(callback)
        }
    }

    /**
     * Set callback to receive analytics submission result
     * @param callback (success: Boolean, response: String)
     */
    fun setOnAnalyticsSubmissionResult(callback: (Boolean, String) -> Unit) {
        onAnalyticsSubmissionResult = callback
        if (::wiseDriveAnalytics.isInitialized) {
            wiseDriveAnalytics.setOnSubmissionResult(callback)
        }
    }

    // ─── INITIALIZATION ──────────────────────────────────────

    /**
     * Initialize SDK
     * MUST be called before any scan operations
     * 
     * @param apiKey Optional API key for future use
     * @param baseUrl Optional base URL for future use
     */
    suspend fun initializeWithKey(
        apiKey: String = "",
        baseUrl: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        Logger.i(TAG, "Initializing SDK (mock=$useMock)...")
        
        // Run integrity check (skip in mock mode for development)
        if (!useMock) {
            try {
                val integrity = IntegrityChecker.verifyEnvironment(context)
                if (!integrity.isSecure) {
                    Logger.w(TAG, "Integrity check warnings: ${integrity.failedChecks}")
                    // Continue anyway - these are non-fatal warnings
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Integrity check skipped: ${e.message}")
            }
        }
        
        // Initialize analytics handler
        wiseDriveAnalytics = WiseDriveAnalytics(useMock)
        
        // Set callbacks if they were registered before initialization
        onAnalyticsPayloadPrepared?.let { wiseDriveAnalytics.setOnPayloadPrepared(it) }
        onAnalyticsSubmissionResult?.let { wiseDriveAnalytics.setOnSubmissionResult(it) }
        
        isInitialized = true
        Logger.i(TAG, "SDK initialized successfully (mock=$useMock)")
        
        true
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
     * Data is automatically sent to WiseDrive analytics endpoint.
     * 
     * @param options Scan options with MANDATORY registrationNumber
     * @return ScanReport - Plain JSON scan report for client use
     */
    suspend fun runFullScan(options: ScanOptions): ScanReport = 
        withContext(Dispatchers.IO) {
            if (!isInitialized) {
                throw IllegalStateException("SDK not initialized. Call initializeWithKey() first.")
            }
            
            stopRequested = false
            val scanId = UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val scanTimestamp = dateFormat.format(Date())
            
            Logger.i(TAG, "Starting full scan: $scanId for registration: ${options.registrationNumber}, trackingId: ${options.trackingId}")
            
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
                val apiPayload = ReportTransformer.transform(scanReport, options.registrationNumber, options.trackingId)
                
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
     * Get the last analytics payload JSON (pretty printed)
     * Useful for debugging/logging
     */
    fun getLastAnalyticsPayloadJson(): String? = 
        if (::wiseDriveAnalytics.isInitialized) wiseDriveAnalytics.getLastPayloadJson() else null

    /**
     * Get the last analytics response
     */
    fun getLastAnalyticsResponse(): String? = 
        if (::wiseDriveAnalytics.isInitialized) wiseDriveAnalytics.getLastResponse() else null

    /**
     * Check if SDK is in mock mode
     */
    fun isMockMode(): Boolean = useMock

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
