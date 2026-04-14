package com.wisedrive.obd2.models

/**
 * SDK initialization configuration
 */
data class SDKConfig(
    val apiKey: String,
    val baseUrl: String = "http://faircar.in:82",
    val useMock: Boolean = false,
    val enableLogging: Boolean = false
)
