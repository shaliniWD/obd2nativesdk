package com.wisedrive.obd2.constants

/**
 * OBD-II PID definitions with formulas
 */
object OBDPIDs {

    data class PIDDefinition(
        val pid: String,
        val name: String,
        val shortName: String,
        val unit: String,
        val minValue: Double,
        val maxValue: Double,
        val bytes: Int,
        val category: String,
        val formula: (List<Int>) -> Double
    )

    val SUPPORTED_PIDS = listOf(
        PIDDefinition("0C", "Engine RPM", "RPM", "RPM", 0.0, 16383.75, 2, "engine",
            formula = { bytes -> ((bytes[0] * 256) + bytes[1]) / 4.0 }),

        PIDDefinition("0D", "Vehicle Speed", "Speed", "km/h", 0.0, 255.0, 1, "speed",
            formula = { bytes -> bytes[0].toDouble() }),

        PIDDefinition("05", "Coolant Temperature", "Coolant", "C", -40.0, 215.0, 1, "temperature",
            formula = { bytes -> bytes[0] - 40.0 }),

        PIDDefinition("11", "Throttle Position", "Throttle", "%", 0.0, 100.0, 1, "engine",
            formula = { bytes -> (bytes[0] * 100.0) / 255.0 }),

        PIDDefinition("04", "Engine Load", "Load", "%", 0.0, 100.0, 1, "engine",
            formula = { bytes -> (bytes[0] * 100.0) / 255.0 }),

        PIDDefinition("0F", "Intake Air Temperature", "Intake Air", "C", -40.0, 215.0, 1, "temperature",
            formula = { bytes -> bytes[0] - 40.0 }),

        PIDDefinition("0B", "Intake Manifold Pressure", "MAP", "kPa", 0.0, 255.0, 1, "engine",
            formula = { bytes -> bytes[0].toDouble() }),

        PIDDefinition("2F", "Fuel Tank Level", "Fuel Level", "%", 0.0, 100.0, 1, "fuel",
            formula = { bytes -> (bytes[0] * 100.0) / 255.0 }),

        PIDDefinition("42", "Control Module Voltage", "Voltage", "V", 0.0, 65.535, 2, "electrical",
            formula = { bytes -> ((bytes[0] * 256) + bytes[1]) / 1000.0 }),

        PIDDefinition("46", "Ambient Air Temperature", "Ambient", "C", -40.0, 215.0, 1, "temperature",
            formula = { bytes -> bytes[0] - 40.0 }),

        PIDDefinition("10", "MAF Air Flow Rate", "MAF", "g/s", 0.0, 655.35, 2, "engine",
            formula = { bytes -> ((bytes[0] * 256) + bytes[1]) / 100.0 }),

        PIDDefinition("0E", "Timing Advance", "Timing", "deg", -64.0, 63.5, 1, "engine",
            formula = { bytes -> (bytes[0] / 2.0) - 64.0 }),

        PIDDefinition("0A", "Fuel Pressure", "Fuel Pressure", "kPa", 0.0, 765.0, 1, "fuel",
            formula = { bytes -> bytes[0] * 3.0 }),

        PIDDefinition("5C", "Engine Oil Temperature", "Oil Temp", "C", -40.0, 210.0, 1, "temperature",
            formula = { bytes -> bytes[0] - 40.0 }),

        PIDDefinition("5E", "Engine Fuel Rate", "Fuel Rate", "L/h", 0.0, 3276.75, 2, "fuel",
            formula = { bytes -> ((bytes[0] * 256) + bytes[1]) / 20.0 }),

        PIDDefinition("1F", "Run Time Since Start", "Run Time", "sec", 0.0, 65535.0, 2, "engine",
            formula = { bytes -> ((bytes[0] * 256) + bytes[1]).toDouble() }),

        PIDDefinition("21", "Distance with MIL On", "MIL Distance", "km", 0.0, 65535.0, 2, "emissions",
            formula = { bytes -> ((bytes[0] * 256) + bytes[1]).toDouble() }),

        PIDDefinition("06", "Short Term Fuel Trim (Bank 1)", "STFT B1", "%", -100.0, 99.2, 1, "fuel",
            formula = { bytes -> (bytes[0] / 1.28) - 100.0 }),

        PIDDefinition("07", "Long Term Fuel Trim (Bank 1)", "LTFT B1", "%", -100.0, 99.2, 1, "fuel",
            formula = { bytes -> (bytes[0] / 1.28) - 100.0 }),

        PIDDefinition("33", "Barometric Pressure", "Baro", "kPa", 0.0, 255.0, 1, "other",
            formula = { bytes -> bytes[0].toDouble() })
    )

    // Priority PIDs (scanned first, in this order)
    val PRIORITY_PIDS = listOf("0C", "0D", "05", "11", "04", "0F", "0B", "2F", "42", "46")

    fun getPIDDefinition(pid: String): PIDDefinition? {
        return SUPPORTED_PIDS.find { it.pid.equals(pid, ignoreCase = true) }
    }
}
