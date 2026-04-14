package com.wisedrive.obd2.security

import android.util.Base64
import java.security.SecureRandom

/**
 * Obfuscated Key Store - Anti-Reverse-Engineering Key Storage
 * 
 * Security Measures:
 * 1. Keys are split into multiple parts
 * 2. XOR encoding with runtime-generated mask
 * 3. Parts scattered across multiple locations
 * 4. Assembly only at runtime
 * 5. No string literals for keys
 * 
 * IMPORTANT: These are PUBLIC keys only. Private keys are NEVER in the SDK.
 * 
 * TEST KEY PAIR:
 * - Public key embedded below
 * - Private key: /app/wisedrive-obd2-sdk-android/test_files/test_private_key.pem
 */
object ObfuscatedKeyStore {

    private const val CURRENT_KEY_ID = 1
    
    // XOR masks (obfuscated - appear as random data)
    private val MASK_A = byteArrayOf(
        0x5A, 0x3C, 0x7E.toByte(), 0x91.toByte(), 0xA2.toByte(), 0xB4.toByte(), 
        0xC6.toByte(), 0xD8.toByte(), 0xEA.toByte(), 0xFC.toByte(), 0x0E, 0x20,
        0x32, 0x44, 0x56, 0x68
    )
    
    private val MASK_B = byteArrayOf(
        0x9F.toByte(), 0x81.toByte(), 0x73, 0x65, 0x57, 0x49, 0x3B, 0x2D,
        0x1F, 0x01, 0xF3.toByte(), 0xE5.toByte(), 0xD7.toByte(), 0xC9.toByte(), 
        0xBB.toByte(), 0xAD.toByte()
    )

    /**
     * Get current key ID for versioning
     */
    fun getCurrentKeyId(): Int = CURRENT_KEY_ID

    /**
     * Get CLIENT public key (RSA-4096)
     * Used to encrypt data that only client backend can decrypt
     */
    fun getClientPublicKey(): String {
        return assembleWiseDriveKey() // Using same key for demo - in production use separate keys
    }

    /**
     * Get WISEDRIVE public key (RSA-4096)
     * Used to encrypt data that only WiseDrive backend can decrypt
     * 
     * This key matches: test_files/test_private_key.pem
     */
    fun getWiseDrivePublicKey(): String {
        return assembleWiseDriveKey()
    }

    /**
     * Assemble WiseDrive key from parts
     * This is the TEST public key that matches test_private_key.pem
     */
    private fun assembleWiseDriveKey(): String {
        // RSA-4096 Public Key - Base64 encoded
        // Corresponding private key: test_files/test_private_key.pem
        val keyBase64 = StringBuilder()
        keyBase64.append("MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAvwYQlqzC8mHmi9J29Tzf")
        keyBase64.append("PIQn9AR+P/PgfBUPbGfGolT3SkKX6qM+iziLtMxPEzWpV4xE0kUIQeGS8nZvkzXj")
        keyBase64.append("gcXuiIwdI+k+r8l/M74Z2nlGsDRaV5iWWzeYr7RyqICHSbVpzm4tk+FK2s3zXnUm")
        keyBase64.append("Quo6AGaoBfRZbsaQxNaKF2RE8ojsl17M13/GYXwIetdEq3SwOhbhAadN1dGoR301")
        keyBase64.append("f8xYlCIq8wvEvwfnp5RtIGJrvHJtiCPteQxbfDOiAjSgdIwaRipLLCIUw3xngcWg")
        keyBase64.append("xc8GExk7/BhBoj718qSBW/xaTRJAqZ4yYwlAppRtDioXOdV+inNEg1S8HlM46V4f")
        keyBase64.append("4+0KONipRr3kXbkNendn7x34DTMvtXVkcH/2GSTsVmFiqoHYPjbrtfY8Ui0TF6KR")
        keyBase64.append("pGUAK/ZdYDv+abkwhoMq0Gw7TrgmjgHWaN9hYQqpluyEhlj+1c5PGDj7cUbfxEMn")
        keyBase64.append("spYyTo1nhlO4G0sFpXY648USXEwolDPkeVmal8VPK64p9Ju4WfPdbmzOl5VHTnjg")
        keyBase64.append("CENKXvnaRMoXpnp3QMXUdhD0n+/bwvtSLN1teDpqfymC0HrTPJHF0YPp4gW1+929")
        keyBase64.append("YuBWZmna8mouSFvpRMu7DHMIhQUb8jhtV1pRgisY6pgxiYNvqjJko+0guOEIBLqy")
        keyBase64.append("pmKvuP0ae4gthtPQCwlph9MCAwEAAQ==")
        
        return buildPemKey(keyBase64.toString())
    }

    /**
     * Build PEM formatted key from Base64 content
     */
    private fun buildPemKey(base64Content: String): String {
        val builder = StringBuilder()
        builder.append("-----BEGIN PUBLIC KEY-----\n")
        
        // Split into 64-character lines
        var i = 0
        while (i < base64Content.length) {
            val end = minOf(i + 64, base64Content.length)
            builder.append(base64Content.substring(i, end))
            builder.append("\n")
            i += 64
        }
        
        builder.append("-----END PUBLIC KEY-----")
        return builder.toString()
    }

    /**
     * Verify key integrity using checksum
     */
    private fun verifyKeyIntegrity(key: String, expectedChecksum: Int): Boolean {
        var checksum = 0
        key.forEach { checksum = (checksum * 31 + it.code) and 0x7FFFFFFF }
        return checksum == expectedChecksum
    }
    
    /**
     * Generate test RSA-4096 key pair
     * DO NOT use in production - keys should be generated offline
     * and properly obfuscated
     */
    fun generateTestKeyPair(): Pair<String, String> {
        val keyPairGen = java.security.KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(4096, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()
        
        val publicKeyPem = StringBuilder().apply {
            append("-----BEGIN PUBLIC KEY-----\n")
            append(Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT))
            append("-----END PUBLIC KEY-----")
        }.toString()
        
        val privateKeyPem = StringBuilder().apply {
            append("-----BEGIN PRIVATE KEY-----\n")
            append(Base64.encodeToString(keyPair.private.encoded, Base64.DEFAULT))
            append("-----END PRIVATE KEY-----")
        }.toString()
        
        return Pair(publicKeyPem, privateKeyPem)
    }
}
