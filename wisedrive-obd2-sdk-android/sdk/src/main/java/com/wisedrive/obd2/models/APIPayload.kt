package com.wisedrive.obd2.models

/**
 * API Payload - EXACT format expected by backend
 */
data class APIPayload(
    val license_plate: String,              // = orderId passed in ScanOptions
    val report_url: String,                 // = "https://example.com/report.pdf" (placeholder)
    val car_company: String,                // = vehicle.manufacturer ?? "Unknown"
    val status: Int,                        // = 1 (success)
    val time: String,                       // = scanTimestamp (ISO 8601)
    val mechanic_name: String,              // = "Wisedrive Utils"
    val mechanic_email: String,             // = "utils@wisedrive.in"
    val vin: String,                        // = vehicle.vin ?? "Unknown"
    val mil_status: Boolean,                // = true if any DTCs found
    val scan_ended: String,                 // = "automatic_success"
    val faulty_modules: List<String>,       // Modules that have DTCs
    val non_faulty_modules: List<String>,   // Modules without DTCs
    val code_details: List<CodeDetail>,     // All DTCs with full details
    val battery_voltage: Double?            // From live data PID 42 (Control Module Voltage)
)

data class CodeDetail(
    val dtc: String,                        // e.g., "P0133"
    val meaning: String,                    // DTC description
    val module: String,                     // "Engine", "BCM", "ABS", etc.
    val status: String,                     // "Confirmed", "Pending", "Permanent"
    val descriptions: List<String>,         // [description]
    val causes: List<String>,               // From DTCKnowledgeBase (top 5)
    val symptoms: List<String>,             // From DTCKnowledgeBase (top 5)
    val solutions: List<String>             // From DTCKnowledgeBase (top 5)
)

/**
 * All 15 standard module names
 */
object ModuleNames {
    val ALL_MODULES = listOf(
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
}
