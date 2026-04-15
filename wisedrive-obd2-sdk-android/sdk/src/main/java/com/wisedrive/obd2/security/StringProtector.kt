package com.wisedrive.obd2.security

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * StringProtector - Runtime String Encryption/Decryption
 * 
 * All sensitive strings (AT commands, OBD PIDs, ECU addresses, protocol constants)
 * are stored as encrypted byte arrays. Decryption happens only at runtime.
 * 
 * A decompiler will see only byte arrays and complex key derivation - 
 * no readable strings for OBD commands, ECU addresses, or protocol logic.
 * 
 * Encryption: XOR with position-dependent key derived from multiple seeds
 */
object StringProtector {

    // Seed components scattered as byte arrays (appear as random data to decompiler)
    private val S1 = byteArrayOf(
        0x4F, 0x42, 0x44, 0x5F, 0x53, 0x45, 0x45, 0x44,
        0x5F, 0x41, 0x4C, 0x50, 0x48, 0x41, 0x5F, 0x31
    )
    private val S2 = byteArrayOf(
        0x57, 0x44, 0x5F, 0x50, 0x52, 0x4F, 0x54, 0x4F,
        0x43, 0x4F, 0x4C, 0x5F, 0x47, 0x55, 0x41, 0x52
    )
    private val S3 = byteArrayOf(
        0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(),
        0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(),
        0x13, 0x37, 0x42, 0x58, 0x7A, 0x3C, 0x9E.toByte(), 0xF1.toByte()
    )

    // Derived key (lazily computed at runtime only)
    private val derivedKey: ByteArray by lazy { deriveKey() }

    /**
     * Derive encryption key from scattered seeds using SHA-256
     * Key is never stored as a constant - always computed at runtime
     */
    private fun deriveKey(): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(S1)
        md.update(S2)
        md.update(S3)
        // Add runtime entropy from class identity
        md.update(StringProtector::class.java.name.toByteArray())
        return md.digest()
    }

    /**
     * Decrypt an encrypted byte array back to a String
     * Uses position-dependent XOR with derived key
     */
    fun d(encrypted: ByteArray): String {
        val key = derivedKey
        val decrypted = ByteArray(encrypted.size)
        for (i in encrypted.indices) {
            // Position-dependent: XOR with key[i % keyLen] + position offset
            val keyByte = key[(i + encrypted.size) % key.size]
            val positionOffset = ((i * 7 + 3) % 256).toByte()
            decrypted[i] = (encrypted[i].toInt() xor keyByte.toInt() xor positionOffset.toInt()).toByte()
        }
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Encrypt a plaintext string for storage in code
     * This is used OFFLINE to generate the encrypted constants
     * NOT called at runtime in production
     */
    fun encrypt(plaintext: String): ByteArray {
        val key = derivedKey
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        val encrypted = ByteArray(bytes.size)
        for (i in bytes.indices) {
            val keyByte = key[(i + bytes.size) % key.size]
            val positionOffset = ((i * 7 + 3) % 256).toByte()
            encrypted[i] = (bytes[i].toInt() xor keyByte.toInt() xor positionOffset.toInt()).toByte()
        }
        return encrypted
    }

    /**
     * Encrypt and return as hex string (for code generation)
     */
    fun encryptToHex(plaintext: String): String {
        return encrypt(plaintext).joinToString(",") { 
            "0x${(it.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')}.toByte()" 
        }
    }

    /**
     * Decrypt from hex byte array
     */
    fun dh(vararg bytes: Int): String {
        val ba = ByteArray(bytes.size) { bytes[it].toByte() }
        return d(ba)
    }
}
