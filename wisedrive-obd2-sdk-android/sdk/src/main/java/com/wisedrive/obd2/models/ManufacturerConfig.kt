package com.wisedrive.obd2.models

/**
 * Manufacturer ECU configuration
 */
data class ManufacturerConfig(
    val name: String,
    val ids: List<String>,              // Identifiers: e.g., ["tata", "tata motors"]
    val protocol: String,               // e.g., "CAN 11-bit 500kbps"
    val modules: List<ECUModule>
)

/**
 * ECU Module definition with CAN addresses
 */
data class ECUModule(
    val name: String,                   // e.g., "Engine/PCM"
    val txId: String,                   // CAN TX address, e.g., "7E0"
    val rxId: String                    // CAN RX address, e.g., "7E8"
)
