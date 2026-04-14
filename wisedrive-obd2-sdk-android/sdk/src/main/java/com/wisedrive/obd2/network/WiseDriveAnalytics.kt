package com.wisedrive.obd2.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wisedrive.obd2.models.APIPayload
import com.wisedrive.obd2.util.Logger
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WiseDrive Analytics - Internal analytics endpoint handler
 * 
 * Sends scan data to WiseDrive analytics endpoint (plain JSON).
 * Implements silent retry mechanism until successful or submitReport() is called.
 */
internal class WiseDriveAnalytics(
    private val useMock: Boolean = false
) {
    companion object {
        private const val TAG = "WiseDriveAnalytics"
        private const val ANALYTICS_ENDPOINT = "http://faircar.in:82/apiv2/webhook/obdreport/wisedrive"
        private const val MAX_RETRIES = 10
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val gsonCompact: Gson = Gson()
    
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
    
    // Callbacks for logging/UI updates
    private var onPayloadPrepared: ((String) -> Unit)? = null
    private var onSubmissionResult: ((Boolean, String) -> Unit)? = null

    /**
     * Set callback for when payload is prepared (for logging)
     */
    fun setOnPayloadPrepared(callback: (String) -> Unit) {
        onPayloadPrepared = callback
    }

    /**
     * Set callback for submission result
     */
    fun setOnSubmissionResult(callback: (Boolean, String) -> Unit) {
        onSubmissionResult = callback
    }

    /**
     * Send analytics data to WiseDrive endpoint (plain JSON)
     * Runs in background with silent retry until success or submitReport() called
     * 
     * @param apiPayload The scan data payload
     * @param scope CoroutineScope for background retry
     */
    fun sendAnalytics(apiPayload: APIPayload, scope: CoroutineScope) {
        isSubmitted.set(false)
        pendingPayload = apiPayload
        lastResponse = null
        
        // Generate pretty JSON for logging
        lastPayloadJson = gson.toJson(apiPayload)
        
        Logger.i(TAG, "=== Analytics Payload Prepared ===")
        Logger.i(TAG, lastPayloadJson ?: "null")
        Logger.i(TAG, "=================================")
        
        // Notify callback
        onPayloadPrepared?.invoke(lastPayloadJson ?: "")
        
        // If mock mode, simulate success
        if (useMock) {
            Logger.i(TAG, "[MOCK MODE] Simulating successful submission")
            scope.launch(Dispatchers.IO) {
                delay(500) // Simulate network delay
                isSubmitted.set(true)
                lastResponse = """{"result": "SUCCESS", "mock": true}"""
                pendingPayload = null
                onSubmissionResult?.invoke(true, lastResponse!!)
                Logger.i(TAG, "[MOCK MODE] Analytics submission simulated: $lastResponse")
            }
            return
        }
        
        // Start background retry for real endpoint
        retryJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            var delayMs = INITIAL_RETRY_DELAY_MS
            
            while (!isSubmitted.get() && attempt < MAX_RETRIES) {
                try {
                    Logger.d(TAG, "Sending analytics attempt ${attempt + 1}/$MAX_RETRIES")
                    val success = sendToEndpoint(apiPayload)
                    if (success) {
                        Logger.i(TAG, "Analytics sent successfully on attempt ${attempt + 1}")
                        isSubmitted.set(true)
                        pendingPayload = null
                        onSubmissionResult?.invoke(true, lastResponse ?: "SUCCESS")
                        return@launch
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Analytics send attempt ${attempt + 1} failed: ${e.message}")
                    lastResponse = "Error: ${e.message}"
                }
                
                attempt++
                if (!isSubmitted.get() && attempt < MAX_RETRIES) {
                    Logger.d(TAG, "Retrying in ${delayMs}ms...")
                    delay(delayMs)
                    delayMs = minOf(delayMs * 2, MAX_RETRY_DELAY_MS) // Exponential backoff
                }
            }
            
            if (!isSubmitted.get()) {
                Logger.w(TAG, "Analytics failed after $MAX_RETRIES attempts")
                onSubmissionResult?.invoke(false, lastResponse ?: "Max retries exceeded")
            }
        }
    }

    /**
     * Called when client calls submitReport() - final attempt if not already sent
     */
    suspend fun onClientSubmit(): Boolean {
        // Cancel ongoing retry
        retryJob?.cancel()
        
        // If already submitted, return success
        if (isSubmitted.get()) {
            return true
        }
        
        // If mock mode, just return success
        if (useMock) {
            isSubmitted.set(true)
            lastResponse = """{"result": "SUCCESS", "mock": true}"""
            return true
        }
        
        // Final attempt with pending payload
        pendingPayload?.let { payload ->
            return try {
                val success = sendToEndpoint(payload)
                if (success) {
                    isSubmitted.set(true)
                    pendingPayload = null
                    onSubmissionResult?.invoke(true, lastResponse ?: "SUCCESS")
                }
                success
            } catch (e: Exception) {
                Logger.e(TAG, "Final analytics send failed: ${e.message}")
                lastResponse = "Error: ${e.message}"
                onSubmissionResult?.invoke(false, lastResponse!!)
                false
            }
        }
        
        return false
    }

    /**
     * Send plain JSON payload to WiseDrive analytics endpoint
     */
    private fun sendToEndpoint(apiPayload: APIPayload): Boolean {
        val jsonBody = gsonCompact.toJson(apiPayload)
        
        Logger.d(TAG, "Sending to: $ANALYTICS_ENDPOINT")
        
        val request = Request.Builder()
            .url(ANALYTICS_ENDPOINT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic cHJhc2FkOnByYXNhZEAxMjM=")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        lastResponse = responseBody
        
        return response.isSuccessful.also { success ->
            if (success) {
                Logger.i(TAG, "Analytics endpoint returned success: $responseBody")
            } else {
                Logger.w(TAG, "Analytics endpoint returned ${response.code}: $responseBody")
            }
        }
    }

    /**
     * Check if analytics has been successfully submitted
     */
    fun isAnalyticsSubmitted(): Boolean = isSubmitted.get()

    /**
     * Get the last response from the endpoint
     */
    fun getLastResponse(): String? = lastResponse

    /**
     * Get the last payload JSON (pretty printed)
     */
    fun getLastPayloadJson(): String? = lastPayloadJson

    /**
     * Cancel any pending operations
     */
    fun cancel() {
        retryJob?.cancel()
        retryJob = null
    }
}
