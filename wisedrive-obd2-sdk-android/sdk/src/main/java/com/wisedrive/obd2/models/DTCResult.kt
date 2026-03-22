package com.wisedrive.obd2.models

/**
 * Represents a single Diagnostic Trouble Code result
 */
data class DTCResult(
    val code: String,                      // e.g., "P0133", "B1601", "C0035", "U0100"
    val category: Char,                    // 'P', 'B', 'C', 'U'
    val categoryName: String,              // "Powertrain", "Body", "Chassis", "Network"
    val rawHex: String,                    // Original hex bytes
    val mode: String? = null,              // "03", "07", "0A"
    val description: String,               // Human-readable description
    val isManufacturerSpecific: Boolean = false,
    var ecuSource: String? = null          // "Engine/PCM", "Transmission", "ABS", etc.
)

/**
 * Basic DTC info for report building
 */
data class DTCBasic(
    val code: String,
    val category: String,
    val description: String,
    var ecuSource: String? = null
)

/**
 * Manufacturer-specific DTC from UDS scanning
 */
data class ManufacturerDTC(
    val code: String,
    val rawBytes: String,
    val status: Int,
    val statusText: String,
    var ecuSource: String? = null
)
