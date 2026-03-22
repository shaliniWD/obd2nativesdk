package com.wisedrive.obd2.constants

/**
 * Manufacturers master list for validation and lookup
 */
object Manufacturers {
    
    val SUPPORTED_MANUFACTURERS = mapOf(
        // Indian
        "tata" to "Tata Motors",
        "mahindra" to "Mahindra & Mahindra",
        "maruti" to "Maruti Suzuki",
        "maruti suzuki" to "Maruti Suzuki",
        "suzuki" to "Maruti Suzuki",
        
        // Korean
        "hyundai" to "Hyundai",
        "kia" to "Kia",
        
        // Japanese
        "toyota" to "Toyota",
        "lexus" to "Toyota/Lexus",
        "honda" to "Honda",
        "acura" to "Honda/Acura",
        "nissan" to "Nissan",
        "datsun" to "Nissan/Datsun",
        "infiniti" to "Nissan/Infiniti",
        "mitsubishi" to "Mitsubishi",
        "mazda" to "Mazda",
        "subaru" to "Subaru",
        
        // German
        "vw" to "Volkswagen",
        "volkswagen" to "Volkswagen",
        "audi" to "Audi",
        "bmw" to "BMW",
        "mini" to "BMW/MINI",
        "mercedes" to "Mercedes-Benz",
        "mercedes-benz" to "Mercedes-Benz",
        "mb" to "Mercedes-Benz",
        "porsche" to "Porsche",
        
        // American
        "ford" to "Ford",
        "lincoln" to "Ford/Lincoln",
        "gm" to "General Motors",
        "chevrolet" to "General Motors/Chevrolet",
        "buick" to "General Motors/Buick",
        "cadillac" to "General Motors/Cadillac",
        "gmc" to "General Motors/GMC",
        "chrysler" to "Chrysler/FCA",
        "dodge" to "Chrysler/Dodge",
        "jeep" to "Chrysler/Jeep",
        "ram" to "Chrysler/RAM",
        "fca" to "FCA/Stellantis",
        "stellantis" to "Stellantis",
        "tesla" to "Tesla",
        
        // European
        "volvo" to "Volvo",
        "jaguar" to "Jaguar Land Rover",
        "land rover" to "Jaguar Land Rover",
        "jlr" to "Jaguar Land Rover",
        "renault" to "Renault",
        "dacia" to "Renault/Dacia",
        "peugeot" to "Peugeot",
        "citroen" to "Citroen",
        "psa" to "PSA Group",
        "fiat" to "Fiat",
        "alfa romeo" to "Alfa Romeo",
        "lancia" to "Lancia",
        "seat" to "SEAT",
        "skoda" to "Skoda"
    )
    
    fun getDisplayName(manufacturerId: String?): String {
        if (manufacturerId == null) return "Unknown"
        return SUPPORTED_MANUFACTURERS[manufacturerId.lowercase().trim()] ?: manufacturerId
    }
    
    fun isSupported(manufacturerId: String?): Boolean {
        if (manufacturerId == null) return false
        return SUPPORTED_MANUFACTURERS.containsKey(manufacturerId.lowercase().trim())
    }
}
