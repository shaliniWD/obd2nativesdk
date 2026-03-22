package com.wisedrive.obd2.protocol

import com.wisedrive.obd2.constants.OBDPIDs
import com.wisedrive.obd2.constants.DTCDescriptions
import com.wisedrive.obd2.constants.DTCKnowledgeBase
import com.wisedrive.obd2.models.*
import com.wisedrive.obd2.util.Logger
import java.text.SimpleDateFormat
import java.util.*

/**
 * Live Data Parser - Parses OBD-II PID responses and generates scan reports
 */
object LiveDataParser {

    private const val TAG = "LiveDataParser"

    /**
     * Parse live data response for a specific PID
     */
    fun parseLiveDataResponse(pidCode: String, response: String): LiveDataReading? {
        val pidDef = OBDPIDs.getPIDDefinition(pidCode.uppercase()) ?: return null
        
        val cleaned = cleanResponse(response)
        val dataBytes = extractDataBytes(pidCode, cleaned)
        
        if (dataBytes.isEmpty() || dataBytes.size < pidDef.bytes) {
            Logger.w(TAG, "Not enough data bytes for PID $pidCode: ${dataBytes.size} < ${pidDef.bytes}")
            return null
        }
        
        // Apply formula
        val value = try {
            pidDef.formula(dataBytes.take(pidDef.bytes))
        } catch (e: Exception) {
            Logger.e(TAG, "Formula error for PID $pidCode: ${e.message}")
            return null
        }
        
        // Format display value
        val displayValue = formatDisplayValue(value, pidDef.unit)
        
        return LiveDataReading(
            pid = pidCode.uppercase(),
            name = pidDef.name,
            shortName = pidDef.shortName,
            value = value,
            displayValue = displayValue,
            unit = pidDef.unit,
            category = pidDef.category,
            timestamp = System.currentTimeMillis(),
            rawHex = dataBytes.joinToString(" ") { String.format("%02X", it) }
        )
    }

    /**
     * Extract data bytes from OBD response
     */
    private fun extractDataBytes(pid: String, response: String): List<Int> {
        val tokens = response.split("\\s+".toRegex())
        
        // Strategy 1: Find "41 {PID}" marker
        for (i in 0 until tokens.size - 1) {
            if (tokens[i].equals("41", ignoreCase = true) && 
                tokens[i + 1].equals(pid, ignoreCase = true)) {
                
                // Data bytes start after PID
                return tokens.drop(i + 2)
                    .take(4) // Max 4 data bytes
                    .mapNotNull { it.toIntOrNull(16) }
            }
        }
        
        // Strategy 2: Handle multi-line with ECU header
        val lines = response.split("\r", "\n").filter { it.isNotBlank() }
        for (line in lines) {
            val lineTokens = line.trim().split("\\s+".toRegex())
            
            for (i in 0 until lineTokens.size - 1) {
                if (lineTokens[i].equals("41", ignoreCase = true) && 
                    lineTokens[i + 1].equals(pid, ignoreCase = true)) {
                    
                    return lineTokens.drop(i + 2)
                        .take(4)
                        .mapNotNull { it.toIntOrNull(16) }
                }
            }
        }
        
        return emptyList()
    }

    /**
     * Format value for display
     */
    private fun formatDisplayValue(value: Double, unit: String): String {
        return when (unit) {
            "RPM" -> "${value.toInt()} RPM"
            "km/h" -> "${value.toInt()} km/h"
            "C" -> "${String.format("%.1f", value)} C"
            "%" -> "${String.format("%.1f", value)}%"
            "V" -> "${String.format("%.2f", value)} V"
            "kPa" -> "${String.format("%.1f", value)} kPa"
            "g/s" -> "${String.format("%.2f", value)} g/s"
            "L/h" -> "${String.format("%.2f", value)} L/h"
            "sec" -> "${value.toInt()} sec"
            "km" -> "${value.toInt()} km"
            "deg" -> "${String.format("%.1f", value)} deg"
            else -> "${String.format("%.2f", value)} $unit"
        }
    }

    /**
     * Create complete scan report JSON
     */
    fun createScanResultJSON(
        scanId: String,
        scanTimestamp: String,
        scanDuration: Long,
        protocol: String,
        vehicle: VehicleInfo,
        storedDTCs: List<DTCBasic>,
        pendingDTCs: List<DTCBasic>,
        permanentDTCs: List<DTCBasic>,
        liveData: List<LiveDataReading>,
        scanCycles: Int,
        orderId: String?
    ): ScanReport {
        
        // Convert DTCBasic to DTCDetail with knowledge base enrichment
        val historyDetails = storedDTCs.map { enrichDTC(it) }
        val pendingDetails = pendingDTCs.map { enrichDTC(it) }
        val currentDetails = permanentDTCs.map { enrichDTC(it) }
        
        // Calculate summary
        val allDTCs = historyDetails + pendingDetails + currentDetails
        val uniqueCodes = allDTCs.map { it.code }.distinct()
        
        val criticalCount = allDTCs.count { it.severity == "Critical" }
        val importantCount = allDTCs.count { it.severity == "Important" }
        val nonCriticalCount = allDTCs.count { it.severity == "Non-Critical" }
        
        val summary = ScanSummary(
            totalDTCs = uniqueCodes.size,
            byType = DTCTypeCounts(
                critical = criticalCount,
                important = importantCount,
                nonCritical = nonCriticalCount
            ),
            byCategory = DTCCategoryCounts(
                history = historyDetails.size,
                current = currentDetails.size,
                pending = pendingDetails.size
            ),
            totalLiveReadings = liveData.size,
            scanCycles = scanCycles
        )
        
        // MIL Status based on DTCs
        val milStatus = MILStatus(
            on = allDTCs.isNotEmpty(),
            dtcCount = uniqueCodes.size
        )
        
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        
        return ScanReport(
            inspectionId = orderId,
            scanId = scanId,
            scanTimestamp = scanTimestamp,
            scanDuration = scanDuration,
            vehicle = vehicle,
            protocol = protocol,
            milStatus = milStatus,
            diagnosticTroubleCodes = allDTCs,
            dtcsByCategory = DTCsByCategory(
                history = historyDetails,
                current = currentDetails,
                pending = pendingDetails
            ),
            liveData = liveData,
            summary = summary,
            apiVersion = "2.0",
            generatedAt = now
        )
    }

    /**
     * Enrich DTC with description and knowledge base
     */
    private fun enrichDTC(basic: DTCBasic): DTCDetail {
        val severity = DTCDescriptions.getSeverity(basic.code)
        val knowledge = DTCKnowledgeBase.getKnowledge(basic.code)
        
        return DTCDetail(
            code = basic.code,
            category = basic.category,
            description = basic.description,
            severity = severity,
            possibleCauses = knowledge?.possibleCauses?.take(5) ?: emptyList(),
            symptoms = knowledge?.symptoms?.take(5) ?: emptyList(),
            solutions = knowledge?.solutions?.take(5) ?: emptyList(),
            isManufacturerSpecific = DTCDescriptions.isManufacturerSpecific(basic.code),
            ecuSource = basic.ecuSource
        )
    }

    private fun cleanResponse(response: String): String {
        return response
            .replace("SEARCHING...", "")
            .replace("BUS INIT...", "")
            .replace(">", "")
            .replace("\r\r", " ")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
    }
}
