package com.wisedrive.obd2.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wisedrive.obd2.models.APIPayload
import com.wisedrive.obd2.models.ScanReport
import com.wisedrive.obd2.models.SDKConfig
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
 * Dual Submission Architecture:
 * ┌─────────────────────────────────────────────────────────────┐
 * │                      SDK SCAN DATA                          │
 * │                           │                                 │
 * │         ┌─────────────────┴─────────────────┐               │
 * │         ▼                                   ▼               │
 * │  ┌──────────────────┐           ┌──────────────────┐        │
 * │  │ WiseDrive Blob   │           │ Client Blob      │        │
 * │  │ (WDSW magic)     │           │ (WDSC magic)     │        │
 * │  │                  │           │                  │        │
 * │  │ Encrypted with   │           │ Encrypted with   │        │
 * │  │ WiseDrive key    │           │ Client's key     │        │
 * │  └────────┬─────────┘           └────────┬─────────┘        │
 * │           │                              │                  │
 * │           ▼                              ▼                  │
 * │  faircar.in:9768/encrypted    client-api.com/endpoint        │
 * │  (ALWAYS sent)               (if configured)                │
 * └─────────────────────────────────────────────────────────────┘
 * 
 * - WiseDrive ALWAYS receives encrypted analytics
 * - Client receives their own encrypted copy (if configured)
 */
internal class SecureWiseDriveAnalytics(
    private val useMock: Boolean = false,
    private val clientEndpoint: String? = null,
    private val clientPublicKey: String? = null
) {
    companion object {
        private const val TAG = "SecureAnalytics"
        
        // WiseDrive's endpoint (ALWAYS used - hardcoded)
        private const val WISEDRIVE_ENDPOINT = "https://faircar.in:9768/api/obd/encrypted"
        
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
    private val isWiseDriveSubmitted = AtomicBoolean(false)
    private val isClientSubmitted = AtomicBoolean(false)
    private var pendingPayload: APIPayload? = null
    private var lastResponse: String? = null
    private var lastPayloadJson: String? = null
    private var lastWiseDriveBlob: EncryptedBlob? = null
    private var lastClientBlob: EncryptedBlob? = null
    
    // Callbacks
    private var onPayloadPrepared: ((String) -> Unit)? = null
    private var onEncryptionComplete: ((EncryptedBlob) -> Unit)? = null
    private var onSubmissionResult: ((Boolean, String) -> Unit)? = null
    private var onClientSubmissionResult: ((Boolean, String) -> Unit)? = null

    init {
        // Initialize encryption manager with client key if provided
        if (!encryptionManager.initialize(clientPublicKey)) {
            Logger.e(TAG, "Failed to initialize encryption manager")
        }
        
        if (clientEndpoint != null) {
            Logger.i(TAG, "Client endpoint configured: $clientEndpoint")
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
    
    fun setOnClientSubmissionResult(callback: (Boolean, String) -> Unit) {
        onClientSubmissionResult = callback
    }

    /**
     * Send ENCRYPTED analytics data to BOTH WiseDrive and Client (if configured)
     */
    fun sendEncryptedAnalytics(apiPayload: APIPayload, scope: CoroutineScope) {
        isWiseDriveSubmitted.set(false)
        isClientSubmitted.set(false)
        pendingPayload = apiPayload
        lastResponse = null
        
        // Generate JSON (for logging only - NOT sent as plaintext)
        lastPayloadJson = gson.toJson(apiPayload)
        
        Logger.i(TAG, "=== Preparing Encrypted Submission ===")
        Logger.d(TAG, "Payload size: ${lastPayloadJson?.length} chars")
        
        onPayloadPrepared?.invoke(lastPayloadJson ?: "")
        
        // Mock mode
        if (useMock) {
            Logger.i(TAG, "[MOCK MODE] Simulating encrypted submission")
            scope.launch(Dispatchers.IO) {
                delay(500)
                
                // Still perform encryption in mock mode for testing
                lastWiseDriveBlob = encryptionManager.encryptForWiseDrive(lastPayloadJson!!)
                Logger.i(TAG, "[MOCK] WiseDrive encrypted blob size: ${lastWiseDriveBlob?.size} bytes")
                
                if (clientEndpoint != null && clientPublicKey != null) {
                    lastClientBlob = encryptionManager.encryptForClient(lastPayloadJson!!)
                    Logger.i(TAG, "[MOCK] Client encrypted blob size: ${lastClientBlob?.size} bytes")
                }
                
                onEncryptionComplete?.invoke(lastWiseDriveBlob!!)
                
                isWiseDriveSubmitted.set(true)
                isClientSubmitted.set(clientEndpoint == null) // Mark as done if no client endpoint
                
                lastResponse = """{"result": "SUCCESS", "mock": true, "encrypted": true}"""
                pendingPayload = null
                onSubmissionResult?.invoke(true, lastResponse!!)
                onClientSubmissionResult?.invoke(true, lastResponse!!)
            }
            return
        }
        
        // Real encrypted submission
        retryJob = scope.launch(Dispatchers.IO) {
            // 1. Send to WiseDrive (ALWAYS)
            sendToWiseDrive(apiPayload)
            
            // 2. Send to Client (if configured)
            if (clientEndpoint != null) {
                sendToClient(apiPayload)
            } else {
                isClientSubmitted.set(true) // No client endpoint, mark as done
            }
        }
    }
    
    /**
     * Send encrypted data to WiseDrive endpoint (ALWAYS)
     */
    private suspend fun sendToWiseDrive(apiPayload: APIPayload) {
        var attempt = 0
        var delayMs = INITIAL_RETRY_DELAY_MS
        
        while (!isWiseDriveSubmitted.get() && attempt < MAX_RETRIES) {
            try {
                Logger.d(TAG, "WiseDrive submission attempt ${attempt + 1}/$MAX_RETRIES")
                val success = sendEncryptedToEndpoint(apiPayload, WISEDRIVE_ENDPOINT, true)
                if (success) {
                    Logger.i(TAG, "WiseDrive submission successful on attempt ${attempt + 1}")
                    isWiseDriveSubmitted.set(true)
                    onSubmissionResult?.invoke(true, lastResponse ?: "SUCCESS")
                    return
                }
            } catch (e: Exception) {
                Logger.w(TAG, "WiseDrive attempt ${attempt + 1} failed: ${e.message}")
                lastResponse = "Error: ${e.message}"
            }
            
            attempt++
            if (!isWiseDriveSubmitted.get() && attempt < MAX_RETRIES) {
                Logger.d(TAG, "Retrying WiseDrive in ${delayMs}ms...")
                delay(delayMs)
                delayMs = minOf(delayMs * 2, MAX_RETRY_DELAY_MS)
            }
        }
        
        if (!isWiseDriveSubmitted.get()) {
            Logger.w(TAG, "WiseDrive submission failed after $MAX_RETRIES attempts")
            onSubmissionResult?.invoke(false, lastResponse ?: "Max retries exceeded")
        }
    }
    
    /**
     * Send encrypted data to Client endpoint (if configured)
     */
    private suspend fun sendToClient(apiPayload: APIPayload) {
        if (clientEndpoint == null) {
            isClientSubmitted.set(true)
            return
        }
        
        var attempt = 0
        var delayMs = INITIAL_RETRY_DELAY_MS
        
        while (!isClientSubmitted.get() && attempt < MAX_RETRIES) {
            try {
                Logger.d(TAG, "Client submission attempt ${attempt + 1}/$MAX_RETRIES to $clientEndpoint")
                val success = sendEncryptedToEndpoint(apiPayload, clientEndpoint, false)
                if (success) {
                    Logger.i(TAG, "Client submission successful on attempt ${attempt + 1}")
                    isClientSubmitted.set(true)
                    onClientSubmissionResult?.invoke(true, lastResponse ?: "SUCCESS")
                    return
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Client attempt ${attempt + 1} failed: ${e.message}")
            }
            
            attempt++
            if (!isClientSubmitted.get() && attempt < MAX_RETRIES) {
                delay(delayMs)
                delayMs = minOf(delayMs * 2, MAX_RETRY_DELAY_MS)
            }
        }
        
        if (!isClientSubmitted.get()) {
            Logger.w(TAG, "Client submission failed after $MAX_RETRIES attempts")
            onClientSubmissionResult?.invoke(false, "Max retries exceeded")
        }
    }

    /**
     * Encrypt and send to specified endpoint
     * For WiseDrive internal API: license_plate sent as URL parameter
     * For Client API: everything in encrypted body
     */
    private fun sendEncryptedToEndpoint(apiPayload: APIPayload, endpoint: String, isWiseDrive: Boolean): Boolean {
        val jsonPlaintext = gsonCompact.toJson(apiPayload)
        
        // Encrypt for appropriate recipient
        val encryptedBlob = if (isWiseDrive) {
            encryptionManager.encryptForWiseDrive(jsonPlaintext).also { lastWiseDriveBlob = it }
        } else {
            encryptionManager.encryptForClient(jsonPlaintext).also { lastClientBlob = it }
        }
        
        Logger.i(TAG, "=== ENCRYPTED PAYLOAD for ${if (isWiseDrive) "WiseDrive" else "Client"} ===")
        Logger.i(TAG, "Magic: ${encryptedBlob.magic}")
        Logger.i(TAG, "Version: ${encryptedBlob.version}")
        Logger.i(TAG, "Key ID: ${encryptedBlob.keyId}")
        Logger.i(TAG, "Size: ${encryptedBlob.size} bytes")
        Logger.d(TAG, "Payload (Base64): ${encryptedBlob.payload.take(100)}...")
        
        if (isWiseDrive) {
            onEncryptionComplete?.invoke(encryptedBlob)
        }
        
        // Build JSON wrapper for encrypted payload
        val encryptedRequest = mapOf(
            "version" to encryptedBlob.version,
            "keyId" to encryptedBlob.keyId,
            "timestamp" to encryptedBlob.timestamp,
            "encryptedData" to encryptedBlob.payload
        )
        
        // Build URL - for WiseDrive, add license_plate as query parameter
        val finalUrl = if (isWiseDrive && apiPayload.license_plate.isNotBlank()) {
            val encodedPlate = java.net.URLEncoder.encode(apiPayload.license_plate, "UTF-8")
            "$endpoint?license_plate=$encodedPlate"
        } else {
            endpoint
        }
        
        Logger.i(TAG, "Sending to: $finalUrl")
        
        val request = Request.Builder()
            .url(finalUrl)
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic YWRtaW46YWRtaW5AMTIz")
            .header("X-Encryption-Version", "2")
            .header("X-Key-ID", encryptedBlob.keyId.toString())
            .post(gsonCompact.toJson(encryptedRequest).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        lastResponse = responseBody
        
        return response.isSuccessful.also { success ->
            if (success) {
                Logger.i(TAG, "${if (isWiseDrive) "WiseDrive" else "Client"} endpoint returned success: $responseBody")
            } else {
                Logger.w(TAG, "${if (isWiseDrive) "WiseDrive" else "Client"} endpoint returned ${response.code}: $responseBody")
            }
        }
    }

    /**
     * Get encrypted blob for client to use externally
     */
    fun encryptForClient(scanReport: ScanReport): EncryptedBlob {
        val json = gsonCompact.toJson(scanReport)
        return encryptionManager.encryptForClient(json)
    }

    suspend fun onClientSubmit(): Boolean {
        retryJob?.cancel()
        
        if (isWiseDriveSubmitted.get() && isClientSubmitted.get()) return true
        if (useMock) {
            isWiseDriveSubmitted.set(true)
            isClientSubmitted.set(true)
            lastResponse = """{"result": "SUCCESS", "mock": true}"""
            return true
        }
        
        pendingPayload?.let { payload ->
            return try {
                val successWD = sendEncryptedToEndpoint(payload, WISEDRIVE_ENDPOINT, true)
                if (successWD) {
                    isWiseDriveSubmitted.set(true)
                    onSubmissionResult?.invoke(true, lastResponse ?: "SUCCESS")
                }
                
                val successClient = if (clientEndpoint != null) {
                    sendEncryptedToEndpoint(payload, clientEndpoint, false).also {
                        if (it) isClientSubmitted.set(true)
                    }
                } else true
                
                if (successWD && successClient) {
                    pendingPayload = null
                }
                successWD && successClient
            } catch (e: Exception) {
                Logger.e(TAG, "Final send failed: ${e.message}")
                lastResponse = "Error: ${e.message}"
                onSubmissionResult?.invoke(false, lastResponse!!)
                false
            }
        }
        
        return false
    }

    fun isAnalyticsSubmitted(): Boolean = isWiseDriveSubmitted.get() && isClientSubmitted.get()
    fun isWiseDriveSubmitted(): Boolean = isWiseDriveSubmitted.get()
    fun isClientSubmitted(): Boolean = isClientSubmitted.get()
    fun getLastResponse(): String? = lastResponse
    fun getLastPayloadJson(): String? = lastPayloadJson
    fun getLastWiseDriveBlob(): EncryptedBlob? = lastWiseDriveBlob
    fun getLastClientBlob(): EncryptedBlob? = lastClientBlob

    fun cancel() {
        retryJob?.cancel()
        retryJob = null
    }
}
