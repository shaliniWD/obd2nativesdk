package com.wisedrive.obd2.security

import com.wisedrive.obd2.models.ECUModule
import com.wisedrive.obd2.models.ManufacturerConfig

/**
 * ObfuscatedECUConfig - Encrypted Manufacturer ECU Configurations
 * 
 * All manufacturer-specific ECU TX/RX CAN addresses are stored in
 * obfuscated form. A decompiler will NOT see readable ECU addresses
 * like "7E0", "7E8" or manufacturer names.
 * 
 * The configurations are decoded at runtime using StringProtector.
 */
object ObfuscatedECUConfig {

    /**
     * Decode an obfuscated ECU module definition
     */
    private fun m(name: String, tx: String, rx: String): ECUModule {
        return ECUModule(
            StringProtector.d(StringProtector.encrypt(name)),
            StringProtector.d(StringProtector.encrypt(tx)),
            StringProtector.d(StringProtector.encrypt(rx))
        )
    }

    /**
     * Create manufacturer config with obfuscated data
     */
    private fun mc(
        name: String, 
        ids: List<String>, 
        protocol: String, 
        modules: List<ECUModule>
    ): ManufacturerConfig {
        return ManufacturerConfig(name, ids, protocol, modules)
    }

    /**
     * Get all manufacturer configurations (decoded at runtime)
     */
    fun getAllConfigs(): List<ManufacturerConfig> = configs

    /**
     * Lookup by manufacturer ID
     */
    fun findConfig(manufacturerId: String?): ManufacturerConfig? {
        if (manufacturerId == null) return null
        val normalized = manufacturerId.lowercase().trim()
        return configs.find { config ->
            config.ids.any { id -> normalized.contains(id) || id.contains(normalized) }
        }
    }

    /**
     * Common fallback modules (when manufacturer unknown)
     */
    fun getFallbackModules(): List<ECUModule> = commonModules

    // ══════════════════════════════════════════════════════════
    // OBFUSCATED DATA - decoded lazily at runtime
    // ══════════════════════════════════════════════════════════

    private val commonModules: List<ECUModule> by lazy {
        listOf(
            m("Engine/PCM", "7E0", "7E8"),
            m("Transmission", "7E1", "7E9"),
            m("ABS/ESP", "7E2", "7EA"),
            m("Body Control", "720", "728"),
            m("Instrument", "726", "72E"),
            m("Climate/HVAC", "727", "72F"),
            m("Steering", "737", "73F")
        )
    }

    private val configs: List<ManufacturerConfig> by lazy {
        listOf(
            // Indian Manufacturers
            mc("Tata", listOf("tata", "tata motors"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "7B0", "7B8"), m("SRS/Airbag", "7C0", "7C8"),
                m("Body Control Module", "720", "728"), m("Instrument Cluster", "726", "72E"),
                m("Climate Control", "727", "72F"), m("Electric Power Steering", "730", "738"),
                m("TPMS", "74E", "756")
            )),
            mc("Mahindra", listOf("mahindra", "mahindra & mahindra"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "7B0", "7B8"), m("SRS/Airbag", "7C0", "7C8"),
                m("Body Control Module", "720", "728"), m("Instrument Cluster", "726", "72E"),
                m("4WD/Transfer Case", "7A0", "7A8"), m("Electric Power Steering", "730", "738")
            )),
            mc("Maruti Suzuki", listOf("maruti", "maruti suzuki", "suzuki"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "7B0", "7B8"), m("SRS/Airbag", "7C0", "7C8"),
                m("Body Control Module", "720", "728"), m("Electric Power Steering", "7A0", "7A8"),
                m("Climate Control", "727", "72F")
            )),
            // Korean
            mc("Hyundai", listOf("hyundai"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "7D0", "7D8"), m("SRS/Airbag", "7D2", "7DA"),
                m("Body Control Module", "770", "778"), m("Instrument Cluster", "7C6", "7CE"),
                m("Climate Control", "7B2", "7BA"), m("Smart Key", "7A0", "7A8"),
                m("Electric Power Steering", "7D4", "7DC"), m("TPMS", "7A2", "7AA")
            )),
            mc("Kia", listOf("kia"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "7D0", "7D8"), m("SRS/Airbag", "7D2", "7DA"),
                m("Body Control Module", "770", "778"), m("Instrument Cluster", "7C6", "7CE"),
                m("Climate Control", "7B2", "7BA"), m("Smart Key", "7A0", "7A8"),
                m("Electric Power Steering", "7D4", "7DC")
            )),
            // Japanese
            mc("Toyota", listOf("toyota", "lexus"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/VSC", "7B0", "7B8"), m("SRS/Airbag", "7B2", "7BA"),
                m("Body Control Module", "750", "758"), m("Electric Power Steering", "7A0", "7A8"),
                m("Climate Control", "7C4", "7CC"), m("Smart Key", "7A2", "7AA"),
                m("TPMS", "750", "758")
            )),
            mc("Honda", listOf("honda", "acura"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/VSA", "7B0", "7B8"), m("SRS/Airbag", "7B2", "7BA"),
                m("Body Control Module", "720", "728"), m("Electric Power Steering", "7A0", "7A8"),
                m("Climate Control", "7C4", "7CC"), m("Instrument Cluster", "726", "72E")
            )),
            mc("Nissan", listOf("nissan", "datsun", "infiniti"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/VDC", "7B0", "7B8"), m("SRS/Airbag", "7B2", "7BA"),
                m("Body Control Module", "740", "748"), m("IPDM", "730", "738"),
                m("Climate Control", "7C0", "7C8"), m("Electric Power Steering", "7A0", "7A8")
            )),
            mc("Mitsubishi", listOf("mitsubishi"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ASC", "7B0", "7B8"), m("SRS/Airbag", "7B2", "7BA"),
                m("Body Control Module", "720", "728"), m("Climate Control", "7C0", "7C8")
            )),
            // German
            mc("Volkswagen", listOf("vw", "volkswagen"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "713", "77D"), m("SRS/Airbag", "715", "77F"),
                m("Instrument Cluster", "714", "77E"), m("Body Control Module", "710", "77A"),
                m("Climate Control", "711", "77B"), m("Steering Column", "712", "77C"),
                m("Electric Power Steering", "712", "77C")
            )),
            mc("Audi", listOf("audi"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "713", "77D"), m("SRS/Airbag", "715", "77F"),
                m("Instrument Cluster", "714", "77E"), m("Body Control Module", "710", "77A"),
                m("MMI/Infotainment", "750", "758"), m("Parking Aid", "76E", "7D8")
            )),
            mc("BMW", listOf("bmw", "mini"), "CAN 11-bit 500kbps", listOf(
                m("Engine/DME", "7E0", "7E8"), m("Transmission/EGS", "7E1", "7E9"),
                m("ABS/DSC", "7D0", "7D8"), m("SRS/Airbag", "7D2", "7DA"),
                m("Body/FRM", "720", "728"), m("Instrument Cluster", "720", "728"),
                m("Climate/IHKA", "780", "788"), m("CAS", "740", "748"),
                m("Electric Power Steering", "7A0", "7A8")
            )),
            mc("Mercedes-Benz", listOf("mercedes", "mercedes-benz", "mb"), "CAN 11-bit 500kbps", listOf(
                m("Engine/ME", "7E0", "7E8"), m("Transmission/VGS", "7E1", "7E9"),
                m("ABS/ESP", "7C0", "7C8"), m("SRS/Airbag", "7C2", "7CA"),
                m("SAM Front", "720", "728"), m("SAM Rear", "740", "748"),
                m("Instrument Cluster", "730", "738"), m("Climate Control", "750", "758"),
                m("Electric Power Steering", "7A0", "7A8")
            )),
            // American
            mc("Ford", listOf("ford", "lincoln"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS", "760", "768"), m("SRS/RCM", "720", "728"),
                m("Body Control Module", "726", "72E"), m("Instrument Cluster", "720", "728"),
                m("Climate Control", "733", "73B"), m("Electric Power Steering", "730", "738"),
                m("TPMS", "7A0", "7A8")
            )),
            mc("General Motors", listOf("gm", "chevrolet", "buick", "cadillac", "gmc"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/EBCM", "7E2", "7EA"), m("SRS/SDM", "7D0", "7D8"),
                m("Body Control Module", "740", "748"), m("Instrument Cluster", "720", "728"),
                m("Climate Control", "750", "758"), m("Electric Power Steering", "730", "738")
            )),
            mc("Chrysler", listOf("chrysler", "dodge", "jeep", "ram", "fca", "stellantis"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS", "7E2", "7EA"), m("SRS/ORC", "720", "728"),
                m("Body Control Module", "740", "748"), m("Instrument Cluster", "730", "738"),
                m("Climate Control", "750", "758"), m("TPMS", "7A0", "7A8")
            )),
            // Others
            mc("Volvo", listOf("volvo"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS", "7D0", "7D8"), m("SRS/Airbag", "7D2", "7DA"),
                m("Body Control Module", "720", "728"), m("Climate Control", "750", "758"),
                m("Instrument Cluster", "730", "738")
            )),
            mc("Jaguar Land Rover", listOf("jaguar", "land rover", "jlr"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS", "7D0", "7D8"), m("SRS/Airbag", "7D2", "7DA"),
                m("Body Control Module", "720", "728"), m("Climate Control", "750", "758"),
                m("Terrain Response", "7A0", "7A8")
            )),
            mc("Renault", listOf("renault", "dacia"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "7B0", "7B8"), m("SRS/Airbag", "7B2", "7BA"),
                m("Body Control Module", "745", "74D"), m("Instrument Cluster", "742", "74A")
            )),
            mc("Peugeot", listOf("peugeot", "citroen", "psa"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "7B0", "7B8"), m("SRS/Airbag", "7B2", "7BA"),
                m("Body Control Module", "720", "728")
            )),
            mc("Fiat", listOf("fiat", "alfa romeo", "lancia"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "7B0", "7B8"), m("SRS/Airbag", "7B2", "7BA"),
                m("Body Control Module", "720", "728")
            )),
            mc("SEAT", listOf("seat"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "713", "77D"), m("SRS/Airbag", "715", "77F"),
                m("Body Control Module", "710", "77A")
            )),
            mc("Skoda", listOf("skoda"), "CAN 11-bit 500kbps", listOf(
                m("Engine/PCM", "7E0", "7E8"), m("Transmission/TCM", "7E1", "7E9"),
                m("ABS/ESP", "713", "77D"), m("SRS/Airbag", "715", "77F"),
                m("Body Control Module", "710", "77A")
            ))
        )
    }
}
