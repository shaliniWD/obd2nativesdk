package com.wisedrive.obd2.models

/**
 * SDK initialization configuration
 * 
 * @param apiKey Client's API key for authentication
 * @param clientEndpoint Client's backend URL to receive encrypted scan data (optional)
 * @param clientPublicKey Client's RSA public key for encryption (optional, PEM format)
 * @param useMock Enable mock mode for testing without real OBD device
 * @param enableLogging Enable debug logging
 * 
 * Example:
 * ```kotlin
 * val config = SDKConfig(
 *     apiKey = "your-api-key",
 *     clientEndpoint = "https://your-api.com/obd/receive",
 *     clientPublicKey = "-----BEGIN PUBLIC KEY-----\n...",
 *     useMock = false,
 *     enableLogging = true
 * )
 * ```
 */
data class SDKConfig(
    val apiKey: String,
    val clientEndpoint: String? = null,           // Client's backend URL (optional)
    val clientPublicKey: String? = null,          // Client's RSA public key (optional)
    val useMock: Boolean = false,
    val enableLogging: Boolean = false
) {
    companion object {
        // WiseDrive's internal endpoints (always used)
        const val WISEDRIVE_BASE_URL = "http://faircar.in:82"
        const val WISEDRIVE_ANALYTICS_ENDPOINT = "http://faircar.in:82/apiv2/webhook/obdreport/wisedrive"
        const val WISEDRIVE_ENCRYPTED_ENDPOINT = "http://faircar.in:82/apiv2/webhook/obdreport/wisedrive/encrypted"
    }
}
