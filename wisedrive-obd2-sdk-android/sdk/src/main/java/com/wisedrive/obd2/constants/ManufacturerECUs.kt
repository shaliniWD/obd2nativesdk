package com.wisedrive.obd2.constants

import com.wisedrive.obd2.models.ECUModule
import com.wisedrive.obd2.models.ManufacturerConfig

/**
 * Manufacturer ECU configurations - 20+ manufacturers with TX/RX CAN addresses
 */
object ManufacturerECUs {

    val MANUFACTURER_CONFIGS = listOf(
        // ─── INDIAN ─────────────────────────────────────────
        ManufacturerConfig("Tata", listOf("tata", "tata motors"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "7B0", "7B8"),
            ECUModule("SRS/Airbag", "7C0", "7C8"),
            ECUModule("Body Control Module", "720", "728"),
            ECUModule("Instrument Cluster", "726", "72E"),
            ECUModule("Climate Control", "727", "72F"),
            ECUModule("Electric Power Steering", "730", "738"),
            ECUModule("TPMS", "74E", "756")
        )),

        ManufacturerConfig("Mahindra", listOf("mahindra", "mahindra & mahindra"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "7B0", "7B8"),
            ECUModule("SRS/Airbag", "7C0", "7C8"),
            ECUModule("Body Control Module", "720", "728"),
            ECUModule("Instrument Cluster", "726", "72E"),
            ECUModule("4WD/Transfer Case", "7A0", "7A8"),
            ECUModule("Electric Power Steering", "730", "738")
        )),

        ManufacturerConfig("Maruti Suzuki", listOf("maruti", "maruti suzuki", "suzuki"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "7B0", "7B8"),
            ECUModule("SRS/Airbag", "7C0", "7C8"),
            ECUModule("Body Control Module", "720", "728"),
            ECUModule("Electric Power Steering", "7A0", "7A8"),
            ECUModule("Climate Control", "727", "72F")
        )),

        // ─── KOREAN ─────────────────────────────────────────
        ManufacturerConfig("Hyundai", listOf("hyundai"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "7D0", "7D8"),
            ECUModule("SRS/Airbag", "7D2", "7DA"),
            ECUModule("Body Control Module", "770", "778"),
            ECUModule("Instrument Cluster", "7C6", "7CE"),
            ECUModule("Climate Control", "7B2", "7BA"),
            ECUModule("Smart Key", "7A0", "7A8"),
            ECUModule("Electric Power Steering", "7D4", "7DC"),
            ECUModule("TPMS", "7A2", "7AA")
        )),

        ManufacturerConfig("Kia", listOf("kia"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "7D0", "7D8"),
            ECUModule("SRS/Airbag", "7D2", "7DA"),
            ECUModule("Body Control Module", "770", "778"),
            ECUModule("Instrument Cluster", "7C6", "7CE"),
            ECUModule("Climate Control", "7B2", "7BA"),
            ECUModule("Smart Key", "7A0", "7A8"),
            ECUModule("Electric Power Steering", "7D4", "7DC")
        )),

        // ─── JAPANESE ───────────────────────────────────────
        ManufacturerConfig("Toyota", listOf("toyota", "lexus"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/VSC", "7B0", "7B8"),
            ECUModule("SRS/Airbag", "7B2", "7BA"),
            ECUModule("Body Control Module", "750", "758"),
            ECUModule("Electric Power Steering", "7A0", "7A8"),
            ECUModule("Climate Control", "7C4", "7CC"),
            ECUModule("Smart Key", "7A2", "7AA"),
            ECUModule("TPMS", "750", "758")
        )),

        ManufacturerConfig("Honda", listOf("honda", "acura"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/VSA", "7B0", "7B8"),
            ECUModule("SRS/Airbag", "7B2", "7BA"),
            ECUModule("Body Control Module", "720", "728"),
            ECUModule("Electric Power Steering", "7A0", "7A8"),
            ECUModule("Climate Control", "7C4", "7CC"),
            ECUModule("Instrument Cluster", "726", "72E")
        )),

        ManufacturerConfig("Nissan", listOf("nissan", "datsun", "infiniti"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/VDC", "7B0", "7B8"),
            ECUModule("SRS/Airbag", "7B2", "7BA"),
            ECUModule("Body Control Module", "740", "748"),
            ECUModule("IPDM", "730", "738"),
            ECUModule("Climate Control", "7C0", "7C8"),
            ECUModule("Electric Power Steering", "7A0", "7A8")
        )),

        ManufacturerConfig("Mitsubishi", listOf("mitsubishi"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ASC", "7B0", "7B8"),
            ECUModule("SRS/Airbag", "7B2", "7BA"),
            ECUModule("Body Control Module", "720", "728"),
            ECUModule("Climate Control", "7C0", "7C8")
        )),

        // ─── GERMAN ─────────────────────────────────────────
        ManufacturerConfig("Volkswagen", listOf("vw", "volkswagen"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "713", "77D"),
            ECUModule("SRS/Airbag", "715", "77F"),
            ECUModule("Instrument Cluster", "714", "77E"),
            ECUModule("Body Control Module", "710", "77A"),
            ECUModule("Climate Control", "711", "77B"),
            ECUModule("Steering Column", "712", "77C"),
            ECUModule("Electric Power Steering", "712", "77C")
        )),

        ManufacturerConfig("Audi", listOf("audi"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "713", "77D"),
            ECUModule("SRS/Airbag", "715", "77F"),
            ECUModule("Instrument Cluster", "714", "77E"),
            ECUModule("Body Control Module", "710", "77A"),
            ECUModule("MMI/Infotainment", "750", "758"),
            ECUModule("Parking Aid", "76E", "7D8")
        )),

        ManufacturerConfig("BMW", listOf("bmw", "mini"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/DME", "7E0", "7E8"),
            ECUModule("Transmission/EGS", "7E1", "7E9"),
            ECUModule("ABS/DSC", "7D0", "7D8"),
            ECUModule("SRS/Airbag", "7D2", "7DA"),
            ECUModule("Body/FRM", "720", "728"),
            ECUModule("Instrument Cluster", "720", "728"),
            ECUModule("Climate/IHKA", "780", "788"),
            ECUModule("CAS", "740", "748"),
            ECUModule("Electric Power Steering", "7A0", "7A8")
        )),

        ManufacturerConfig("Mercedes-Benz", listOf("mercedes", "mercedes-benz", "mb"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/ME", "7E0", "7E8"),
            ECUModule("Transmission/VGS", "7E1", "7E9"),
            ECUModule("ABS/ESP", "7C0", "7C8"),
            ECUModule("SRS/Airbag", "7C2", "7CA"),
            ECUModule("SAM Front", "720", "728"),
            ECUModule("SAM Rear", "740", "748"),
            ECUModule("Instrument Cluster", "730", "738"),
            ECUModule("Climate Control", "750", "758"),
            ECUModule("Electric Power Steering", "7A0", "7A8")
        )),

        // ─── AMERICAN ───────────────────────────────────────
        ManufacturerConfig("Ford", listOf("ford", "lincoln"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS", "760", "768"),
            ECUModule("SRS/RCM", "720", "728"),
            ECUModule("Body Control Module", "726", "72E"),
            ECUModule("Instrument Cluster", "720", "728"),
            ECUModule("Climate Control", "733", "73B"),
            ECUModule("Electric Power Steering", "730", "738"),
            ECUModule("TPMS", "7A0", "7A8")
        )),

        ManufacturerConfig("General Motors", listOf("gm", "chevrolet", "buick", "cadillac", "gmc"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/EBCM", "7E2", "7EA"),
            ECUModule("SRS/SDM", "7D0", "7D8"),
            ECUModule("Body Control Module", "740", "748"),
            ECUModule("Instrument Cluster", "720", "728"),
            ECUModule("Climate Control", "750", "758"),
            ECUModule("Electric Power Steering", "730", "738")
        )),

        ManufacturerConfig("Chrysler", listOf("chrysler", "dodge", "jeep", "ram", "fca", "stellantis"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS", "7E2", "7EA"),
            ECUModule("SRS/ORC", "720", "728"),
            ECUModule("Body Control Module", "740", "748"),
            ECUModule("Instrument Cluster", "730", "738"),
            ECUModule("Climate Control", "750", "758"),
            ECUModule("TPMS", "7A0", "7A8")
        )),

        // ─── OTHERS ─────────────────────────────────────────
        ManufacturerConfig("Volvo", listOf("volvo"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS", "7D0", "7D8"),
            ECUModule("SRS/Airbag", "7D2", "7DA"),
            ECUModule("Body Control Module", "720", "728"),
            ECUModule("Climate Control", "750", "758"),
            ECUModule("Instrument Cluster", "730", "738")
        )),

        ManufacturerConfig("Jaguar Land Rover", listOf("jaguar", "land rover", "jlr"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS", "7D0", "7D8"),
            ECUModule("SRS/Airbag", "7D2", "7DA"),
            ECUModule("Body Control Module", "720", "728"),
            ECUModule("Climate Control", "750", "758"),
            ECUModule("Terrain Response", "7A0", "7A8")
        )),

        ManufacturerConfig("Renault", listOf("renault", "dacia"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "7B0", "7B8"),
            ECUModule("SRS/Airbag", "7B2", "7BA"),
            ECUModule("Body Control Module", "745", "74D"),
            ECUModule("Instrument Cluster", "742", "74A")
        )),

        ManufacturerConfig("Peugeot", listOf("peugeot", "citroen", "psa"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "7B0", "7B8"),
            ECUModule("SRS/Airbag", "7B2", "7BA"),
            ECUModule("Body Control Module", "720", "728")
        )),

        ManufacturerConfig("Fiat", listOf("fiat", "alfa romeo", "lancia"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "7B0", "7B8"),
            ECUModule("SRS/Airbag", "7B2", "7BA"),
            ECUModule("Body Control Module", "720", "728")
        )),

        ManufacturerConfig("SEAT", listOf("seat"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "713", "77D"),
            ECUModule("SRS/Airbag", "715", "77F"),
            ECUModule("Body Control Module", "710", "77A")
        )),

        ManufacturerConfig("Skoda", listOf("skoda"), "CAN 11-bit 500kbps", listOf(
            ECUModule("Engine/PCM", "7E0", "7E8"),
            ECUModule("Transmission/TCM", "7E1", "7E9"),
            ECUModule("ABS/ESP", "713", "77D"),
            ECUModule("SRS/Airbag", "715", "77F"),
            ECUModule("Body Control Module", "710", "77A")
        ))
    )

    /**
     * Look up manufacturer config by ID
     */
    fun getManufacturerConfig(manufacturerId: String?): ManufacturerConfig? {
        if (manufacturerId == null) return null
        val normalized = manufacturerId.lowercase().trim()
        return MANUFACTURER_CONFIGS.find { config ->
            config.ids.any { id -> normalized.contains(id) || id.contains(normalized) }
        }
    }

    /**
     * Common module addresses for fallback when manufacturer unknown
     */
    val COMMON_MODULES = listOf(
        ECUModule("Engine/PCM", "7E0", "7E8"),
        ECUModule("Transmission", "7E1", "7E9"),
        ECUModule("ABS/ESP", "7E2", "7EA"),
        ECUModule("Body Control", "720", "728"),
        ECUModule("Instrument", "726", "72E"),
        ECUModule("Climate/HVAC", "727", "72F"),
        ECUModule("Steering", "737", "73F")
    )
}
