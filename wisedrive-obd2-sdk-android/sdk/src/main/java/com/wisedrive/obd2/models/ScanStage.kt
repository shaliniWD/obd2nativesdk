package com.wisedrive.obd2.models

/**
 * Represents a scan stage for progress reporting
 */
data class ScanStage(
    val id: StageId,
    val label: String,
    val status: StageStatus,
    val detail: String? = null
)

enum class StageId {
    INIT,
    VIN,
    MIL_STATUS,
    DTC_STORED,
    DTC_PENDING,
    DTC_PERMANENT,
    MANUFACTURER,
    LIVE_DATA,
    COMPLETE
}

enum class StageStatus {
    RUNNING,
    COMPLETED,
    ERROR,
    SKIPPED
}
