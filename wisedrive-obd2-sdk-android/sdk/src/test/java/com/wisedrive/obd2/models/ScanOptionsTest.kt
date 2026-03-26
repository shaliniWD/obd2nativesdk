package com.wisedrive.obd2.models

import org.junit.Test
import org.junit.Assert.*

/**
 * WhiteBox Tests for ScanOptions
 * Tests internal validation and field handling
 */
class ScanOptionsTest {

    // ========== WHITEBOX: Validation Tests ==========

    @Test
    fun `valid options with both mandatory fields`() {
        val options = ScanOptions(
            registrationNumber = "MH12AB1234",
            trackingId = "ORD6894331",
            manufacturer = "hyundai",
            year = 2022
        )
        
        assertEquals("MH12AB1234", options.registrationNumber)
        assertEquals("ORD6894331", options.trackingId)
        assertEquals("hyundai", options.manufacturer)
        assertEquals(2022, options.year)
    }

    @Test
    fun `valid options with only mandatory fields`() {
        val options = ScanOptions(
            registrationNumber = "KA01MN5678",
            trackingId = "ORD1234567"
        )
        
        assertEquals("KA01MN5678", options.registrationNumber)
        assertEquals("ORD1234567", options.trackingId)
        assertNull(options.manufacturer)
        assertNull(options.year)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank registration number throws exception`() {
        ScanOptions(
            registrationNumber = "",
            trackingId = "ORD6894331"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `whitespace-only registration number throws exception`() {
        ScanOptions(
            registrationNumber = "   ",
            trackingId = "ORD6894331"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank tracking ID throws exception`() {
        ScanOptions(
            registrationNumber = "MH12AB1234",
            trackingId = ""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `whitespace-only tracking ID throws exception`() {
        ScanOptions(
            registrationNumber = "MH12AB1234",
            trackingId = "   "
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `both fields blank throws exception for registration`() {
        ScanOptions(
            registrationNumber = "",
            trackingId = ""
        )
    }

    // ========== WHITEBOX: Format Acceptance Tests ==========

    @Test
    fun `accepts various registration number formats`() {
        // Indian formats
        val indian1 = ScanOptions("MH12AB1234", "ORD001")
        assertEquals("MH12AB1234", indian1.registrationNumber)
        
        val indian2 = ScanOptions("KA01MN5678", "ORD002")
        assertEquals("KA01MN5678", indian2.registrationNumber)
        
        val indian3 = ScanOptions("DL8CAF1234", "ORD003")
        assertEquals("DL8CAF1234", indian3.registrationNumber)
        
        // US formats
        val us1 = ScanOptions("ABC1234", "ORD004")
        assertEquals("ABC1234", us1.registrationNumber)
        
        // UK formats
        val uk1 = ScanOptions("AB12CDE", "ORD005")
        assertEquals("AB12CDE", uk1.registrationNumber)
        
        // Special characters
        val special = ScanOptions("MH-12-AB-1234", "ORD006")
        assertEquals("MH-12-AB-1234", special.registrationNumber)
    }

    @Test
    fun `accepts various tracking ID formats`() {
        // Standard WiseDrive format
        val wd1 = ScanOptions("REG001", "ORD6894331")
        assertEquals("ORD6894331", wd1.trackingId)
        
        // Alphanumeric
        val alpha = ScanOptions("REG002", "WD2025ABC123")
        assertEquals("WD2025ABC123", alpha.trackingId)
        
        // With hyphens
        val hyphen = ScanOptions("REG003", "ORD-2025-001")
        assertEquals("ORD-2025-001", hyphen.trackingId)
        
        // UUID-style
        val uuid = ScanOptions("REG004", "550e8400-e29b-41d4-a716-446655440000")
        assertEquals("550e8400-e29b-41d4-a716-446655440000", uuid.trackingId)
    }

    // ========== WHITEBOX: Progress Callback Tests ==========

    @Test
    fun `progress callback can be null`() {
        val options = ScanOptions(
            registrationNumber = "MH12AB1234",
            trackingId = "ORD6894331",
            onProgress = null
        )
        assertNull(options.onProgress)
    }

    @Test
    fun `progress callback can be provided`() {
        var callbackInvoked = false
        val options = ScanOptions(
            registrationNumber = "MH12AB1234",
            trackingId = "ORD6894331",
            onProgress = { stage ->
                callbackInvoked = true
            }
        )
        assertNotNull(options.onProgress)
    }

    // ========== WHITEBOX: Data Class Equality Tests ==========

    @Test
    fun `data class equality works correctly`() {
        val options1 = ScanOptions("MH12AB1234", "ORD001", "hyundai", 2022)
        val options2 = ScanOptions("MH12AB1234", "ORD001", "hyundai", 2022)
        val options3 = ScanOptions("MH12AB1234", "ORD002", "hyundai", 2022)
        
        assertEquals(options1, options2)
        assertNotEquals(options1, options3)
    }

    @Test
    fun `copy function preserves fields`() {
        val original = ScanOptions("MH12AB1234", "ORD001", "hyundai", 2022)
        val copied = original.copy(trackingId = "ORD002")
        
        assertEquals("MH12AB1234", copied.registrationNumber)
        assertEquals("ORD002", copied.trackingId)
        assertEquals("hyundai", copied.manufacturer)
        assertEquals(2022, copied.year)
    }
}
