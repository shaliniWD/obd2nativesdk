package com.wisedrive.obd2.constants

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DTC Descriptions database
 */
class DTCDescriptionsTest {

    @Test
    fun `lookup common P codes`() {
        val p0133 = DTCDescriptions.getDTCInfo("P0133")
        assertNotNull(p0133)
        assertTrue(p0133!!.description.contains("O2 Sensor"))
        
        val p0300 = DTCDescriptions.getDTCInfo("P0300")
        assertNotNull(p0300)
        assertTrue(p0300!!.description.contains("Misfire"))
        assertEquals("Critical", p0300.severity)
        
        val p0420 = DTCDescriptions.getDTCInfo("P0420")
        assertNotNull(p0420)
        assertTrue(p0420!!.description.contains("Catalyst"))
    }

    @Test
    fun `lookup U codes (network)`() {
        val u0100 = DTCDescriptions.getDTCInfo("U0100")
        assertNotNull(u0100)
        assertTrue(u0100!!.description.contains("ECM") || u0100.description.contains("PCM"))
        assertEquals("Critical", u0100.severity)
    }

    @Test
    fun `get category name`() {
        assertEquals("Powertrain", DTCDescriptions.getCategoryName("P0123"))
        assertEquals("Body", DTCDescriptions.getCategoryName("B1601"))
        assertEquals("Chassis", DTCDescriptions.getCategoryName("C0035"))
        assertEquals("Network/Communication", DTCDescriptions.getCategoryName("U0100"))
    }

    @Test
    fun `manufacturer specific codes detection`() {
        assertTrue(DTCDescriptions.isManufacturerSpecific("P1234"))
        assertTrue(DTCDescriptions.isManufacturerSpecific("P2000"))
        assertTrue(DTCDescriptions.isManufacturerSpecific("P3000"))
        assertFalse(DTCDescriptions.isManufacturerSpecific("P0123"))
        assertFalse(DTCDescriptions.isManufacturerSpecific("U0100"))
    }

    @Test
    fun `severity assignment`() {
        // Critical codes
        assertEquals("Critical", DTCDescriptions.getSeverity("P0300"))
        assertEquals("Critical", DTCDescriptions.getSeverity("P0700"))
        
        // Important codes
        assertEquals("Important", DTCDescriptions.getSeverity("P0133"))
        assertEquals("Important", DTCDescriptions.getSeverity("P0420"))
        
        // Non-critical codes
        assertEquals("Non-Critical", DTCDescriptions.getSeverity("P0110"))
    }
}
