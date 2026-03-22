package com.wisedrive.obd2.models

/**
 * MIL (Malfunction Indicator Lamp) status result
 */
data class MILStatusResult(
    val milOn: Boolean,
    val dtcCount: Int,
    val rawResponse: String? = null
)
