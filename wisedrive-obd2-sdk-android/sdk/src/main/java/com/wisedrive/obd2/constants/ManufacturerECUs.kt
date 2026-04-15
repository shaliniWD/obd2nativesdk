package com.wisedrive.obd2.constants

import com.wisedrive.obd2.models.ECUModule
import com.wisedrive.obd2.models.ManufacturerConfig
import com.wisedrive.obd2.security.ObfuscatedECUConfig

/**
 * Manufacturer ECU configurations - 20+ manufacturers with TX/RX CAN addresses
 * 
 * NOTE: All ECU data is stored in obfuscated form via ObfuscatedECUConfig.
 * This class is a public facade that delegates to the protected store.
 */
object ManufacturerECUs {

    val MANUFACTURER_CONFIGS: List<ManufacturerConfig>
        get() = ObfuscatedECUConfig.getAllConfigs()

    /**
     * Look up manufacturer config by ID
     */
    fun getManufacturerConfig(manufacturerId: String?): ManufacturerConfig? {
        return ObfuscatedECUConfig.findConfig(manufacturerId)
    }

    /**
     * Common module addresses for fallback when manufacturer unknown
     */
    val COMMON_MODULES: List<ECUModule>
        get() = ObfuscatedECUConfig.getFallbackModules()
}
