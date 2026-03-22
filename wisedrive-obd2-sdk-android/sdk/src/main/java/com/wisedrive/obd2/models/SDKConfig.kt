package com.wisedrive.obd2.models

/**
 * SDK initialization configuration
 */
data class SDKConfig(
    val apiKey: String,
    val baseUrl: String = "https://wisedrive.com:81",
    val useMock: Boolean = false,
    val enableLogging: Boolean = false
)
