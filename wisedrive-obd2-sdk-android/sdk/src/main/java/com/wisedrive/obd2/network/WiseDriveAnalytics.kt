package com.wisedrive.obd2.network

import com.google.gson.Gson
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
internal class WiseDriveAnalytics {
    companion object {
        private const val TAG = "WiseDriveAnalytics"
        private const val ANALYTICS_ENDPOINT = "http://164.52.213.170:82/apiv2/webhook/obdreport/wisedrive"
        private const val MAX_RETRIES = 10
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var retryJob: Job? = null
    private val isSubmitted = AtomicBoolean(false)
    private var pendingPayload: APIPayload? = null
    private var lastResponse: String? = null

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
        
        // Start background retry
        retryJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            var delayMs = INITIAL_RETRY_DELAY_MS
            
            while (!isSubmitted.get() && attempt < MAX_RETRIES) {
                try {
                    val success = sendToEndpoint(apiPayload)
                    if (success) {
                        Logger.i(TAG, "Analytics sent successfully on attempt ${attempt + 1}")
                        isSubmitted.set(true)
                        pendingPayload = null
                        return@launch
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Analytics send attempt ${attempt + 1} failed: ${e.message}")
                    lastResponse = "Error: ${e.message}"
                }
                
                attempt++
                if (!isSubmitted.get() && attempt < MAX_RETRIES) {
                    delay(delayMs)
                    delayMs = minOf(delayMs * 2, MAX_RETRY_DELAY_MS) // Exponential backoff
                }
            }
            
            if (!isSubmitted.get()) {
                Logger.w(TAG, "Analytics failed after $MAX_RETRIES attempts, will retry on submitReport()")
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
        
        // Final attempt with pending payload
        pendingPayload?.let { payload ->
            return try {
                val success = sendToEndpoint(payload)
                if (success) {
                    isSubmitted.set(true)
                    pendingPayload = null
                }
                success
            } catch (e: Exception) {
                Logger.e(TAG, "Final analytics send failed: ${e.message}")
                lastResponse = "Error: ${e.message}"
                false
            }
        }
        
        return false
    }

    /**
     * Send plain JSON payload to WiseDrive analytics endpoint
     */
    private fun sendToEndpoint(apiPayload: APIPayload): Boolean {
        val jsonBody = gson.toJson(apiPayload)
        
        Logger.d(TAG, "Sending analytics to: $ANALYTICS_ENDPOINT")
        Logger.d(TAG, "Payload: $jsonBody")
        
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
     * Cancel any pending operations
     */
    fun cancel() {
        retryJob?.cancel()
        retryJob = null
    }
}
