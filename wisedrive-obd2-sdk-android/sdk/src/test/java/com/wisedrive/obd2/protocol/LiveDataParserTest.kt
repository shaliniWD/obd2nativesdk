package com.wisedrive.obd2.protocol

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Live Data Parser
 */
class LiveDataParserTest {

    @Test
    fun `parse RPM - PID 0C`() {
        val response = "41 0C 1A F8"
        val reading = LiveDataParser.parseLiveDataResponse("0C", response)
        assertNotNull(reading)
        // Formula: ((0x1A * 256) + 0xF8) / 4 = (6656 + 248) / 4 = 1726
        assertEquals(1726.0, reading!!.value, 0.1)
        assertEquals("RPM", reading.unit)
        assertEquals("Engine RPM", reading.name)
    }

    @Test
    fun `parse Vehicle Speed - PID 0D`() {
        val response = "41 0D 45"
        val reading = LiveDataParser.parseLiveDataResponse("0D", response)
        assertNotNull(reading)
        assertEquals(69.0, reading!!.value, 0.1)
        assertEquals("km/h", reading.unit)
    }

    @Test
    fun `parse Coolant Temp - PID 05`() {
        val response = "41 05 7B"
        val reading = LiveDataParser.parseLiveDataResponse("05", response)
        assertNotNull(reading)
        // Formula: 0x7B - 40 = 123 - 40 = 83
        assertEquals(83.0, reading!!.value, 0.1)
        assertEquals("C", reading.unit)
    }

    @Test
    fun `parse Control Module Voltage - PID 42`() {
        val response = "41 42 37 DC"
        val reading = LiveDataParser.parseLiveDataResponse("42", response)
        assertNotNull(reading)
        // Formula: ((0x37 * 256) + 0xDC) / 1000 = (14080 + 220) / 1000 = 14.3
        assertEquals(14.3, reading!!.value, 0.1)
        assertEquals("V", reading.unit)
    }

    @Test
    fun `parse response with ECU header`() {
        val response = "7E8 06 41 0C 1A F8"
        val reading = LiveDataParser.parseLiveDataResponse("0C", response)
        assertNotNull(reading)
        assertEquals(1726.0, reading!!.value, 0.1)
    }

    @Test
    fun `parse Throttle Position - PID 11`() {
        val response = "41 11 1A"
        val reading = LiveDataParser.parseLiveDataResponse("11", response)
        assertNotNull(reading)
        // Formula: (0x1A * 100) / 255 = (26 * 100) / 255 = 10.2%
        assertEquals(10.2, reading!!.value, 0.5)
        assertEquals("%", reading.unit)
    }

    @Test
    fun `parse Engine Load - PID 04`() {
        val response = "41 04 64"
        val reading = LiveDataParser.parseLiveDataResponse("04", response)
        assertNotNull(reading)
        // Formula: (0x64 * 100) / 255 = (100 * 100) / 255 = 39.2%
        assertEquals(39.2, reading!!.value, 0.5)
    }

    @Test
    fun `parse Intake Air Temp - PID 0F`() {
        val response = "41 0F 4B"
        val reading = LiveDataParser.parseLiveDataResponse("0F", response)
        assertNotNull(reading)
        // Formula: 0x4B - 40 = 75 - 40 = 35
        assertEquals(35.0, reading!!.value, 0.1)
    }

    @Test
    fun `return null for unsupported PID`() {
        val reading = LiveDataParser.parseLiveDataResponse("FF", "41 FF 00")
        assertNull(reading)
    }

    @Test
    fun `return null for malformed response`() {
        val reading = LiveDataParser.parseLiveDataResponse("0C", "NO DATA")
        assertNull(reading)
    }
}
