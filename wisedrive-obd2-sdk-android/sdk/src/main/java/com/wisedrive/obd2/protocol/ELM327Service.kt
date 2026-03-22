package com.wisedrive.obd2.protocol

import com.wisedrive.obd2.adapter.BluetoothAdapterInterface
import com.wisedrive.obd2.constants.ManufacturerECUs
import com.wisedrive.obd2.models.*
import com.wisedrive.obd2.util.Logger
import kotlinx.coroutines.*

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ELM327 Protocol Service
 * Core command engine for OBD-II communication
 * 
 * Handles all AT commands, OBD modes, and manufacturer-specific UDS scanning
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ELM327Service(private val adapter: BluetoothAdapterInterface) {

    companion object {
        private const val TAG = "ELM327Service"
        private const val COMMAND_TIMEOUT_MS = 8000L
        private const val QUIET_COMMAND_TIMEOUT_MS = 500L
        private const val MODULE_SCAN_DELAY_MS = 15L
        private const val MAX_RETRIES = 3
    }

    // Initialization commands - EXACT sequence from production SDK
    private val initCommands = listOf(
        InitCommand("ATZ", "Reset ELM327"),
        InitCommand("ATE0", "Echo OFF"),
        InitCommand("ATL1", "Linefeeds ON (for multi-ECU parsing)"),
        InitCommand("ATS1", "Spaces ON (for hex parsing)"),
        InitCommand("ATH1", "Headers ON - CRITICAL: shows ECU IDs like 7E8, 7E9"),
        InitCommand("ATCAF1", "CAN Auto Formatting"),
        InitCommand("ATAT2", "Adaptive Timing - Aggressive"),
        InitCommand("ATST FF", "Max response timeout 1020ms (all ECUs respond)"),
        InitCommand("ATAL", "Allow Long Messages (ISO-TP multi-frame)"),
        InitCommand("ATCFC1", "CAN Flow Control ON (prevents BUFFER FULL)"),
        InitCommand("ATSP0", "Auto Protocol Detection"),
        InitCommand("ATDPN", "Detect Protocol Number"),
        InitCommand("0100", "ECU Capability Check (wake up bus)")
    )

    private var currentProtocol: String = "Unknown"
    private val ecuResponses = ConcurrentLinkedQueue<RawECUResponse>()
    private var responseBuffer = StringBuilder()
    private var responseDeferred: CompletableDeferred<String>? = null

    init {
        // Register for incoming data
        adapter.onData { data ->
            responseBuffer.append(data)
            
            // Check if response is complete (ends with '>')
            if (responseBuffer.contains(">")) {
                val response = responseBuffer.toString()
                responseBuffer.clear()
                responseDeferred?.complete(response)
            }
        }
    }

    /**
     * Initialize ELM327 adapter with exact 13-command sequence
     */
    suspend fun initialize(): List<ELM327InitStep> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<ELM327InitStep>()
        
        Logger.i(TAG, "Starting ELM327 initialization...")
        
        for (cmd in initCommands) {
            val step = executeInitCommand(cmd)
            steps.add(step)
            
            // Special handling for ATDPN - extract protocol
            if (cmd.command == "ATDPN" && step.status == StepStatus.SUCCESS) {
                currentProtocol = parseProtocol(step.response ?: "")
                Logger.i(TAG, "Detected protocol: $currentProtocol")
            }
        }
        
        Logger.i(TAG, "ELM327 initialization complete. Protocol: $currentProtocol")
        steps
    }

    private suspend fun executeInitCommand(cmd: InitCommand): ELM327InitStep {
        var lastError: String? = null
        var response: String? = null
        val startTime = System.currentTimeMillis()
        
        for (retry in 1..MAX_RETRIES) {
            try {
                Logger.d(TAG, "Init: ${cmd.command} (attempt $retry)")
                response = sendCommand(cmd.command, COMMAND_TIMEOUT_MS)
                
                // Check for valid response
                if (isValidResponse(response)) {
                    val duration = System.currentTimeMillis() - startTime
                    Logger.d(TAG, "Init success: ${cmd.command} -> ${response.take(50)}")
                    return ELM327InitStep(
                        command = cmd.command,
                        description = cmd.description,
                        status = StepStatus.SUCCESS,
                        response = cleanResponse(response),
                        durationMs = duration
                    )
                } else {
                    lastError = "Invalid response: ${response.take(50)}"
                }
            } catch (e: Exception) {
                lastError = e.message
                Logger.w(TAG, "Init attempt $retry failed for ${cmd.command}: ${e.message}")
            }
            
            // Wait before retry
            if (retry < MAX_RETRIES) {
                delay(200)
            }
        }
        
        val duration = System.currentTimeMillis() - startTime
        Logger.e(TAG, "Init failed: ${cmd.command} after $MAX_RETRIES attempts")
        return ELM327InitStep(
            command = cmd.command,
            description = cmd.description,
            status = StepStatus.FAILED,
            error = lastError,
            response = response?.let { cleanResponse(it) },
            durationMs = duration
        )
    }

    /**
     * Send command and wait for response
     */
    suspend fun sendCommand(command: String, timeoutMs: Long = COMMAND_TIMEOUT_MS): String = 
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            // Create deferred for response
            responseDeferred = CompletableDeferred()
            responseBuffer.clear()
            
            // Write command
            adapter.write(command)
            
            // Wait for response with timeout
            try {
                withTimeout(timeoutMs) {
                    responseDeferred?.await() ?: ""
                }
            } catch (e: TimeoutCancellationException) {
                // Return whatever we have in buffer
                val partial = responseBuffer.toString()
                responseBuffer.clear()
                if (partial.isNotEmpty()) partial else throw e
            } finally {
                val duration = System.currentTimeMillis() - startTime
                val response = responseBuffer.toString().ifEmpty { 
                    responseDeferred?.let { if (it.isCompleted) it.getCompleted() else "" } ?: "" 
                }
                
                // Record raw response
                ecuResponses.add(RawECUResponse(
                    command = command,
                    rawResponse = response,
                    timestamp = System.currentTimeMillis(),
                    durationMs = duration
                ))
                
                responseDeferred = null
            }
        }

    /**
     * Send command quietly (short timeout, ignore errors)
     */
    private suspend fun sendCommandQuiet(command: String): String {
        return try {
            sendCommand(command, QUIET_COMMAND_TIMEOUT_MS)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Scan DTCs (Mode 03, 07, or 0A)
     */
    suspend fun scanDTCs(mode: String): DTCScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        // Clear buffer with dummy command
        sendCommandQuiet("AT")
        
        val response = sendCommand(mode)
        val duration = System.currentTimeMillis() - startTime
        
        Logger.d(TAG, "DTC scan mode $mode: ${response.take(100)}")
        
        DTCScanResult(
            dtcBytes = response,
            rawResponse = response,
            durationMs = duration
        )
    }

    /**
     * Read MIL Status (Mode 01 PID 01)
     */
    suspend fun readMILStatus(): MILStatusResult = withContext(Dispatchers.IO) {
        val response = sendCommand("0101")
        
        // Parse response: find "41 01" marker
        val cleaned = cleanResponse(response)
        val tokens = cleaned.split("\\s+".toRegex())
        
        var milOn = false
        var dtcCount = 0
        
        // Find 41 01 in response
        for (i in 0 until tokens.size - 1) {
            if (tokens[i].equals("41", ignoreCase = true) && 
                tokens[i + 1].equals("01", ignoreCase = true) &&
                i + 2 < tokens.size) {
                
                // Byte A is at position i+2
                val byteA = tokens[i + 2].toIntOrNull(16) ?: 0
                
                // Bit 7 (0x80): MIL lamp ON/OFF
                milOn = (byteA and 0x80) != 0
                
                // Bits 0-6: DTC count (0-127)
                dtcCount = byteA and 0x7F
                
                Logger.d(TAG, "MIL Status: ON=$milOn, DTCs=$dtcCount (byteA=0x${byteA.toString(16)})")
                break
            }
        }
        
        MILStatusResult(milOn, dtcCount, response)
    }

    /**
     * Fetch VIN (Mode 09 PID 02)
     */
    suspend fun fetchVIN(): VINResult = withContext(Dispatchers.IO) {
        val response = sendCommand("0902")
        
        // VIN is 17 ASCII characters spread across multi-frame ISO-TP response
        val vin = parseVIN(response)
        
        Logger.d(TAG, "VIN: $vin")
        VINResult(vin, response)
    }

    private fun parseVIN(response: String): String? {
        val cleaned = cleanResponse(response)
        
        // Try to find hex bytes after "49 02"
        val vinBytes = mutableListOf<Int>()
        val lines = cleaned.split("\r", "\n").filter { it.isNotBlank() }
        
        for (line in lines) {
            val tokens = line.trim().split("\\s+".toRegex())
            var foundMarker = false
            
            for (i in tokens.indices) {
                // Skip ISO-TP headers (10, 21, 22, etc.) and find data
                if (tokens[i].equals("49", ignoreCase = true) && 
                    i + 1 < tokens.size && 
                    tokens[i + 1].equals("02", ignoreCase = true)) {
                    foundMarker = true
                    // Skip count byte (usually at i+2), data starts at i+3
                    for (j in (i + 3) until tokens.size) {
                        tokens[j].toIntOrNull(16)?.let { vinBytes.add(it) }
                    }
                } else if (foundMarker || line.startsWith("1:") || line.startsWith("2:")) {
                    // Continuation data
                    val hexToken = tokens[i].replace(":", "")
                    hexToken.toIntOrNull(16)?.let { vinBytes.add(it) }
                }
            }
        }
        
        if (vinBytes.size < 17) {
            // Alternative parsing: just extract all printable ASCII
            val allHex = cleaned.replace(Regex("[^0-9A-Fa-f ]"), "")
                .split("\\s+".toRegex())
                .mapNotNull { it.toIntOrNull(16) }
                .filter { it in 0x30..0x5A } // 0-9, A-Z
            
            if (allHex.size >= 17) {
                return allHex.take(17).map { it.toChar() }.joinToString("")
            }
        }
        
        // Convert bytes to ASCII
        val vinChars = vinBytes
            .filter { it in 0x30..0x5A || it in 0x61..0x7A } // Alphanumeric
            .map { it.toChar() }
            .joinToString("")
        
        return if (vinChars.length >= 17) vinChars.take(17) else if (vinChars.isNotEmpty()) vinChars else null
    }

    /**
     * Read Enhanced DTCs using UDS Service 0x19
     */
    suspend fun readEnhancedDTCs(ecuAddress: String? = null): EnhancedDTCResult = 
        withContext(Dispatchers.IO) {
            // Set header if specific ECU
            if (ecuAddress != null) {
                sendCommand("ATSH $ecuAddress")
            }
            
            val response = sendCommand("1902FF")
            
            // Reset header
            if (ecuAddress != null) {
                sendCommand("ATSH 7DF")
            }
            
            EnhancedDTCResult(
                dtcBytes = response,
                rawResponse = response,
                ecuAddress = ecuAddress
            )
        }

    /**
     * Read Enhanced DTCs with BUFFER FULL recovery
     */
    suspend fun readEnhancedDTCsAdvanced(ecuAddress: String? = null): EnhancedDTCResult = 
        withContext(Dispatchers.IO) {
            if (ecuAddress != null) {
                sendCommand("ATSH $ecuAddress")
            }
            
            var response = sendCommand("1902FF")
            var bufferFull = false
            
            // Check for BUFFER FULL
            if (response.contains("BUFFER FULL", ignoreCase = true)) {
                bufferFull = true
                Logger.w(TAG, "BUFFER FULL detected, trying fallback status masks")
                
                // Fallback 1: Try status mask 0x08 (confirmed DTCs only)
                response = sendCommand("190208")
                
                if (response.contains("BUFFER FULL", ignoreCase = true)) {
                    // Fallback 2: Try status mask 0x04
                    response = sendCommand("190204")
                }
                
                if (response.contains("BUFFER FULL", ignoreCase = true)) {
                    // Fallback 3: Try status mask 0x80
                    response = sendCommand("190280")
                }
                
                if (response.contains("BUFFER FULL", ignoreCase = true)) {
                    // Fallback 4: Try sub-function 0x0F (first confirmed DTC)
                    response = sendCommand("190F")
                }
            }
            
            if (ecuAddress != null) {
                sendCommand("ATSH 7DF")
            }
            
            EnhancedDTCResult(
                dtcBytes = response,
                rawResponse = response,
                ecuAddress = ecuAddress,
                bufferFullDetected = bufferFull
            )
        }

    /**
     * Scan Manufacturer ECU Modules
     */
    suspend fun scanManufacturerModules(
        manufacturerId: String?,
        onProgress: ((ManufacturerScanProgress) -> Unit)? = null
    ): ManufacturerScanResult = withContext(Dispatchers.IO) {
        
        val config = ManufacturerECUs.getManufacturerConfig(manufacturerId)
        
        // Fall back to common modules if no config
        val modules = config?.modules ?: ManufacturerECUs.COMMON_MODULES
        
        Logger.i(TAG, "Scanning ${modules.size} modules for ${config?.name ?: "Generic"}")
        
        val allDTCs = mutableListOf<ManufacturerDTC>()
        val modulesScanned = mutableListOf<String>()
        val rawResponses = mutableMapOf<String, String>()
        
        for ((index, module) in modules.withIndex()) {
            onProgress?.invoke(ManufacturerScanProgress(
                phase = "module_scan",
                component = module.name,
                index = index,
                total = modules.size,
                status = "scanning"
            ))
            
            try {
                // Set CAN header to target specific ECU
                sendCommand("ATSH ${module.txId}")
                
                // Set CAN receive address filter
                sendCommand("ATCRA ${module.rxId}")
                
                // Send UDS ReadDTCInformation request
                val response = sendCommand("1902FF")
                rawResponses[module.name] = response
                
                // Parse UDS response for DTCs
                val dtcs = parseUDSResponse(response)
                
                // Tag each DTC with its ECU source
                dtcs.forEach { it.ecuSource = module.name }
                allDTCs.addAll(dtcs)
                
                modulesScanned.add(module.name)
                
                Logger.d(TAG, "Module ${module.name}: ${dtcs.size} DTCs")
                
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to scan module ${module.name}: ${e.message}")
            }
            
            // 15ms delay between modules to prevent CAN bus overload
            delay(MODULE_SCAN_DELAY_MS)
        }
        
        // Reset headers back to default
        sendCommand("ATSH 7DF")
        sendCommand("ATCRA")
        
        // Deduplicate DTCs (same code from multiple ECUs → keep first)
        val uniqueDTCs = allDTCs.distinctBy { it.code }
        
        Logger.i(TAG, "Manufacturer scan complete: ${uniqueDTCs.size} unique DTCs from ${modulesScanned.size} modules")
        
        ManufacturerScanResult(
            dtcs = uniqueDTCs,
            modulesScanned = modulesScanned,
            rawResponses = rawResponses,
            config = config
        )
    }

    /**
     * Parse UDS Response (Service 0x19 0x02)
     */
    private fun parseUDSResponse(rawResponse: String): MutableList<ManufacturerDTC> {
        val dtcs = mutableListOf<ManufacturerDTC>()
        val cleaned = cleanResponse(rawResponse)
        
        // Check for positive response (59 02)
        if (!cleaned.contains("59", ignoreCase = true)) {
            return dtcs
        }
        
        // Extract all hex bytes
        val allBytes = mutableListOf<Int>()
        val lines = cleaned.split("\r", "\n").filter { it.isNotBlank() }
        
        for (line in lines) {
            val tokens = line.trim().split("\\s+".toRegex())
            
            for (token in tokens) {
                // Skip ISO-TP headers and non-hex
                if (token.length == 2) {
                    token.toIntOrNull(16)?.let { allBytes.add(it) }
                }
            }
        }
        
        // Find start of DTC data after "59 02 XX" (XX = status availability mask)
        var dataStart = 0
        for (i in 0 until allBytes.size - 2) {
            if (allBytes[i] == 0x59 && allBytes[i + 1] == 0x02) {
                dataStart = i + 3  // Skip 59 02 and status mask
                break
            }
        }
        
        // Parse DTCs in groups of 3 bytes: [DTC High] [DTC Low] [Status]
        var i = dataStart
        while (i + 2 < allBytes.size) {
            val highByte = allBytes[i]
            val lowByte = allBytes[i + 1]
            val status = allBytes[i + 2]
            
            // Skip empty DTCs (0x00 0x00)
            if (highByte != 0 || lowByte != 0) {
                val code = decodeDTCBytes(highByte, lowByte)
                val statusText = getStatusText(status)
                
                dtcs.add(ManufacturerDTC(
                    code = code,
                    rawBytes = String.format("%02X %02X", highByte, lowByte),
                    status = status,
                    statusText = statusText
                ))
            }
            
            i += 3
        }
        
        return dtcs
    }

    /**
     * Decode DTC bytes according to ISO 15031-6 / SAE J2012
     */
    private fun decodeDTCBytes(highByte: Int, lowByte: Int): String {
        // High byte bits 7-6 → category
        val category = when ((highByte shr 6) and 0x03) {
            0 -> 'P'  // Powertrain
            1 -> 'C'  // Chassis
            2 -> 'B'  // Body
            3 -> 'U'  // Network
            else -> 'P'
        }
        
        // High byte bits 5-4 → first digit (0-3)
        val firstDigit = (highByte shr 4) and 0x03
        
        // High byte bits 3-0 → second digit (0-F)
        val secondDigit = highByte and 0x0F
        
        // Low byte bits 7-4 → third digit (0-F)
        val thirdDigit = (lowByte shr 4) and 0x0F
        
        // Low byte bits 3-0 → fourth digit (0-F)
        val fourthDigit = lowByte and 0x0F
        
        return String.format("%c%d%X%X%X", category, firstDigit, secondDigit, thirdDigit, fourthDigit)
    }

    private fun getStatusText(status: Int): String {
        val parts = mutableListOf<String>()
        
        if ((status and 0x01) != 0) parts.add("testFailed")
        if ((status and 0x02) != 0) parts.add("testFailedThisOperationCycle")
        if ((status and 0x04) != 0) parts.add("pendingDTC")
        if ((status and 0x08) != 0) parts.add("confirmedDTC")
        if ((status and 0x10) != 0) parts.add("testNotCompletedSinceLastClear")
        if ((status and 0x20) != 0) parts.add("testFailedSinceLastClear")
        if ((status and 0x40) != 0) parts.add("testNotCompletedThisOperationCycle")
        if ((status and 0x80) != 0) parts.add("warningIndicatorRequested")
        
        return parts.joinToString(", ")
    }

    /**
     * Scan live data for a specific PID
     */
    suspend fun scanLiveData(pid: String): LiveDataScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val command = "01$pid"
        val response = sendCommand(command)
        val duration = System.currentTimeMillis() - startTime
        
        LiveDataScanResult(
            response = response,
            rawResponse = response,
            durationMs = duration
        )
    }

    /**
     * Scan all common modules when manufacturer is unknown
     */
    suspend fun scanAllModules(): List<ModuleScanResult> = withContext(Dispatchers.IO) {
        val commonModules = listOf(
            ModuleAddress("Engine/PCM", "7E0", "7E8"),
            ModuleAddress("Transmission", "7E1", "7E9"),
            ModuleAddress("ABS/ESP", "7E2", "7EA"),
            ModuleAddress("Body Control", "720", "728"),
            ModuleAddress("Instrument", "726", "72E"),
            ModuleAddress("Climate/HVAC", "727", "72F"),
            ModuleAddress("Steering", "737", "73F")
        )
        
        val results = mutableListOf<ModuleScanResult>()
        
        for (module in commonModules) {
            try {
                sendCommand("ATSH ${module.txId}")
                sendCommand("ATCRA ${module.rxId}")
                val response = sendCommand("1902FF")
                val dtcs = parseUDSResponse(response)
                dtcs.forEach { it.ecuSource = module.name }
                results.add(ModuleScanResult(module.name, module.txId, dtcs, response))
            } catch (e: Exception) {
                Logger.w(TAG, "Module ${module.name} not responding")
            }
            delay(MODULE_SCAN_DELAY_MS)
        }
        
        // Reset headers
        sendCommand("ATSH 7DF")
        sendCommand("ATCRA")
        
        results
    }

    /**
     * Get current protocol name
     */
    fun getProtocol(): String = currentProtocol

    /**
     * Get all raw ECU responses
     */
    fun getECUResponses(): List<RawECUResponse> = ecuResponses.toList()

    /**
     * Clear response history
     */
    fun clearResponses() {
        ecuResponses.clear()
    }

    /**
     * Parse protocol number to name
     */
    private fun parseProtocol(response: String): String {
        val cleaned = cleanResponse(response)
        val protocolNumber = cleaned.trim().uppercase().replace("A", "")
        
        return when (protocolNumber) {
            "0" -> "Automatic"
            "1" -> "SAE J1850 PWM (41.6 kbps) - Ford"
            "2" -> "SAE J1850 VPW (10.4 kbps) - GM"
            "3" -> "ISO 9141-2 (5 baud init) - European"
            "4" -> "ISO 14230-4 KWP (5 baud init)"
            "5" -> "ISO 14230-4 KWP (fast init)"
            "6" -> "ISO 15765-4 CAN (11 bit ID, 500 kbps)"
            "7" -> "ISO 15765-4 CAN (29 bit ID, 500 kbps)"
            "8" -> "ISO 15765-4 CAN (11 bit ID, 250 kbps)"
            "9" -> "ISO 15765-4 CAN (29 bit ID, 250 kbps)"
            "A" -> "SAE J1939 CAN (29 bit ID, 250 kbps)"
            "B" -> "USER1 CAN (11 bit ID, 125 kbps)"
            "C" -> "USER2 CAN (11 bit ID, 50 kbps)"
            else -> "Unknown Protocol ($protocolNumber)"
        }
    }

    private fun cleanResponse(response: String): String {
        return response
            .replace("SEARCHING...", "")
            .replace("BUS INIT...", "")
            .replace(">", "")
            .replace("\r\r", "\r")
            .trim()
    }

    private fun isValidResponse(response: String): Boolean {
        val cleaned = cleanResponse(response)
        return cleaned.isNotEmpty() && 
               !cleaned.contains("?") && 
               !cleaned.contains("NO DATA", ignoreCase = true) &&
               !cleaned.contains("UNABLE TO CONNECT", ignoreCase = true) &&
               !cleaned.contains("CAN ERROR", ignoreCase = true)
    }
}
