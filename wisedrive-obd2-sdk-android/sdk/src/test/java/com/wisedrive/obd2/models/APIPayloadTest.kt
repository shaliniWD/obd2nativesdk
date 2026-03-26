package com.wisedrive.obd2.models

import org.junit.Test
import org.junit.Assert.*

/**
 * WhiteBox Tests for APIPayload
 * Tests the API payload structure and field mapping
 */
class APIPayloadTest {

    // ========== WHITEBOX: Field Structure Tests ==========

    @Test
    fun `payload contains all required fields`() {
        val payload = createTestPayload()
        
        // Verify all mandatory fields are present
        assertNotNull(payload.license_plate)
        assertNotNull(payload.tracking_id)
        assertNotNull(payload.report_url)
        assertNotNull(payload.car_company)
        assertNotNull(payload.time)
        assertNotNull(payload.mechanic_name)
        assertNotNull(payload.mechanic_email)
        assertNotNull(payload.vin)
        assertNotNull(payload.scan_ended)
        assertNotNull(payload.faulty_modules)
        assertNotNull(payload.non_faulty_modules)
        assertNotNull(payload.code_details)
    }

    @Test
    fun `license plate maps correctly`() {
        val payload = createTestPayload(licensePlate = "MH12AB1234")
        assertEquals("MH12AB1234", payload.license_plate)
    }

    @Test
    fun `tracking ID maps correctly`() {
        val payload = createTestPayload(trackingId = "ORD6894331")
        assertEquals("ORD6894331", payload.tracking_id)
    }

    @Test
    fun `both registration and tracking ID are independent`() {
        val payload = createTestPayload(
            licensePlate = "KA01MN5678",
            trackingId = "ORD1234567"
        )
        
        assertEquals("KA01MN5678", payload.license_plate)
        assertEquals("ORD1234567", payload.tracking_id)
        assertNotEquals(payload.license_plate, payload.tracking_id)
    }

    // ========== WHITEBOX: Module Classification Tests ==========

    @Test
    fun `faulty and non-faulty modules are mutually exclusive`() {
        val faultyModules = listOf("Engine", "ABS")
        val nonFaultyModules = listOf("Transmission", "BCM", "Airbag")
        
        val payload = createTestPayload(
            faultyModules = faultyModules,
            nonFaultyModules = nonFaultyModules
        )
        
        // No overlap between faulty and non-faulty
        val overlap = payload.faulty_modules.intersect(payload.non_faulty_modules.toSet())
        assertTrue(overlap.isEmpty())
    }

    @Test
    fun `all standard modules covered`() {
        val allModules = ModuleNames.ALL_MODULES
        assertEquals(15, allModules.size)
        
        assertTrue(allModules.contains("Engine"))
        assertTrue(allModules.contains("ABS"))
        assertTrue(allModules.contains("Transmission"))
        assertTrue(allModules.contains("BCM"))
        assertTrue(allModules.contains("Airbag"))
        assertTrue(allModules.contains("HVAC"))
        assertTrue(allModules.contains("EPS"))
        assertTrue(allModules.contains("Others"))
    }

    // ========== WHITEBOX: Code Detail Tests ==========

    @Test
    fun `code detail contains all required fields`() {
        val codeDetail = CodeDetail(
            dtc = "P0503",
            meaning = "Vehicle Speed Sensor A Circuit Intermittent",
            module = "Engine",
            status = "Confirmed",
            descriptions = listOf("Description 1"),
            causes = listOf("Cause 1", "Cause 2"),
            symptoms = listOf("Symptom 1"),
            solutions = listOf("Solution 1", "Solution 2")
        )
        
        assertEquals("P0503", codeDetail.dtc)
        assertEquals("Engine", codeDetail.module)
        assertEquals("Confirmed", codeDetail.status)
        assertFalse(codeDetail.causes.isEmpty())
        assertFalse(codeDetail.solutions.isEmpty())
    }

    @Test
    fun `status values are valid`() {
        val validStatuses = listOf("Confirmed", "Pending", "Permanent")
        
        validStatuses.forEach { status ->
            val detail = CodeDetail(
                dtc = "P0001",
                meaning = "Test",
                module = "Engine",
                status = status,
                descriptions = emptyList(),
                causes = emptyList(),
                symptoms = emptyList(),
                solutions = emptyList()
            )
            assertTrue(validStatuses.contains(detail.status))
        }
    }

    // ========== WHITEBOX: Battery Voltage Tests ==========

    @Test
    fun `battery voltage can be null`() {
        val payload = createTestPayload(batteryVoltage = null)
        assertNull(payload.battery_voltage)
    }

    @Test
    fun `battery voltage can be set`() {
        val payload = createTestPayload(batteryVoltage = 14.02)
        assertEquals(14.02, payload.battery_voltage!!, 0.01)
    }

    // ========== WHITEBOX: MIL Status Tests ==========

    @Test
    fun `mil status true when DTCs present`() {
        val payload = createTestPayload(
            milStatus = true,
            codeDetails = listOf(createTestCodeDetail("P0503"))
        )
        assertTrue(payload.mil_status)
        assertFalse(payload.code_details.isEmpty())
    }

    @Test
    fun `mil status false when no DTCs`() {
        val payload = createTestPayload(
            milStatus = false,
            codeDetails = emptyList()
        )
        assertFalse(payload.mil_status)
        assertTrue(payload.code_details.isEmpty())
    }

    // ========== Helper Functions ==========

    private fun createTestPayload(
        licensePlate: String = "MH12AB1234",
        trackingId: String = "ORD6894331",
        faultyModules: List<String> = emptyList(),
        nonFaultyModules: List<String> = ModuleNames.ALL_MODULES,
        codeDetails: List<CodeDetail> = emptyList(),
        batteryVoltage: Double? = 14.02,
        milStatus: Boolean = false
    ): APIPayload {
        return APIPayload(
            license_plate = licensePlate,
            tracking_id = trackingId,
            report_url = "https://example.com/report.pdf",
            car_company = "Hyundai",
            status = 1,
            time = "2026-01-15T10:30:00.000Z",
            mechanic_name = "Wisedrive Utils",
            mechanic_email = "utils@wisedrive.in",
            vin = "KMHXX00XXXX000000",
            mil_status = milStatus,
            scan_ended = "automatic_success",
            faulty_modules = faultyModules,
            non_faulty_modules = nonFaultyModules,
            code_details = codeDetails,
            battery_voltage = batteryVoltage
        )
    }

    private fun createTestCodeDetail(dtc: String): CodeDetail {
        return CodeDetail(
            dtc = dtc,
            meaning = "Test DTC Description",
            module = "Engine",
            status = "Confirmed",
            descriptions = listOf("Description"),
            causes = listOf("Cause 1"),
            symptoms = listOf("Symptom 1"),
            solutions = listOf("Solution 1")
        )
    }
}
