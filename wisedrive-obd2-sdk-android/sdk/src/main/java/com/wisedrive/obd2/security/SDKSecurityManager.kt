package com.wisedrive.obd2.security

import android.util.Base64
import com.wisedrive.obd2.models.EncryptedPayload
import com.wisedrive.obd2.util.Logger
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SDK Security Manager
 * Handles AES-256-GCM encryption and key management
 * 
 * Key is stored ONLY in memory - never persisted to disk
 */
class SDKSecurityManager {

    companion object {
        private const val TAG = "SDKSecurityManager"
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12  // 12 bytes for GCM
        private const val AUTH_TAG_LENGTH = 128  // bits
    }

    private var encryptionKey: SecretKey? = null
    private var keyId: String? = null
    private var keyExpiresAt: Long = 0

    /**
     * Initialize with encryption key from backend
     * @param base64Key Base64-encoded AES-256 key
     * @param keyId Unique key identifier
     * @param expiresAt Key expiration timestamp (epoch millis)
     */
    fun initialize(base64Key: String, keyId: String, expiresAt: Long): Boolean {
        return try {
            val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
            
            if (keyBytes.size != 32) {
                Logger.e(TAG, "Invalid key size: ${keyBytes.size} bytes (expected 32)")
                return false
            }
            
            encryptionKey = SecretKeySpec(keyBytes, "AES")
            this.keyId = keyId
            this.keyExpiresAt = expiresAt
            
            Logger.i(TAG, "Security initialized with key: $keyId")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize security: ${e.message}")
            false
        }
    }

    /**
     * Check if security is initialized with valid key
     */
    fun isInitialized(): Boolean {
        return encryptionKey != null && keyId != null
    }

    /**
     * Check if key has expired
     */
    fun isKeyExpired(): Boolean {
        return System.currentTimeMillis() > keyExpiresAt
    }

    /**
     * Encrypt report JSON using AES-256-GCM
     */
    fun encryptReport(reportJson: String): EncryptedPayload {
        val key = encryptionKey 
            ?: throw SecurityException("SDK not initialized with encryption key")
        
        if (isKeyExpired()) {
            throw SecurityException("Encryption key expired - call initialize() again")
        }
        
        val currentKeyId = keyId 
            ?: throw SecurityException("Key ID not set")
        
        // 1. Generate random 12-byte IV
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        
        // 2. Setup cipher for AES-256-GCM
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(AUTH_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        
        // 3. Add Associated Data (AAD) - keyId + timestamp for replay prevention
        val timestamp = System.currentTimeMillis().toString()
        val aad = "$currentKeyId:$timestamp"
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        
        // 4. Encrypt
        val ciphertext = cipher.doFinal(reportJson.toByteArray(Charsets.UTF_8))
        
        // 5. Create encrypted payload
        val payload = EncryptedPayload(
            keyId = currentKeyId,
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            payload = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            timestamp = timestamp,
            algorithm = "AES-256-GCM"
        )
        
        // 6. Sign the payload
        val signature = signPayload(payload)
        
        return payload.copy(signature = signature)
    }

    /**
     * Generate HMAC-SHA256 signature for tamper detection
     */
    fun signPayload(payload: EncryptedPayload): String {
        val key = encryptionKey 
            ?: throw SecurityException("SDK not initialized")
        
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        
        val data = "${payload.keyId}:${payload.iv}:${payload.payload}:${payload.timestamp}"
        val signature = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    /**
     * Clear security state
     */
    fun clear() {
        encryptionKey = null
        keyId = null
        keyExpiresAt = 0
        Logger.i(TAG, "Security state cleared")
    }

    /**
     * Get current key ID (for logging/debugging)
     */
    fun getKeyId(): String? = keyId

    /**
     * Get key expiration timestamp
     */
    fun getKeyExpiresAt(): Long = keyExpiresAt
}
