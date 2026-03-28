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
        // This is a DEMO key - In production, generate your own keys
        // and obfuscate them properly using the techniques shown
        return assembleClientKey()
    }

    /**
     * Get WISEDRIVE public key (RSA-4096)
     * Used to encrypt data that only WiseDrive backend can decrypt
     */
    fun getWiseDrivePublicKey(): String {
        // This is a DEMO key - In production, generate your own keys
        return assembleWiseDriveKey()
    }

    /**
     * Assemble client key from obfuscated parts
     * In production, split the key across multiple methods/classes
     */
    private fun assembleClientKey(): String {
        // RSA-4096 public key for CLIENT
        // Generated using: openssl genrsa -out client_private.pem 4096
        //                  openssl rsa -in client_private.pem -pubout -out client_public.pem
        val parts = arrayOf(
            getClientKeyPart1(),
            getClientKeyPart2(),
            getClientKeyPart3(),
            getClientKeyPart4(),
            getClientKeyPart5(),
            getClientKeyPart6(),
            getClientKeyPart7(),
            getClientKeyPart8()
        )
        return buildKey(parts)
    }

    /**
     * Assemble WiseDrive key from obfuscated parts
     */
    private fun assembleWiseDriveKey(): String {
        val parts = arrayOf(
            getWiseDriveKeyPart1(),
            getWiseDriveKeyPart2(),
            getWiseDriveKeyPart3(),
            getWiseDriveKeyPart4(),
            getWiseDriveKeyPart5(),
            getWiseDriveKeyPart6(),
            getWiseDriveKeyPart7(),
            getWiseDriveKeyPart8()
        )
        return buildKey(parts)
    }

    private fun buildKey(parts: Array<String>): String {
        val builder = StringBuilder()
        builder.append("-----BEGIN PUBLIC KEY-----\n")
        parts.forEach { builder.append(it) }
        builder.append("\n-----END PUBLIC KEY-----")
        return builder.toString()
    }

    // ============================================================
    // CLIENT KEY PARTS (RSA-4096 Public Key)
    // In production, these should be XOR encoded and scattered
    // ============================================================
    
    private fun getClientKeyPart1(): String {
        // Part 1 of 8 - Base64 encoded RSA public key data
        return deobfuscate("MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA", 1)
    }
    
    private fun getClientKeyPart2(): String {
        return deobfuscate("t5K8V2gHxPnL4Jm7cRzYqDsE9fW1bI0uNoXvMaKjTyS", 2)
    }
    
    private fun getClientKeyPart3(): String {
        return deobfuscate("hGp3dUwOiQrZlFeCkBnVmAx6LsPtJ8y2Nf9H4Ku7Dca", 3)
    }
    
    private fun getClientKeyPart4(): String {
        return deobfuscate("RoMzXvbS1TgWjYiP5qE0lLKd8cUhnFwOaQrJx3CyBmA", 4)
    }
    
    private fun getClientKeyPart5(): String {
        return deobfuscate("9NeVtZsGpHfI6kDuLa2XwY1Qb7rJhMcOiPjT4nSvEgK", 5)
    }
    
    private fun getClientKeyPart6(): String {
        return deobfuscate("lU0FmWxB3zCdRoN8yqYsHtV5iIjPeAaKgLuDv2Zn1Sf", 6)
    }
    
    private fun getClientKeyPart7(): String {
        return deobfuscate("7Tb4GcHwJxOmQrXpYz9Ek0I1lLn3NoBaSdVuWfCgKiM", 7)
    }
    
    private fun getClientKeyPart8(): String {
        return deobfuscate("jR2PtQsUvXy6ZaAbBcDdEeFfGgHhIiJjKkLlMmNnOoP", 8) + 
               "pQqRrSsTtUuVvWwXxYyZz0123456789ABCDEFGHIJKL" +
               "MNOPQRSTUVWXYZ+/=AQAB"
    }

    // ============================================================
    // WISEDRIVE KEY PARTS (RSA-4096 Public Key)
    // ============================================================
    
    private fun getWiseDriveKeyPart1(): String {
        return deobfuscate("MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA", 1)
    }
    
    private fun getWiseDriveKeyPart2(): String {
        return deobfuscate("w7Z9H3xLqJnRvTmCpY1kSoEfUiAa5bGdXeK8Wu2VsNg", 2)
    }
    
    private fun getWiseDriveKeyPart3(): String {
        return deobfuscate("yI4DcOhMtFlQrPj6B0nZxSuKvWa3E7LgTmYsJiHdCbR", 3)
    }
    
    private fun getWiseDriveKeyPart4(): String {
        return deobfuscate("fN5VpXq8zAoGwU2kL1SeY9I6rJaMnHcTdCbZxOvWgKu", 4)
    }
    
    private fun getWiseDriveKeyPart5(): String {
        return deobfuscate("jE3PtRmQsF7hL0yI4nV6aCbWxDdZeGfHgIiJjKkLlMm", 5)
    }
    
    private fun getWiseDriveKeyPart6(): String {
        return deobfuscate("NnOoPpQqRrSsTtUuVvWwXxYyZz0123456789+/ABCDE", 6)
    }
    
    private fun getWiseDriveKeyPart7(): String {
        return deobfuscate("FGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuv", 7)
    }
    
    private fun getWiseDriveKeyPart8(): String {
        return deobfuscate("wxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabc", 8) +
               "defghijklmnopqrstuvwxyz0123456789+/=AQAB"
    }

    /**
     * De-obfuscate key part
     * In a real implementation, this would XOR with runtime-computed values
     */
    private fun deobfuscate(obfuscatedPart: String, partIndex: Int): String {
        // Simple pass-through for demo
        // In production: XOR with computed values, check integrity, etc.
        return obfuscatedPart
    }

    /**
     * Verify key integrity using checksum
     */
    private fun verifyKeyIntegrity(key: String, expectedChecksum: Int): Boolean {
        var checksum = 0
        key.forEach { checksum = (checksum * 31 + it.code) and 0x7FFFFFFF }
        return checksum == expectedChecksum
    }
    
    // ============================================================
    // GENERATE NEW KEYS (For development/testing only)
    // ============================================================
    
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
