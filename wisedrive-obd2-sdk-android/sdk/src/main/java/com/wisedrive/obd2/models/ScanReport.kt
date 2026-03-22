package com.wisedrive.obd2.models

/**
 * Complete scan report - internal format
 */
data class ScanReport(
    val inspectionId: String?,
    val scanId: String,
    val scanTimestamp: String,              // ISO 8601
    val scanDuration: Long,                 // milliseconds
    val vehicle: VehicleInfo,
    val protocol: String,                   // e.g., "ISO 15765-4 CAN 11/500"
    val milStatus: MILStatus,
    val diagnosticTroubleCodes: List<DTCDetail>,
    val dtcsByCategory: DTCsByCategory,
    val liveData: List<LiveDataReading>,
    val summary: ScanSummary,
    val apiVersion: String = "2.0",
    val generatedAt: String                 // ISO 8601
)

data class VehicleInfo(
    val manufacturer: String?,
    val manufacturerId: String?,
    val year: Int?,
    val vin: String?
)

data class MILStatus(
    val on: Boolean,
    val dtcCount: Int
)

data class DTCsByCategory(
    val history: List<DTCDetail>,       // Stored DTCs (Mode 03)
    val current: List<DTCDetail>,       // Permanent DTCs (Mode 0A)
    val pending: List<DTCDetail>        // Pending DTCs (Mode 07)
)

data class DTCDetail(
    val code: String,
    val category: String,
    val description: String,
    val severity: String,                  // "Critical", "Important", "Non-Critical"
    val possibleCauses: List<String>,
    val symptoms: List<String>,
    val solutions: List<String>,
    val isManufacturerSpecific: Boolean = false,
    val ecuSource: String? = null
)

data class ScanSummary(
    val totalDTCs: Int,
    val byType: DTCTypeCounts,
    val byCategory: DTCCategoryCounts,
    val totalLiveReadings: Int,
    val scanCycles: Int
)

data class DTCTypeCounts(
    val critical: Int,
    val important: Int,
    val nonCritical: Int
)

data class DTCCategoryCounts(
    val history: Int,
    val current: Int,
    val pending: Int
)
