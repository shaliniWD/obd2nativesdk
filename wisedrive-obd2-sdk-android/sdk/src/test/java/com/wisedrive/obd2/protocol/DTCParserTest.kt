package com.wisedrive.obd2.protocol

import com.wisedrive.obd2.constants.DTCDescriptions
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DTC Parser
 */
class DTCParserTest {

    @Test
    fun `parse healthy car - no DTCs`() {
        val response = "43 00"
        val result = DTCParser.parseOBDResponse(response, "03")
        assertEquals(0, result.totalDTCs)
        assertTrue(result.dtcs.isEmpty())
    }

    @Test
    fun `parse single DTC - P0133`() {
        val response = "43 01 01 33"
        val result = DTCParser.parseOBDResponse(response, "03")
        assertEquals(1, result.totalDTCs)
        assertEquals("P0133", result.dtcs[0].code)
        assertEquals('P', result.dtcs[0].category)
        assertTrue(result.dtcs[0].description.contains("O2 Sensor"))
    }

    @Test
    fun `parse multiple DTCs`() {
        val response = "43 03 01 33 01 01 04 20"
        val result = DTCParser.parseOBDResponse(response, "03")
        assertEquals(3, result.totalDTCs)
        assertEquals("P0133", result.dtcs[0].code)
        assertEquals("P0101", result.dtcs[1].code)
        assertEquals("P0420", result.dtcs[2].code)
    }

    @Test
    fun `parse multi-ECU response with headers`() {
        val response = """
            7E8 06 43 02 01 33 01 01
            7E9 04 43 01 07 00
        """.trimIndent()
        val result = DTCParser.parseOBDResponse(response, "03")
        assertTrue(result.totalDTCs >= 2)
        assertTrue(result.dtcs.any { it.code == "P0133" })
        assertTrue(result.dtcs.any { it.code == "P0700" })
    }

    @Test
    fun `parse pending DTCs - Mode 07`() {
        val response = "47 01 01 16"
        val result = DTCParser.parseOBDResponse(response, "07")
        assertEquals(1, result.totalDTCs)
        assertEquals("P0116", result.dtcs[0].code)
    }

    @Test
    fun `parse no permanent DTCs - Mode 0A`() {
        val response = "4A 00"
        val result = DTCParser.parseOBDResponse(response, "0A")
        assertEquals(0, result.totalDTCs)
    }

    @Test
    fun `decode Body DTC - B category`() {
        // B1601: High byte = 0x96 (10 01 0110), Low byte = 0x01
        val response = "43 01 96 01"
        val result = DTCParser.parseOBDResponse(response, "03")
        assertEquals("B1601", result.dtcs[0].code)
        assertEquals('B', result.dtcs[0].category)
    }

    @Test
    fun `decode Network DTC - U category`() {
        // U0100: High byte = 0xC1 (11 00 0001), Low byte = 0x00
        val response = "43 01 C1 00"
        val result = DTCParser.parseOBDResponse(response, "03")
        assertEquals("U0100", result.dtcs[0].code)
        assertEquals('U', result.dtcs[0].category)
    }

    @Test
    fun `decode Chassis DTC - C category`() {
        // C0035: High byte = 0x40 (01 00 0000), Low byte = 0x35
        val response = "43 01 40 35"
        val result = DTCParser.parseOBDResponse(response, "03")
        assertEquals("C0035", result.dtcs[0].code)
        assertEquals('C', result.dtcs[0].category)
    }

    @Test
    fun `get category name`() {
        assertEquals("Powertrain", DTCParser.getCategoryName('P'))
        assertEquals("Body", DTCParser.getCategoryName('B'))
        assertEquals("Chassis", DTCParser.getCategoryName('C'))
        assertEquals("Network/Communication", DTCParser.getCategoryName('U'))
    }
}
