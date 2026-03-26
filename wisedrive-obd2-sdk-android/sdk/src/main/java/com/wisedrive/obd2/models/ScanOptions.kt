package com.wisedrive.obd2.models

/**
 * Options for running a full scan
 * 
 * @param registrationNumber Vehicle registration/license plate number (MANDATORY)
 * @param trackingId WiseDrive Tracking ID / Order ID (MANDATORY)
 * @param manufacturer Vehicle manufacturer ID (e.g., "tata", "hyundai", "maruti")
 * @param year Vehicle model year
 * @param onProgress Callback for scan progress updates
 */
data class ScanOptions(
    val registrationNumber: String,         // MANDATORY - Vehicle registration number (e.g., MH12AB1234)
    val trackingId: String,                 // MANDATORY - WiseDrive Tracking/Order ID (e.g., ORD6894331)
    val manufacturer: String? = null,       // e.g., "tata", "hyundai", "maruti"
    val year: Int? = null,
    val onProgress: ((ScanStage) -> Unit)? = null
) {
    init {
        require(registrationNumber.isNotBlank()) { 
            "Registration number is required and cannot be blank" 
        }
        require(trackingId.isNotBlank()) { 
            "Tracking ID is required and cannot be blank" 
        }
    }
}
