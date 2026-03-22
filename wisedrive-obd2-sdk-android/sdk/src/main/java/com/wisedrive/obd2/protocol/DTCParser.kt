package com.wisedrive.obd2.protocol

import com.wisedrive.obd2.constants.DTCDescriptions
import com.wisedrive.obd2.models.DTCResult
import com.wisedrive.obd2.models.ParsedDTCResponse
import com.wisedrive.obd2.util.Logger

/**
 * DTC Parser - Decodes OBD-II DTC bytes according to ISO 15031-6 / SAE J2012
 */
object DTCParser {

    private const val TAG = "DTCParser"

    /**
     * Parse OBD Mode 03/07/0A response
     * @param rawResponse The raw response from ELM327
     * @param mode The OBD mode (03, 07, or 0A)
     * @param manufacturerId Optional manufacturer ID for enhanced DTC descriptions (reserved for future use)
     */
    @Suppress("UNUSED_PARAMETER")
    fun parseOBDResponse(
        rawResponse: String,
        mode: String,
        manufacturerId: String? = null
    ): ParsedDTCResponse {
        val dtcs = mutableListOf<DTCResult>()
        val ecuResponses = mutableMapOf<String, String>()
        
        // Clean the response
        val cleaned = cleanResponse(rawResponse)
        
        // Split into lines for multi-ECU handling
        val lines = cleaned.split("\r", "\n").filter { it.isNotBlank() }
        
        // Expected response byte based on mode
        val responseMarker = when (mode) {
            "03" -> "43"
            "07" -> "47"
            "0A" -> "4A"
            else -> "43"
        }
        
        var currentECU = "7E8"  // Default ECU
        
        for (line in lines) {
            val tokens = line.trim().split("\\s+".toRegex())
            
            // Check for ECU header (e.g., "7E8", "7E9")
            if (tokens.isNotEmpty() && isECUHeader(tokens[0])) {
                currentECU = tokens[0]
            }
            
            // Find response marker position
            var markerIndex = -1
            for (i in tokens.indices) {
                if (tokens[i].equals(responseMarker, ignoreCase = true)) {
                    markerIndex = i
                    break
                }
            }
            
            if (markerIndex >= 0 && markerIndex + 1 < tokens.size) {
                // Next byte after marker is DTC count (captured for protocol compliance)
                @Suppress("UNUSED_VARIABLE")
                val dtcCountByte = tokens[markerIndex + 1].toIntOrNull(16) ?: 0
                
                ecuResponses[currentECU] = line
                
                // Extract DTC bytes (pairs after count)
                var byteIndex = markerIndex + 2
                while (byteIndex + 1 < tokens.size) {
                    val highByte = tokens[byteIndex].toIntOrNull(16)
                    val lowByte = tokens[byteIndex + 1].toIntOrNull(16)
                    
                    if (highByte != null && lowByte != null) {
                        // Skip empty DTCs (00 00)
                        if (highByte != 0 || lowByte != 0) {
                            val dtc = decodeDTC(highByte, lowByte, mode, currentECU)
                            dtcs.add(dtc)
                        }
                    }
                    
                    byteIndex += 2
                }
            }
        }
        
        // Handle ISO-TP multi-frame responses
        if (dtcs.isEmpty() && cleaned.contains("10")) {
            val multiFrameDTCs = parseMultiFrameResponse(cleaned, mode)
            dtcs.addAll(multiFrameDTCs)
        }
        
        // Deduplicate (same code from multiple ECUs)
        val uniqueDTCs = dtcs.distinctBy { it.code }
        
        Logger.d(TAG, "Parsed ${uniqueDTCs.size} DTCs from mode $mode")
        
        return ParsedDTCResponse(
            totalDTCs = uniqueDTCs.size,
            dtcs = uniqueDTCs,
            ecuResponses = ecuResponses,
            debugInfo = "Mode: $mode, Raw lines: ${lines.size}"
        )
    }

    /**
     * Decode DTC from high/low bytes
     */
    private fun decodeDTC(highByte: Int, lowByte: Int, mode: String, ecuSource: String): DTCResult {
        // High byte bits 7-6 → category
        val categoryCode = (highByte shr 6) and 0x03
        val category = when (categoryCode) {
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
        
        val code = String.format("%c%d%X%X%X", category, firstDigit, secondDigit, thirdDigit, fourthDigit)
        val rawHex = String.format("%02X %02X", highByte, lowByte)
        
        // Lookup description
        val description = DTCDescriptions.getDescription(code)
        val isManufacturerSpecific = DTCDescriptions.isManufacturerSpecific(code)
        
        return DTCResult(
            code = code,
            category = category,
            categoryName = getCategoryName(category),
            rawHex = rawHex,
            mode = mode,
            description = description,
            isManufacturerSpecific = isManufacturerSpecific,
            ecuSource = mapECUToModule(ecuSource)
        )
    }

    /**
     * Parse ISO-TP multi-frame response
     */
    private fun parseMultiFrameResponse(response: String, mode: String): List<DTCResult> {
        val dtcs = mutableListOf<DTCResult>()
        val allBytes = mutableListOf<Int>()
        
        val responseMarker = when (mode) {
            "03" -> 0x43
            "07" -> 0x47
            "0A" -> 0x4A
            else -> 0x43
        }
        
        val lines = response.split("\r", "\n").filter { it.isNotBlank() }
        
        for (line in lines) {
            val tokens = line.trim().split("\\s+".toRegex())
            
            for (token in tokens) {
                // Skip ECU headers and ISO-TP frame headers
                if (token.length == 2 && !isECUHeader(token)) {
                    token.toIntOrNull(16)?.let { allBytes.add(it) }
                }
            }
        }
        
        // Find response marker and extract DTCs
        var dataStart = -1
        for (i in allBytes.indices) {
            if (allBytes[i] == responseMarker && i + 1 < allBytes.size) {
                dataStart = i + 2  // Skip marker and count
                break
            }
        }
        
        if (dataStart >= 0) {
            var i = dataStart
            while (i + 1 < allBytes.size) {
                val highByte = allBytes[i]
                val lowByte = allBytes[i + 1]
                
                if (highByte != 0 || lowByte != 0) {
                    val dtc = decodeDTC(highByte, lowByte, mode, "7E8")
                    dtcs.add(dtc)
                }
                
                i += 2
            }
        }
        
        return dtcs
    }

    /**
     * Get category name from code
     */
    fun getCategoryName(category: Char): String = when (category) {
        'P' -> "Powertrain"
        'B' -> "Body"
        'C' -> "Chassis"
        'U' -> "Network/Communication"
        else -> "Unknown"
    }

    /**
     * Map ECU address to module name
     */
    private fun mapECUToModule(ecuAddress: String): String {
        return when (ecuAddress.uppercase()) {
            "7E8" -> "Engine/PCM"
            "7E9" -> "Transmission/TCM"
            "7EA" -> "ABS/ESP"
            "7EB" -> "Airbag/SRS"
            "728" -> "Body Control"
            "72E" -> "Instrument Cluster"
            "72F" -> "Climate Control"
            "738" -> "Steering"
            else -> ecuAddress
        }
    }

    /**
     * Check if token is an ECU header
     */
    private fun isECUHeader(token: String): Boolean {
        if (token.length !in 3..8) return false
        
        // Common CAN IDs for OBD responses
        val commonHeaders = listOf(
            "7E8", "7E9", "7EA", "7EB", "7EC", "7ED", "7EE", "7EF",
            "728", "72E", "72F", "738", "748", "758", "768", "778", "788",
            "77A", "77B", "77C", "77D", "77E", "77F"
        )
        
        return commonHeaders.contains(token.uppercase()) || 
               token.uppercase().matches(Regex("7[0-9A-F]{2}"))
    }

    private fun cleanResponse(response: String): String {
        return response
            .replace("SEARCHING...", "")
            .replace("BUS INIT...", "")
            .replace(">", "")
            .trim()
    }
}
