package com.wisedrive.obd2.network

import com.wisedrive.obd2.constants.DTCKnowledgeBase
import com.wisedrive.obd2.models.*

/**
 * Report Transformer
 * Converts internal ScanReport to API payload format
 */
object ReportTransformer {

    /**
     * All 15 standard module names
     */
    private val ALL_MODULES = listOf(
        "Engine",
        "ABS",
        "4WD",
        "BCM",
        "Infotainment system",
        "Immobilizer",
        "Transmission",
        "SIC",
        "HVAC",
        "Airbag",
        "EPS",
        "Instrument Cluster",
        "Head up display",
        "Radio",
        "Others"
    )

    /**
     * Transform ScanReport to API payload format
     */
    fun transform(scanReport: ScanReport, orderId: String?): APIPayload {
        // Extract DTCs from categories
        val stored = scanReport.dtcsByCategory.history   // → status = "Confirmed"
        val pending = scanReport.dtcsByCategory.pending   // → status = "Pending"
        val permanent = scanReport.dtcsByCategory.current // → status = "Permanent"

        // Combine all DTCs with status
        val allDTCs = mutableListOf<Pair<DTCDetail, String>>()
        stored.forEach { allDTCs.add(it to "Confirmed") }
        pending.forEach { allDTCs.add(it to "Pending") }
        permanent.forEach { allDTCs.add(it to "Permanent") }

        // Determine faulty modules (unique module names from DTC list)
        val faultyModules = allDTCs.map { (dtc, _) ->
            dtc.ecuSource?.let { mapECUSourceToModule(it) }
                ?: mapCategoryToModule(dtc.category)
        }.distinct()

        // Non-faulty = ALL_MODULES minus faulty
        val nonFaultyModules = ALL_MODULES.filter { it !in faultyModules }

        // Build code_details with knowledge base enrichment
        val codeDetails = allDTCs.map { (dtc, status) ->
            val knowledge = DTCKnowledgeBase.getKnowledge(dtc.code)
            CodeDetail(
                dtc = dtc.code,
                meaning = dtc.description,
                module = dtc.ecuSource?.let { mapECUSourceToModule(it) }
                    ?: mapCategoryToModule(dtc.category),
                status = status,
                descriptions = listOf(dtc.description),
                causes = knowledge?.possibleCauses ?: emptyList(),
                symptoms = knowledge?.symptoms ?: emptyList(),
                solutions = knowledge?.solutions ?: emptyList()
            )
        }

        // Extract battery voltage from live data
        val batteryVoltage = scanReport.liveData
            .find { it.name.lowercase().contains("voltage") }
            ?.value

        // Build final payload
        return APIPayload(
            license_plate = orderId ?: "UNKNOWN",
            report_url = "https://example.com/report.pdf",
            car_company = scanReport.vehicle.manufacturer ?: "Unknown",
            status = 1,
            time = scanReport.scanTimestamp,
            mechanic_name = "Wisedrive Utils",
            mechanic_email = "utils@wisedrive.in",
            vin = scanReport.vehicle.vin ?: "Unknown",
            mil_status = allDTCs.isNotEmpty(),
            scan_ended = "automatic_success",
            faulty_modules = faultyModules,
            non_faulty_modules = nonFaultyModules,
            code_details = codeDetails,
            battery_voltage = batteryVoltage
        )
    }

    /**
     * Map DTC category to module name
     */
    fun mapCategoryToModule(category: String): String = when (category) {
        "Powertrain" -> "Engine"
        "Body" -> "BCM"
        "Chassis" -> "ABS"
        "Network/Communication" -> "Others"
        "Network" -> "Others"
        else -> category
    }

    /**
     * Map ECU source names to standard module names
     */
    fun mapECUSourceToModule(ecuSource: String): String {
        val lower = ecuSource.lowercase()
        return when {
            lower.contains("engine") || lower.contains("pcm") || 
            lower.contains("dme") || lower.contains("me/") -> "Engine"
            
            lower.contains("transmission") || lower.contains("tcm") || 
            lower.contains("egs") || lower.contains("vgs") -> "Transmission"
            
            lower.contains("abs") || lower.contains("esp") || 
            lower.contains("vsc") || lower.contains("vdc") || 
            lower.contains("dsc") || lower.contains("asc") || 
            lower.contains("vsa") || lower.contains("ebcm") -> "ABS"
            
            lower.contains("airbag") || lower.contains("srs") || 
            lower.contains("sdm") || lower.contains("orc") || 
            lower.contains("rcm") -> "Airbag"
            
            lower.contains("body") || lower.contains("bcm") || 
            lower.contains("sam") || lower.contains("frm") -> "BCM"
            
            lower.contains("instrument") || lower.contains("cluster") -> "Instrument Cluster"
            
            lower.contains("climate") || lower.contains("hvac") || 
            lower.contains("ihka") -> "HVAC"
            
            lower.contains("steering") || lower.contains("eps") -> "EPS"
            
            lower.contains("4wd") || lower.contains("transfer") || 
            lower.contains("terrain") -> "4WD"
            
            lower.contains("key") || lower.contains("immobilizer") || 
            lower.contains("pats") || lower.contains("cas") -> "Immobilizer"
            
            lower.contains("infotainment") || lower.contains("mmi") || 
            lower.contains("radio") -> "Infotainment system"
            
            lower.contains("tpms") -> "Others"
            lower.contains("ipdm") -> "Others"
            
            else -> "Others"
        }
    }
}
