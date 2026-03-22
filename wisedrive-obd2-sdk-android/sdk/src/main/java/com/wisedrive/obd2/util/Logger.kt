package com.wisedrive.obd2.util

import android.util.Log

/**
 * Structured logging utility for SDK
 * Note: In release builds with ProGuard, these calls are removed
 */
object Logger {
    
    private const val SDK_TAG = "WiseDriveOBD2"
    private var loggingEnabled = false
    
    fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabled = enabled
    }
    
    fun v(tag: String, message: String) {
        if (loggingEnabled) {
            Log.v("$SDK_TAG:$tag", message)
        }
    }
    
    fun d(tag: String, message: String) {
        if (loggingEnabled) {
            Log.d("$SDK_TAG:$tag", message)
        }
    }
    
    fun i(tag: String, message: String) {
        if (loggingEnabled) {
            Log.i("$SDK_TAG:$tag", message)
        }
    }
    
    fun w(tag: String, message: String) {
        if (loggingEnabled) {
            Log.w("$SDK_TAG:$tag", message)
        }
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (loggingEnabled) {
            if (throwable != null) {
                Log.e("$SDK_TAG:$tag", message, throwable)
            } else {
                Log.e("$SDK_TAG:$tag", message)
            }
        }
    }
}
