package com.wisedrive.obd2.network

import com.wisedrive.obd2.models.EncryptedPayload
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for MockAPIClient
 */
class MockAPIClientTest {

    private val mockClient = MockAPIClient()

    @Test
    fun fetchEncryptionKeyReturnsValidData() {
        runBlocking {
            val result = mockClient.fetchEncryptionKey()
            
            assertNotNull(result)
            result?.let { (key, keyId, expiresAt) ->
                // Key should be base64 encoded
                assertTrue(key.isNotEmpty())
                assertTrue(key.length > 20) // Base64 of 32 bytes
                
                // KeyId should be a UUID
                assertTrue(keyId.contains("-"))
                assertEquals(36, keyId.length)
                
                // ExpiresAt should be in the future
                assertTrue(expiresAt > System.currentTimeMillis())
            }
        }
    }

    @Test
    fun submitReportAlwaysSucceedsInMockMode() {
        runBlocking {
            val payload = EncryptedPayload(
                keyId = "mock-key-id",
                iv = "mock_iv_base64",
                payload = "encrypted_data_here",
                timestamp = "2025-03-22T12:00:00Z",
                algorithm = "AES-256-GCM"
            )
            
            val success = mockClient.submitReport(payload)
            
            assertTrue(success)
        }
    }

    @Test
    fun submitReportHandlesVariousPayloadSizes() {
        runBlocking {
            // Small payload
            val smallPayload = EncryptedPayload(
                keyId = "key1",
                iv = "iv_base64",
                payload = "small",
                timestamp = "2025-03-22T12:00:00Z",
                algorithm = "AES-256-GCM"
            )
            assertTrue(mockClient.submitReport(smallPayload))
            
            // Large payload
            val largeData = "x".repeat(100000)
            val largePayload = EncryptedPayload(
                keyId = "key2",
                iv = "iv_base64",
                payload = largeData,
                timestamp = "2025-03-22T12:00:00Z",
                algorithm = "AES-256-GCM"
            )
            assertTrue(mockClient.submitReport(largePayload))
        }
    }

    @Test
    fun encryptionKeyExpiresIn24Hours() {
        runBlocking {
            val result = mockClient.fetchEncryptionKey()
            
            assertNotNull(result)
            result?.let { (_, _, expiresAt) ->
                val now = System.currentTimeMillis()
                val twentyFourHoursInMs = 24 * 60 * 60 * 1000L
                
                // Should expire roughly 24 hours from now (with some tolerance)
                val diff = expiresAt - now
                assertTrue(diff > twentyFourHoursInMs - 1000) // At least 23:59:59
                assertTrue(diff <= twentyFourHoursInMs + 1000) // At most 24:00:01
            }
        }
    }
}
