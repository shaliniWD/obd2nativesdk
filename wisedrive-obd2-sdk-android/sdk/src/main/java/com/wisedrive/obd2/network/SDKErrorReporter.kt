package com.wisedrive.obd2.network

import android.os.Build
import com.google.gson.Gson
import com.wisedrive.obd2.security.AdvancedEncryptionManager
import com.wisedrive.obd2.util.Logger
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SDK Error Reporter - Encrypted Error Logging to WiseDrive Internal API
 * 
 * Captures all SDK errors (network, Bluetooth, scan, API, exceptions)
 * and sends them ENCRYPTED to the WiseDrive internal API.
 * 
 * This helps WiseDrive debug SDK issues without reaching the client.
 * 
 * Error logs are:
 * - Encrypted with WiseDrive public key (same as scan data)
 * - Sent to the same internal API endpoint
 * - Queued and sent in batches with retry logic
 * - Non-blocking (errors are queued, not sent synchronously)
 */
object SDKErrorReporter {

    private const val TAG = "SDKErrorReporter"
    private const val ERROR_ENDPOINT = "https://faircar.in:9768/api/obd/encrypted"
    private const val AUTH_ENDPOINT = "https://faircar.in:9768/api/auth/login"
    private const val MAX_QUEUE_SIZE = 50
    private const val FLUSH_INTERVAL_MS = 30_000L
    private const val MAX_RETRIES = 3
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val gson = Gson()
    private val encryptionManager = AdvancedEncryptionManager()
    private val errorQueue = ConcurrentLinkedQueue<ErrorLogEntry>()
    private val isInitialized = AtomicBoolean(false)
    private var flushJob: Job? = null
    private var licensePlate: String = ""
    private var sdkVersion: String = "2.0.0"
    
    @Volatile
    private var authToken: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Initialize the error reporter
     */
    fun initialize(licensePlate: String = "", sdkVersion: String = "2.0.0") {
        if (isInitialized.getAndSet(true)) return
        
        this.licensePlate = licensePlate
        this.sdkVersion = sdkVersion
        encryptionManager.initialize()
        
        Logger.d(TAG, "Error reporter initialized")
    }

    /**
     * Report an error - non-blocking, queued for batch sending
     */
    fun reportError(
        errorType: ErrorType,
        message: String,
        exception: Throwable? = null,
        context: Map<String, String> = emptyMap()
    ) {
        if (!isInitialized.get()) return
        
        val entry = ErrorLogEntry(
            errorType = errorType.name,
            message = message,
            stackTrace = exception?.let { getStackTrace(it) },
            timestamp = System.currentTimeMillis(),
            sdkVersion = sdkVersion,
            licensePlate = licensePlate,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            context = context
        )
        
        // Limit queue size
        if (errorQueue.size >= MAX_QUEUE_SIZE) {
            errorQueue.poll() // Remove oldest
        }
        errorQueue.add(entry)
        
        Logger.d(TAG, "Error queued: ${errorType.name} - $message")
    }

    /**
     * Report a network error
     */
    fun reportNetworkError(endpoint: String, statusCode: Int, responseBody: String, exception: Throwable? = null) {
        reportError(
            ErrorType.NETWORK_ERROR,
            "HTTP $statusCode from $endpoint",
            exception,
            mapOf("endpoint" to endpoint, "statusCode" to statusCode.toString(), "response" to responseBody.take(500))
        )
    }

    /**
     * Report a Bluetooth error
     */
    fun reportBluetoothError(deviceId: String, phase: String, exception: Throwable? = null) {
        reportError(
            ErrorType.BLUETOOTH_ERROR,
            "Bluetooth $phase failed for $deviceId",
            exception,
            mapOf("deviceId" to deviceId, "phase" to phase)
        )
    }

    /**
     * Report a scan error
     */
    fun reportScanError(scanPhase: String, command: String = "", exception: Throwable? = null) {
        reportError(
            ErrorType.SCAN_ERROR,
            "Scan failed at $scanPhase",
            exception,
            mapOf("phase" to scanPhase, "command" to command)
        )
    }

    /**
     * Report an encryption error
     */
    fun reportEncryptionError(operation: String, exception: Throwable? = null) {
        reportError(
            ErrorType.ENCRYPTION_ERROR,
            "Encryption $operation failed",
            exception,
            mapOf("operation" to operation)
        )
    }

    /**
     * Report an API submission error
     */
    fun reportSubmissionError(endpoint: String, attempt: Int, exception: Throwable? = null) {
        reportError(
            ErrorType.SUBMISSION_ERROR,
            "Submission to $endpoint failed (attempt $attempt)",
            exception,
            mapOf("endpoint" to endpoint, "attempt" to attempt.toString())
        )
    }

    /**
     * Report a general SDK exception
     */
    fun reportException(source: String, exception: Throwable) {
        reportError(
            ErrorType.SDK_EXCEPTION,
            "Exception in $source: ${exception.message}",
            exception,
            mapOf("source" to source)
        )
    }

    /**
     * Flush all queued errors - sends them encrypted to internal API
     * Call this periodically or at the end of a scan session
     */
    fun flush(scope: CoroutineScope) {
        if (errorQueue.isEmpty()) return
        
        scope.launch(Dispatchers.IO) {
            sendQueuedErrors()
        }
    }

    /**
     * Start periodic flushing of error queue
     */
    fun startPeriodicFlush(scope: CoroutineScope) {
        flushJob?.cancel()
        flushJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                if (errorQueue.isNotEmpty()) {
                    sendQueuedErrors()
                }
            }
        }
    }

    /**
     * Stop periodic flushing
     */
    fun stopPeriodicFlush() {
        flushJob?.cancel()
        flushJob = null
    }

    /**
     * Update license plate (called when scan starts)
     */
    fun setLicensePlate(plate: String) {
        this.licensePlate = plate
    }

    private fun sendQueuedErrors() {
        val errors = mutableListOf<ErrorLogEntry>()
        while (errorQueue.isNotEmpty() && errors.size < 20) {
            errorQueue.poll()?.let { errors.add(it) }
        }
        
        if (errors.isEmpty()) return
        
        val errorPayload = mapOf(
            "type" to "SDK_ERROR_LOG",
            "sdkVersion" to sdkVersion,
            "licensePlate" to licensePlate,
            "errorCount" to errors.size,
            "errors" to errors,
            "reportTimestamp" to System.currentTimeMillis()
        )
        
        val jsonPlaintext = gson.toJson(errorPayload)
        
        try {
            // Encrypt with WiseDrive key
            val encryptedBlob = encryptionManager.encryptForWiseDrive(jsonPlaintext)
            
            val encryptedRequest: Map<String, Any> = mapOf(
                "version" to encryptedBlob.version,
                "keyId" to encryptedBlob.keyId,
                "timestamp" to encryptedBlob.timestamp,
                "encryptedData" to encryptedBlob.payload
            )
            
            // Authenticate if needed
            if (authToken == null) {
                authenticate()
            }
            
            val url = if (licensePlate.isNotBlank()) {
                "$ERROR_ENDPOINT?license_plate=${java.net.URLEncoder.encode(licensePlate, "UTF-8")}"
            } else {
                ERROR_ENDPOINT
            }
            
            for (attempt in 1..MAX_RETRIES) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer ${authToken ?: ""}")
                        .header("X-Log-Type", "SDK_ERROR")
                        .post(gson.toJson(encryptedRequest).toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                    
                    val response = httpClient.newCall(request).execute()
                    
                    if (response.code == 401 || response.code == 403) {
                        authToken = null
                        authenticate()
                        continue
                    }
                    
                    if (response.isSuccessful) {
                        Logger.d(TAG, "Sent ${errors.size} error logs successfully")
                        return
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Error log send attempt $attempt failed: ${e.message}")
                }
            }
            
            // Re-queue errors on failure
            errors.forEach { errorQueue.add(it) }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to encrypt/send error logs: ${e.message}")
        }
    }

    private fun authenticate() {
        try {
            val authBody = gson.toJson(mapOf(
                "username" to "partner_api",
                "password" to "Partner@2025!"
            ))
            
            val request = Request.Builder()
                .url(AUTH_ENDPOINT)
                .header("Content-Type", "application/json")
                .post(authBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                @Suppress("UNCHECKED_CAST")
                val authResponse: Map<String, Any> = gson.fromJson(body, Map::class.java) as Map<String, Any>
                authToken = authResponse["token"]?.toString()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error reporter auth failed: ${e.message}")
        }
    }

    private fun getStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString().take(2000) // Limit stack trace size
    }

    /**
     * Error types for categorization
     */
    enum class ErrorType {
        NETWORK_ERROR,
        BLUETOOTH_ERROR,
        SCAN_ERROR,
        ENCRYPTION_ERROR,
        SUBMISSION_ERROR,
        SDK_EXCEPTION,
        TIMEOUT_ERROR,
        PROTOCOL_ERROR
    }

    /**
     * Error log entry data class
     */
    data class ErrorLogEntry(
        val errorType: String,
        val message: String,
        val stackTrace: String?,
        val timestamp: Long,
        val sdkVersion: String,
        val licensePlate: String,
        val deviceModel: String,
        val androidVersion: String,
        val context: Map<String, String>
    )
}
