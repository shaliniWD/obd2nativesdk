package com.wisedrive.obd2.constants

/**
 * DTC Descriptions - Main lookup class combining all DTC code databases
 * Total: 4000+ DTC codes across P, B, C, U categories
 */
object DTCDescriptions {

    /**
     * Get DTC info by code
     */
    fun getDTCInfo(code: String): DTCInfo? {
        val normalizedCode = code.uppercase().trim()
        
        return when {
            normalizedCode.startsWith("P0") -> {
                val digit = normalizedCode.getOrNull(2)?.toString() ?: ""
                when (digit) {
                    "0" -> DTCDescriptionsP0.P0_CODES[normalizedCode]
                    "1" -> DTCDescriptionsP0.P0_CODES[normalizedCode]
                    "2" -> DTCDescriptionsP1.P02_CODES[normalizedCode]
                    "3" -> DTCDescriptionsP1.P02_CODES[normalizedCode]
                    "4" -> DTCDescriptionsP2.P04_CODES[normalizedCode]
                    "5" -> DTCDescriptionsP2.P05_CODES[normalizedCode]
                    "6" -> DTCDescriptionsP2.P06_CODES[normalizedCode]
                    "7" -> DTCDescriptionsP2.P07_CODES[normalizedCode]
                    else -> generateGenericDescription(normalizedCode)
                }
            }
            normalizedCode.startsWith("P1") || normalizedCode.startsWith("P2") || normalizedCode.startsWith("P3") -> {
                // Manufacturer specific P codes - generate description
                generateManufacturerPCode(normalizedCode)
            }
            normalizedCode.startsWith("B") -> DTCDescriptionsBCU.B_CODES[normalizedCode] ?: generateGenericDescription(normalizedCode)
            normalizedCode.startsWith("C") -> DTCDescriptionsBCU.C_CODES[normalizedCode] ?: generateGenericDescription(normalizedCode)
            normalizedCode.startsWith("U") -> DTCDescriptionsBCU.U_CODES[normalizedCode] ?: generateGenericDescription(normalizedCode)
            else -> generateGenericDescription(normalizedCode)
        }
    }

    /**
     * Get description string for a DTC code
     */
    fun getDescription(code: String): String {
        return getDTCInfo(code)?.description ?: "Unknown diagnostic trouble code"
    }

    /**
     * Get severity for a DTC code
     */
    fun getSeverity(code: String): String {
        return getDTCInfo(code)?.severity ?: determineSeverityByCategory(code)
    }

    /**
     * Determine severity based on code category
     */
    private fun determineSeverityByCategory(code: String): String {
        val normalizedCode = code.uppercase()
        return when {
            // Critical patterns
            normalizedCode.startsWith("P03") -> "Critical"  // Misfires
            normalizedCode.startsWith("P07") -> "Critical"  // Transmission
            normalizedCode.contains("FUEL") -> "Important"
            normalizedCode.contains("MISFIRE") -> "Critical"
            
            // Category defaults
            normalizedCode.startsWith("P") -> "Important"
            normalizedCode.startsWith("B") -> "Non-Critical"
            normalizedCode.startsWith("C") -> "Critical"   // Chassis safety
            normalizedCode.startsWith("U") -> "Important"  // Communication
            else -> "Non-Critical"
        }
    }

    /**
     * Generate generic description for unknown codes
     */
    private fun generateGenericDescription(code: String): DTCInfo {
        val category = when (code.firstOrNull()) {
            'P' -> "Powertrain"
            'B' -> "Body"
            'C' -> "Chassis"
            'U' -> "Network/Communication"
            else -> "Unknown"
        }
        
        val severity = determineSeverityByCategory(code)
        return DTCInfo("$category System Malfunction - Code $code", severity)
    }

    /**
     * Generate description for manufacturer-specific P codes (P1xxx, P2xxx, P3xxx)
     */
    private fun generateManufacturerPCode(code: String): DTCInfo {
        val codeNumber = code.substring(1).toIntOrNull() ?: 0
        
        val description = when {
            codeNumber in 1000..1099 -> "Manufacturer Specific - Fuel Metering"
            codeNumber in 1100..1199 -> "Manufacturer Specific - Fuel System"
            codeNumber in 1200..1299 -> "Manufacturer Specific - Fuel/Air Control"
            codeNumber in 1300..1399 -> "Manufacturer Specific - Ignition System"
            codeNumber in 1400..1499 -> "Manufacturer Specific - Emissions Control"
            codeNumber in 1500..1599 -> "Manufacturer Specific - Idle/Speed Control"
            codeNumber in 1600..1699 -> "Manufacturer Specific - Computer/Output"
            codeNumber in 1700..1799 -> "Manufacturer Specific - Transmission"
            codeNumber in 2000..2999 -> "Manufacturer Specific - Fuel/Air Metering"
            codeNumber in 3000..3499 -> "Manufacturer Specific - Ignition/Misfire"
            else -> "Manufacturer Specific Powertrain Code"
        }
        
        val severity = when {
            codeNumber in 1300..1399 || codeNumber in 3000..3499 -> "Critical"
            codeNumber in 1700..1799 -> "Critical"
            else -> "Important"
        }
        
        return DTCInfo("$description ($code)", severity)
    }

    /**
     * Get category name from code
     */
    fun getCategoryName(code: String): String {
        return when (code.firstOrNull()?.uppercaseChar()) {
            'P' -> "Powertrain"
            'B' -> "Body"
            'C' -> "Chassis"
            'U' -> "Network/Communication"
            else -> "Unknown"
        }
    }

    /**
     * Check if code is manufacturer specific
     */
    fun isManufacturerSpecific(code: String): Boolean {
        val normalizedCode = code.uppercase()
        if (!normalizedCode.startsWith("P")) return false
        val digit = normalizedCode.getOrNull(1)?.digitToIntOrNull() ?: return false
        return digit in 1..3
    }
}
