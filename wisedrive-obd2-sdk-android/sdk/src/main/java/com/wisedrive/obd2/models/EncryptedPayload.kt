package com.wisedrive.obd2.models

/**
 * Encrypted payload returned to host app - OPAQUE to app
 */
data class EncryptedPayload(
    val keyId: String,
    val iv: String,          // Base64 encoded 12-byte IV
    val payload: String,     // Base64(AES-GCM ciphertext + auth tag)
    val timestamp: String,
    val algorithm: String,
    val signature: String? = null  // HMAC-SHA256 signature
)
