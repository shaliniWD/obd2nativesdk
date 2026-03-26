package com.wisedrive.obd2.network

import com.wisedrive.obd2.models.*
import org.junit.Test
import org.junit.Assert.*

/**
 * WhiteBox Tests for ReportTransformer
 * Tests the transformation logic from ScanReport to APIPayload
 */
class ReportTransformerTest {

    // ========== WHITEBOX: Field Mapping Tests ==========

    @Test
    fun `registration number maps to license_plate`() {
        val scanReport = createMinimalScanReport()
        val payload = ReportTransformer.transform(scanReport, "MH12AB1234", "ORD001")
        
        assertEquals("MH12AB1234", payload.license_plate)
    }

    @Test
    fun `tracking ID maps to tracking_id`() {
        val scanReport = createMinimalScanReport()
        val payload = ReportTransformer.transform(scanReport, "MH12AB1234", "ORD6894331")
        
        assertEquals("ORD6894331", payload.tracking_id)
    }

    @Test
    fun `both fields are independent in output`() {
        val scanReport = createMinimalScanReport()
        val payload = ReportTransformer.transform(scanReport, "KA01MN5678", "ORD1234567")
        
        assertEquals("KA01MN5678", payload.license_plate)
        assertEquals("ORD1234567", payload.tracking_id)
        assertNotEquals(payload.license_plate, payload.tracking_id)
    }

    // ========== WHITEBOX: Module Mapping Tests ==========

    @Test
    fun `category Powertrain maps to Engine`() {
        val module = ReportTransformer.mapCategoryToModule("Powertrain")
        assertEquals("Engine", module)
    }

    @Test
    fun `category Body maps to BCM`() {
        val module = ReportTransformer.mapCategoryToModule("Body")
        assertEquals("BCM", module)
    }

    @Test
    fun `category Chassis maps to ABS`() {
        val module = ReportTransformer.mapCategoryToModule("Chassis")
        assertEquals("ABS", module)
    }

    @Test
    fun `category Network maps to Others`() {
        val module1 = ReportTransformer.mapCategoryToModule("Network")
        val module2 = ReportTransformer.mapCategoryToModule("Network/Communication")
        
        assertEquals("Others", module1)
        assertEquals("Others", module2)
    }

    @Test
    fun `unknown category returns category name`() {
        val module = ReportTransformer.mapCategoryToModule("CustomModule")
        assertEquals("CustomModule", module)
    }

    // ========== WHITEBOX: ECU Source Mapping Tests ==========

    @Test
    fun `ECU source with engine keyword maps to Engine`() {
        val sources = listOf("Engine Control Module", "PCM", "DME", "ME/SFI")
        sources.forEach { source ->
            val module = ReportTransformer.mapECUSourceToModule(source)
            assertEquals("Engine", module)
        }
    }

    @Test
    fun `ECU source with transmission keyword maps to Transmission`() {
        val sources = listOf("Transmission Control", "TCM", "EGS", "VGS")
        sources.forEach { source ->
            val module = ReportTransformer.mapECUSourceToModule(source)
            assertEquals("Transmission", module)
        }
    }

    @Test
    fun `ECU source with ABS keyword maps to ABS`() {
        val sources = listOf("ABS Module", "ESP", "VSC", "VDC", "DSC", "ASC", "VSA", "EBCM")
        sources.forEach { source ->
            val module = ReportTransformer.mapECUSourceToModule(source)
            assertEquals("ABS", module)
        }
    }

    @Test
    fun `ECU source with airbag keyword maps to Airbag`() {
        val sources = listOf("Airbag Module", "SRS", "SDM", "ORC", "RCM")
        sources.forEach { source ->
            val module = ReportTransformer.mapECUSourceToModule(source)
            assertEquals("Airbag", module)
        }
    }

    @Test
    fun `ECU source with body keyword maps to BCM`() {
        val sources = listOf("Body Control", "BCM", "SAM", "FRM")
        sources.forEach { source ->
            val module = ReportTransformer.mapECUSourceToModule(source)
            assertEquals("BCM", module)
        }
    }

    @Test
    fun `unknown ECU source maps to Others`() {
        val module = ReportTransformer.mapECUSourceToModule("Unknown Module XYZ")
        assertEquals("Others", module)
    }

    // ========== WHITEBOX: Faulty Module Detection Tests ==========

    @Test
    fun `faulty modules extracted from DTCs`() {
        val scanReport = createScanReportWithDTCs(
            listOf(
                createDTCDetail("P0503", "Powertrain", "Engine Control Module (ECM)"),
                createDTCDetail("C0035", "Chassis", "ABS/ESP Control Module")
            )
        )
        
        val payload = ReportTransformer.transform(scanReport, "REG001", "TRK001")
        
        assertTrue(payload.faulty_modules.contains("Engine"))
        assertTrue(payload.faulty_modules.contains("ABS"))
    }

    @Test
    fun `non-faulty modules are complement of faulty`() {
        val scanReport = createScanReportWithDTCs(
            listOf(createDTCDetail("P0503", "Powertrain", "Engine Control Module"))
        )
        
        val payload = ReportTransformer.transform(scanReport, "REG001", "TRK001")
        
        // Engine is faulty, so it should not be in non-faulty
        assertFalse(payload.non_faulty_modules.contains("Engine"))
        
        // Other modules should be non-faulty
        assertTrue(payload.non_faulty_modules.contains("Transmission"))
        assertTrue(payload.non_faulty_modules.contains("BCM"))
    }

    // ========== WHITEBOX: DTC Status Mapping Tests ==========

    @Test
    fun `history DTCs get status Confirmed`() {
        val scanReport = createScanReportWithDTCsByCategory(
            history = listOf(createDTCDetail("P0503")),
            pending = emptyList(),
            current = emptyList()
        )
        
        val payload = ReportTransformer.transform(scanReport, "REG001", "TRK001")
        
        assertEquals(1, payload.code_details.size)
        assertEquals("Confirmed", payload.code_details[0].status)
    }

    @Test
    fun `pending DTCs get status Pending`() {
        val scanReport = createScanReportWithDTCsByCategory(
            history = emptyList(),
            pending = listOf(createDTCDetail("P0301")),
            current = emptyList()
        )
        
        val payload = ReportTransformer.transform(scanReport, "REG001", "TRK001")
        
        assertEquals(1, payload.code_details.size)
        assertEquals("Pending", payload.code_details[0].status)
    }

    @Test
    fun `current DTCs get status Permanent`() {
        val scanReport = createScanReportWithDTCsByCategory(
            history = emptyList(),
            pending = emptyList(),
            current = listOf(createDTCDetail("P0420"))
        )
        
        val payload = ReportTransformer.transform(scanReport, "REG001", "TRK001")
        
        assertEquals(1, payload.code_details.size)
        assertEquals("Permanent", payload.code_details[0].status)
    }

    // ========== WHITEBOX: Battery Voltage Extraction Tests ==========

    @Test
    fun `battery voltage extracted from live data`() {
        val scanReport = createScanReportWithLiveData(
            listOf(
                LiveDataReading(
                    pid = "42",
                    name = "Control Module Voltage",
                    shortName = "Voltage",
                    value = 14.02,
                    displayValue = "14.02 V",
                    unit = "V",
                    category = "electrical",
                    timestamp = System.currentTimeMillis(),
                    rawHex = "410E38"
                ),
                LiveDataReading(
                    pid = "0C",
                    name = "Engine RPM",
                    shortName = "RPM",
                    value = 800.0,
                    displayValue = "800 rpm",
                    unit = "rpm",
                    category = "engine",
                    timestamp = System.currentTimeMillis(),
                    rawHex = "410C0C80"
                )
            )
        )
        
        val payload = ReportTransformer.transform(scanReport, "REG001", "TRK001")
        
        assertNotNull(payload.battery_voltage)
        assertEquals(14.02, payload.battery_voltage!!, 0.01)
    }

    @Test
    fun `battery voltage null if not in live data`() {
        val scanReport = createScanReportWithLiveData(
            listOf(
                LiveDataReading(
                    pid = "0C",
                    name = "Engine RPM",
                    shortName = "RPM",
                    value = 800.0,
                    displayValue = "800 rpm",
                    unit = "rpm",
                    category = "engine",
                    timestamp = System.currentTimeMillis(),
                    rawHex = "410C0C80"
                ),
                LiveDataReading(
                    pid = "05",
                    name = "Coolant Temp",
                    shortName = "Coolant",
                    value = 90.0,
                    displayValue = "90 C",
                    unit = "C",
                    category = "temperature",
                    timestamp = System.currentTimeMillis(),
                    rawHex = "41055A"
                )
            )
        )
        
        val payload = ReportTransformer.transform(scanReport, "REG001", "TRK001")
        
        assertNull(payload.battery_voltage)
    }

    // ========== Helper Functions ==========

    private fun createMinimalScanReport(): ScanReport {
        return ScanReport(
            inspectionId = null,
            scanId = "test-scan-001",
            scanTimestamp = "2026-01-15T10:30:00.000Z",
            scanDuration = 5000,
            vehicle = VehicleInfo("Hyundai", "hyundai", 2022, "KMHXX00XXXX000000"),
            protocol = "ISO 15765-4 CAN",
            milStatus = MILStatus(false, 0),
            diagnosticTroubleCodes = emptyList(),
            dtcsByCategory = DTCsByCategory(emptyList(), emptyList(), emptyList()),
            liveData = emptyList(),
            summary = ScanSummary(0, DTCTypeCounts(0, 0, 0), DTCCategoryCounts(0, 0, 0), 0, 1),
            generatedAt = "2026-01-15T10:30:05.000Z"
        )
    }

    private fun createDTCDetail(
        code: String, 
        category: String = "Powertrain",
        ecuSource: String? = null
    ): DTCDetail {
        return DTCDetail(
            code = code,
            category = category,
            description = "Test DTC: $code",
            severity = "Important",
            possibleCauses = listOf("Cause 1"),
            symptoms = listOf("Symptom 1"),
            solutions = listOf("Solution 1"),
            isManufacturerSpecific = false,
            ecuSource = ecuSource
        )
    }

    private fun createScanReportWithDTCs(dtcs: List<DTCDetail>): ScanReport {
        return createMinimalScanReport().copy(
            diagnosticTroubleCodes = dtcs,
            dtcsByCategory = DTCsByCategory(history = dtcs, pending = emptyList(), current = emptyList()),
            milStatus = MILStatus(dtcs.isNotEmpty(), dtcs.size),
            summary = ScanSummary(dtcs.size, DTCTypeCounts(0, dtcs.size, 0), DTCCategoryCounts(dtcs.size, 0, 0), 0, 1)
        )
    }

    private fun createScanReportWithDTCsByCategory(
        history: List<DTCDetail>,
        pending: List<DTCDetail>,
        current: List<DTCDetail>
    ): ScanReport {
        val allDTCs = history + pending + current
        return createMinimalScanReport().copy(
            diagnosticTroubleCodes = allDTCs,
            dtcsByCategory = DTCsByCategory(history = history, pending = pending, current = current),
            milStatus = MILStatus(allDTCs.isNotEmpty(), allDTCs.size),
            summary = ScanSummary(allDTCs.size, DTCTypeCounts(0, allDTCs.size, 0), 
                DTCCategoryCounts(history.size, current.size, pending.size), 0, 1)
        )
    }

    private fun createScanReportWithLiveData(liveData: List<LiveDataReading>): ScanReport {
        return createMinimalScanReport().copy(
            liveData = liveData,
            summary = createMinimalScanReport().summary.copy(totalLiveReadings = liveData.size)
        )
    }
}
