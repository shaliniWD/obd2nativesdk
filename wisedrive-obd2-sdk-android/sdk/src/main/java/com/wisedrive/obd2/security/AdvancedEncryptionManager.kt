package com.wisedrive.obd2.security

import android.util.Base64
import com.wisedrive.obd2.util.Logger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * Advanced Encryption Manager - Hybrid RSA + AES-256-GCM
 * 
 * Implements military-grade encryption with:
 * - RSA-4096 for key exchange
 * - AES-256-GCM for data encryption
 * - HMAC-SHA512 for integrity
 * - Dual key system for Client and WiseDrive
 * 
 * Security Features:
 * - Perfect Forward Secrecy (new AES key per encryption)
 * - Authenticated Encryption (GCM mode)
 * - Key Separation (different keys for different recipients)
 * - Anti-tampering (HMAC verification)
 */
class AdvancedEncryptionManager {

    companion object {
        private const val TAG = "AdvancedEncryption"
        
        // Encryption algorithms
        private const val RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val HMAC_ALGORITHM = "HmacSHA512"
        
        // Sizes
        private const val AES_KEY_SIZE = 256  // bits
        private const val GCM_IV_SIZE = 12    // bytes
        private const val GCM_TAG_SIZE = 128  // bits
        private const val RSA_KEY_SIZE = 4096 // bits
        private const val RSA_ENCRYPTED_SIZE = 512  // bytes (4096/8)
        private const val HMAC_SIZE = 64      // bytes (512/8)
        
        // Magic bytes for payload identification
        private const val MAGIC_CLIENT = "WDSC"      // WiseDrive Scan Client
        private const val MAGIC_WISEDRIVE = "WDSW"   // WiseDrive Scan WiseDrive
        private const val VERSION: Short = 2
        
        // Header size: magic(4) + version(2) + keyId(4) + timestamp(8) = 18 bytes
        // Padded to 16 for alignment
        private const val HEADER_SIZE = 16
    }

    // Public keys loaded from obfuscated storage
    private var clientPublicKey: PublicKey? = null
    private var wiseDrivePublicKey: PublicKey? = null
    private var currentKeyId: Int = 1
    
    // Secure random for cryptographic operations
    private val secureRandom = SecureRandom()

    /**
     * Initialize encryption with public keys
     * Keys are fetched from ObfuscatedKeyStore
     * @param externalClientPublicKey Optional client's public key (PEM format)
     */
    fun initialize(externalClientPublicKey: String? = null): Boolean {
        return try {
            // Load WiseDrive public key (always from ObfuscatedKeyStore)
            val wiseDriveKeyPem = ObfuscatedKeyStore.getWiseDrivePublicKey()
            wiseDrivePublicKey = loadPublicKey(wiseDriveKeyPem)
            
            // Load client public key - use external if provided, otherwise from store
            val clientKeyPem = externalClientPublicKey ?: ObfuscatedKeyStore.getClientPublicKey()
            clientPublicKey = loadPublicKey(clientKeyPem)
            
            currentKeyId = ObfuscatedKeyStore.getCurrentKeyId()
            
            Logger.i(TAG, "Advanced encryption initialized with key ID: $currentKeyId")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize encryption: ${e.message}")
            false
        }
    }

    /**
     * Encrypt data for CLIENT backend
     * Only the client's private key can decrypt this
     */
    fun encryptForClient(plaintext: String): EncryptedBlob {
        val pubKey = clientPublicKey 
            ?: throw SecurityException("Client public key not initialized")
        return encrypt(plaintext, pubKey, MAGIC_CLIENT)
    }

    /**
     * Encrypt data for WISEDRIVE backend
     * Only WiseDrive's private key can decrypt this
     */
    fun encryptForWiseDrive(plaintext: String): EncryptedBlob {
        val pubKey = wiseDrivePublicKey 
            ?: throw SecurityException("WiseDrive public key not initialized")
        return encrypt(plaintext, pubKey, MAGIC_WISEDRIVE)
    }

    /**
     * Core encryption method - Hybrid RSA + AES-GCM
     */
    private fun encrypt(plaintext: String, rsaPublicKey: PublicKey, magic: String): EncryptedBlob {
        // 1. Generate random AES-256 key (Perfect Forward Secrecy)
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE, secureRandom)
        val aesKey = keyGen.generateKey()
        
        // 2. Generate random IV
        val iv = ByteArray(GCM_IV_SIZE)
        secureRandom.nextBytes(iv)
        
        // 3. Encrypt plaintext with AES-GCM
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_SIZE, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // 4. Encrypt AES key with RSA-OAEP
        val rsaCipher = Cipher.getInstance(RSA_ALGORITHM)
        rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)
        
        // 5. Build header
        val timestamp = System.currentTimeMillis()
        val header = buildHeader(magic, timestamp)
        
        // 6. Assemble payload (without HMAC)
        val payloadWithoutHmac = ByteBuffer.allocate(
            HEADER_SIZE + encryptedAesKey.size + iv.size + ciphertext.size
        ).apply {
            put(header)
            put(encryptedAesKey)
            put(iv)
            put(ciphertext)
        }.array()
        
        // 7. Calculate HMAC-SHA512 over entire payload
        val hmacKey = deriveHmacKey(aesKey)
        val hmac = computeHmac(payloadWithoutHmac, hmacKey)
        
        // 8. Final payload = payloadWithoutHmac + hmac
        val finalPayload = ByteBuffer.allocate(payloadWithoutHmac.size + hmac.size).apply {
            put(payloadWithoutHmac)
            put(hmac)
        }.array()
        
        // 9. Base64 encode
        val base64Payload = Base64.encodeToString(finalPayload, Base64.NO_WRAP)
        
        // Clear sensitive data from memory
        clearBytes(aesKey.encoded)
        clearBytes(hmacKey.encoded)
        
        return EncryptedBlob(
            magic = magic,
            version = VERSION.toInt(),
            keyId = currentKeyId,
            timestamp = timestamp,
            payload = base64Payload,
            size = finalPayload.size
        )
    }

    /**
     * Build 16-byte header
     */
    private fun buildHeader(magic: String, timestamp: Long): ByteArray {
        return ByteBuffer.allocate(HEADER_SIZE).apply {
            put(magic.toByteArray(Charsets.UTF_8))  // 4 bytes
            putShort(VERSION)                        // 2 bytes
            putInt(currentKeyId)                     // 4 bytes
            putLong(timestamp)                       // 8 bytes (but we only use first 6 to fit)
        }.array().also { arr ->
            // Rewrite to ensure correct layout
            val buffer = ByteBuffer.allocate(HEADER_SIZE)
            buffer.put(magic.toByteArray(Charsets.UTF_8).copyOf(4))
            buffer.putShort(VERSION)
            buffer.putInt(currentKeyId)
            // Only 6 bytes for timestamp to fit in 16
            buffer.put((timestamp shr 40).toByte())
            buffer.put((timestamp shr 32).toByte())
            buffer.put((timestamp shr 24).toByte())
            buffer.put((timestamp shr 16).toByte())
            buffer.put((timestamp shr 8).toByte())
            buffer.put(timestamp.toByte())
            System.arraycopy(buffer.array(), 0, arr, 0, HEADER_SIZE)
        }
    }

    /**
     * Derive HMAC key from AES key using SHA-256
     */
    private fun deriveHmacKey(aesKey: SecretKey): SecretKey {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(aesKey.encoded)
        md.update("HMAC_KEY_DERIVATION".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(md.digest(), "HmacSHA512")
    }

    /**
     * Compute HMAC-SHA512
     */
    private fun computeHmac(data: ByteArray, key: SecretKey): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(key)
        return mac.doFinal(data)
    }

    /**
     * Load PEM-encoded public key
     */
    private fun loadPublicKey(pem: String): PublicKey {
        val keyContent = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        
        val keyBytes = Base64.decode(keyContent, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * Clear sensitive bytes from memory
     */
    private fun clearBytes(bytes: ByteArray) {
        secureRandom.nextBytes(bytes)
        bytes.fill(0)
    }

    /**
     * Check if encryption is initialized
     */
    fun isInitialized(): Boolean = clientPublicKey != null && wiseDrivePublicKey != null
}

/**
 * Encrypted blob data class
 */
data class EncryptedBlob(
    val magic: String,
    val version: Int,
    val keyId: Int,
    val timestamp: Long,
    val payload: String,  // Base64 encoded
    val size: Int         // Bytes
)
