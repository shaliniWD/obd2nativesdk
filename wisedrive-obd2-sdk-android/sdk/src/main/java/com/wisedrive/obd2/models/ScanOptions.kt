package com.wisedrive.obd2.models

/**
 * Options for running a full scan
 */
data class ScanOptions(
    val orderId: String? = null,
    val manufacturer: String? = null,      // e.g., "tata", "hyundai", "maruti"
    val year: Int? = null,
    val onProgress: ((ScanStage) -> Unit)? = null
)
