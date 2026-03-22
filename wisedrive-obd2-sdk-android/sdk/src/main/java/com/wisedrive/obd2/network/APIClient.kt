package com.wisedrive.obd2.network

import com.wisedrive.obd2.models.EncryptedPayload
import com.wisedrive.obd2.util.Logger
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Interface for API Client operations
 */
interface APIClientInterface {
    suspend fun fetchEncryptionKey(): Triple<String, String, Long>?
    suspend fun submitReport(encryptedPayload: EncryptedPayload): Boolean
}

/**
 * API Client for SDK initialization and report submission
 */
open class APIClient(
    private val baseUrl: String = "https://wisedrive.com:81",
    private val apiKey: String = ""
) : APIClientInterface {
    companion object {
        private const val TAG = "APIClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch encryption key from backend
     * Returns: Triple(base64Key, keyId, expiresAt)
     */
    override suspend fun fetchEncryptionKey(): Triple<String, String, Long>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/sdk/init")
                .header("X-SDK-API-Key", apiKey)
                .header("X-SDK-Platform", "android")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                val initResponse = gson.fromJson(body, InitResponse::class.java)
                
                Logger.i(TAG, "Encryption key fetched: ${initResponse.keyId}")
                
                Triple(
                    initResponse.encryptionKey,
                    initResponse.keyId,
                    initResponse.expiresAt
                )
            } else {
                Logger.e(TAG, "Failed to fetch key: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error fetching key: ${e.message}")
            null
        }
    }

    /**
     * Submit encrypted report to backend
     */
    override suspend fun submitReport(encryptedPayload: EncryptedPayload): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(encryptedPayload)
            
            val request = Request.Builder()
                .url("$baseUrl/apiv2/webhook/obdreport/wisedrive")
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic cHJhc2FkOnByYXNhZEAxMjM=")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                Logger.i(TAG, "Report submitted successfully")
                true
            } else {
                Logger.e(TAG, "Report submission failed: ${response.code}")
                false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error submitting report: ${e.message}")
            false
        }
    }

    private data class InitResponse(
        val encryptionKey: String,
        val keyId: String,
        val expiresAt: Long
    )
}

/**
 * Mock API Client for testing without real backend
 */
class MockAPIClient : APIClientInterface {
    
    companion object {
        private const val TAG = "MockAPIClient"
        
        // Mock encryption key (32 bytes = 256 bits, base64 encoded)
        private const val MOCK_ENCRYPTION_KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
    }

    override suspend fun fetchEncryptionKey(): Triple<String, String, Long>? = withContext(Dispatchers.IO) {
        // Simulate network delay
        delay(500)
        
        val keyId = UUID.randomUUID().toString()
        val expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours
        
        Logger.i(TAG, "Mock encryption key generated: $keyId")
        
        Triple(MOCK_ENCRYPTION_KEY, keyId, expiresAt)
    }

    override suspend fun submitReport(encryptedPayload: EncryptedPayload): Boolean = withContext(Dispatchers.IO) {
        // Simulate network delay
        delay(1000)
        
        Logger.i(TAG, "Mock report submitted: keyId=${encryptedPayload.keyId}")
        Logger.d(TAG, "Payload size: ${encryptedPayload.payload.length} chars")
        
        // Always succeed in mock mode
        true
    }
}
