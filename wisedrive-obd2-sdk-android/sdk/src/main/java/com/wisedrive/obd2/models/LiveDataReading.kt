package com.wisedrive.obd2.models

/**
 * Live data reading from OBD-II PIDs
 */
data class LiveDataReading(
    val pid: String,                       // e.g., "0C"
    val name: String,                      // e.g., "Engine RPM"
    val shortName: String,                 // e.g., "RPM"
    val value: Double,
    val displayValue: String,              // e.g., "2450 RPM"
    val unit: String,                      // e.g., "RPM", "°C", "km/h"
    val category: String,                  // "engine", "fuel", "temperature", etc.
    val timestamp: Long,
    val rawHex: String
)

/**
 * Live data scan result
 */
data class LiveDataScanResult(
    val response: String,
    val rawResponse: String,
    val durationMs: Long
)
