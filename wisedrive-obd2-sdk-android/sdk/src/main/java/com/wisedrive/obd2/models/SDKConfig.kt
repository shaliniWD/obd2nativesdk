package com.wisedrive.obd2.models

/**
 * SDK initialization configuration
 * 
 * @param clientEndpoint Your backend URL to receive scan data as plain JSON (required)
 * @param licensePlate Vehicle registration/license plate number
 * @param useMock Enable mock mode for testing without real OBD device
 * @param enableLogging Enable debug logging
 * 
 * Example:
 * ```kotlin
 * val config = SDKConfig(
 *     clientEndpoint = "https://your-api.com/api/obd-data",
 *     licensePlate = "MH12AB1234",
 *     useMock = false
 * )
 * ```
 */
data class SDKConfig(
    val clientEndpoint: String,
    val licensePlate: String = "",
    val useMock: Boolean = false,
    val enableLogging: Boolean = false
)
