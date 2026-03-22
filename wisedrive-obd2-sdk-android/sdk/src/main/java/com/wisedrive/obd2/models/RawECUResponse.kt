package com.wisedrive.obd2.models

/**
 * Raw ECU response for debugging/logging
 */
data class RawECUResponse(
    val command: String,
    val rawResponse: String,
    val timestamp: Long,
    val durationMs: Long
)

/**
 * DTC scan result from a specific mode
 */
data class DTCScanResult(
    val dtcBytes: String,
    val rawResponse: String,
    val durationMs: Long
)

/**
 * VIN fetch result
 */
data class VINResult(
    val vin: String?,
    val rawResponse: String
)

/**
 * Enhanced DTC result from UDS scanning
 */
data class EnhancedDTCResult(
    val dtcBytes: String,
    val rawResponse: String,
    val ecuAddress: String?,
    val bufferFullDetected: Boolean = false
)

/**
 * Manufacturer scan progress
 */
data class ManufacturerScanProgress(
    val phase: String,
    val component: String,
    val index: Int,
    val total: Int,
    val status: String
)

/**
 * Manufacturer scan result
 */
data class ManufacturerScanResult(
    val dtcs: List<ManufacturerDTC>,
    val modulesScanned: List<String>,
    val rawResponses: Map<String, String>,
    val config: ManufacturerConfig?
)

/**
 * Module scan result
 */
data class ModuleScanResult(
    val moduleName: String,
    val txId: String,
    val dtcs: List<ManufacturerDTC>,
    val rawResponse: String
)

/**
 * Module address definition
 */
data class ModuleAddress(
    val name: String,
    val txId: String,
    val rxId: String
)

/**
 * Parsed DTC response
 */
data class ParsedDTCResponse(
    val totalDTCs: Int,
    val dtcs: List<DTCResult>,
    val ecuResponses: Map<String, String>,
    val debugInfo: String? = null
)
