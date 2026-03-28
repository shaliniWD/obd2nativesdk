package com.wisedrive.obd2.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wisedrive.obd2.models.APIPayload
import com.wisedrive.obd2.models.ScanReport
import com.wisedrive.obd2.security.AdvancedEncryptionManager
import com.wisedrive.obd2.security.EncryptedBlob
import com.wisedrive.obd2.util.Logger
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Secure Analytics Manager - Encrypted Data Transmission
 * 
 * All OBD scan data is encrypted using Hybrid RSA-4096 + AES-256-GCM before
 * transmission. Only authorized recipients with the correct private key can
 * decrypt the data.
 * 
 * Data Flow:
 * 1. SDK generates scan data
 * 2. Data encrypted with recipient's public key
 * 3. Encrypted blob sent to endpoint
 * 4. Only recipient with private key can decrypt
 */
internal class SecureWiseDriveAnalytics(
    private val useMock: Boolean = false
) {
    companion object {
        private const val TAG = "SecureAnalytics"
        private const val ANALYTICS_ENDPOINT = "http://164.52.213.170:82/apiv2/webhook/obdreport/wisedrive/encrypted"
        private const val MAX_RETRIES = 10
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val gsonCompact: Gson = Gson()
    private val encryptionManager = AdvancedEncryptionManager()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var retryJob: Job? = null
    private val isSubmitted = AtomicBoolean(false)
    private var pendingPayload: APIPayload? = null
    private var lastResponse: String? = null
    private var lastPayloadJson: String? = null
    private var lastEncryptedBlob: EncryptedBlob? = null
    
    // Callbacks
    private var onPayloadPrepared: ((String) -> Unit)? = null
    private var onEncryptionComplete: ((EncryptedBlob) -> Unit)? = null
    private var onSubmissionResult: ((Boolean, String) -> Unit)? = null

    init {
        // Initialize encryption manager
        if (!encryptionManager.initialize()) {
            Logger.e(TAG, "Failed to initialize encryption manager")
        }
    }

    fun setOnPayloadPrepared(callback: (String) -> Unit) {
        onPayloadPrepared = callback
    }

    fun setOnEncryptionComplete(callback: (EncryptedBlob) -> Unit) {
        onEncryptionComplete = callback
    }

    fun setOnSubmissionResult(callback: (Boolean, String) -> Unit) {
        onSubmissionResult = callback
    }

    /**
     * Send ENCRYPTED analytics data to WiseDrive
     * Data is encrypted with WiseDrive's public key - only they can decrypt
     */
    fun sendEncryptedAnalytics(apiPayload: APIPayload, scope: CoroutineScope) {
        isSubmitted.set(false)
        pendingPayload = apiPayload
        lastResponse = null
        
        // Generate JSON (for logging only - NOT sent as plaintext)
        lastPayloadJson = gson.toJson(apiPayload)
        
        Logger.i(TAG, "=== Plaintext Payload (DEBUG ONLY) ===")
        Logger.i(TAG, lastPayloadJson ?: "null")
        Logger.i(TAG, "======================================")
        
        onPayloadPrepared?.invoke(lastPayloadJson ?: "")
        
        // Mock mode
        if (useMock) {
            Logger.i(TAG, "[MOCK MODE] Simulating encrypted submission")
            scope.launch(Dispatchers.IO) {
                delay(500)
                
                // Still perform encryption in mock mode for testing
                val encryptedBlob = encryptionManager.encryptForWiseDrive(lastPayloadJson!!)
                lastEncryptedBlob = encryptedBlob
                
                Logger.i(TAG, "[MOCK] Encrypted blob size: ${encryptedBlob.size} bytes")
                onEncryptionComplete?.invoke(encryptedBlob)
                
                isSubmitted.set(true)
                lastResponse = """{"result": "SUCCESS", "mock": true, "encrypted": true}"""
                pendingPayload = null
                onSubmissionResult?.invoke(true, lastResponse!!)
            }
            return
        }
        
        // Real encrypted submission
        retryJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            var delayMs = INITIAL_RETRY_DELAY_MS
            
            while (!isSubmitted.get() && attempt < MAX_RETRIES) {
                try {
                    Logger.d(TAG, "Encrypted analytics attempt ${attempt + 1}/$MAX_RETRIES")
                    val success = sendEncryptedToEndpoint(apiPayload)
                    if (success) {
                        Logger.i(TAG, "Encrypted analytics sent on attempt ${attempt + 1}")
                        isSubmitted.set(true)
                        pendingPayload = null
                        onSubmissionResult?.invoke(true, lastResponse ?: "SUCCESS")
                        return@launch
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Encrypted send attempt ${attempt + 1} failed: ${e.message}")
                    lastResponse = "Error: ${e.message}"
                }
                
                attempt++
                if (!isSubmitted.get() && attempt < MAX_RETRIES) {
                    Logger.d(TAG, "Retrying in ${delayMs}ms...")
                    delay(delayMs)
                    delayMs = minOf(delayMs * 2, MAX_RETRY_DELAY_MS)
                }
            }
            
            if (!isSubmitted.get()) {
                Logger.w(TAG, "Encrypted analytics failed after $MAX_RETRIES attempts")
                onSubmissionResult?.invoke(false, lastResponse ?: "Max retries exceeded")
            }
        }
    }

    /**
     * Encrypt and send to WiseDrive endpoint
     */
    private fun sendEncryptedToEndpoint(apiPayload: APIPayload): Boolean {
        val jsonPlaintext = gsonCompact.toJson(apiPayload)
        
        // Encrypt for WiseDrive (only they can decrypt)
        val encryptedBlob = encryptionManager.encryptForWiseDrive(jsonPlaintext)
        lastEncryptedBlob = encryptedBlob
        
        Logger.i(TAG, "=== ENCRYPTED PAYLOAD ===")
        Logger.i(TAG, "Magic: ${encryptedBlob.magic}")
        Logger.i(TAG, "Version: ${encryptedBlob.version}")
        Logger.i(TAG, "Key ID: ${encryptedBlob.keyId}")
        Logger.i(TAG, "Size: ${encryptedBlob.size} bytes")
        Logger.i(TAG, "Payload (Base64): ${encryptedBlob.payload.take(100)}...")
        Logger.i(TAG, "=========================")
        
        onEncryptionComplete?.invoke(encryptedBlob)
        
        // Build JSON wrapper for encrypted payload
        val encryptedRequest = mapOf(
            "version" to encryptedBlob.version,
            "keyId" to encryptedBlob.keyId,
            "timestamp" to encryptedBlob.timestamp,
            "encryptedData" to encryptedBlob.payload
        )
        
        val request = Request.Builder()
            .url(ANALYTICS_ENDPOINT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic cHJhc2FkOnByYXNhZEAxMjM=")
            .header("X-Encryption-Version", "2")
            .header("X-Key-ID", encryptedBlob.keyId.toString())
            .post(gsonCompact.toJson(encryptedRequest).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        lastResponse = responseBody
        
        return response.isSuccessful.also { success ->
            if (success) {
                Logger.i(TAG, "Encrypted endpoint returned success: $responseBody")
            } else {
                Logger.w(TAG, "Encrypted endpoint returned ${response.code}: $responseBody")
            }
        }
    }

    /**
     * Encrypt data for CLIENT backend
     * Returns encrypted blob that only client can decrypt
     */
    fun encryptForClient(scanReport: ScanReport): EncryptedBlob {
        val json = gsonCompact.toJson(scanReport)
        return encryptionManager.encryptForClient(json)
    }

    suspend fun onClientSubmit(): Boolean {
        retryJob?.cancel()
        
        if (isSubmitted.get()) return true
        if (useMock) {
            isSubmitted.set(true)
            lastResponse = """{"result": "SUCCESS", "mock": true}"""
            return true
        }
        
        pendingPayload?.let { payload ->
            return try {
                val success = sendEncryptedToEndpoint(payload)
                if (success) {
                    isSubmitted.set(true)
                    pendingPayload = null
                    onSubmissionResult?.invoke(true, lastResponse ?: "SUCCESS")
                }
                success
            } catch (e: Exception) {
                Logger.e(TAG, "Final encrypted send failed: ${e.message}")
                lastResponse = "Error: ${e.message}"
                onSubmissionResult?.invoke(false, lastResponse!!)
                false
            }
        }
        
        return false
    }

    fun isAnalyticsSubmitted(): Boolean = isSubmitted.get()
    fun getLastResponse(): String? = lastResponse
    fun getLastPayloadJson(): String? = lastPayloadJson
    fun getLastEncryptedBlob(): EncryptedBlob? = lastEncryptedBlob

    fun cancel() {
        retryJob?.cancel()
        retryJob = null
    }
}
