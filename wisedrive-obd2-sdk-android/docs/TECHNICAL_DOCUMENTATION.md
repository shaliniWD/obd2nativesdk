# WiseDrive OBD2 SDK - Technical Documentation

## Version 2.0.0

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Security Architecture](#2-security-architecture)
3. [Cryptographic Implementation](#3-cryptographic-implementation)
4. [Protocol Specifications](#4-protocol-specifications)
5. [Backend Integration Guide](#5-backend-integration-guide)
6. [Key Management](#6-key-management)
7. [API Specifications](#7-api-specifications)
8. [Error Handling](#8-error-handling)
9. [Performance Considerations](#9-performance-considerations)
10. [Security Testing Results](#10-security-testing-results)

---

## 1. Architecture Overview

### 1.1 System Components

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           MOBILE APPLICATION                             │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    WiseDrive OBD2 SDK v2.0                        │  │
│  │                                                                    │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐   │  │
│  │  │  Bluetooth  │  │   Scanner   │  │    Security Module      │   │  │
│  │  │   Module    │  │   Engine    │  │                         │   │  │
│  │  │             │  │             │  │  • AdvancedEncryption   │   │  │
│  │  │  • Classic  │  │  • Multi-ECU│  │  • ObfuscatedKeyStore  │   │  │
│  │  │  • BLE      │  │  • DTCs     │  │  • IntegrityChecker    │   │  │
│  │  │  • Mock     │  │  • LiveData │  │  • SDKSecurityManager  │   │  │
│  │  └──────┬──────┘  └──────┬──────┘  └───────────┬─────────────┘   │  │
│  │         │                │                      │                 │  │
│  │         └────────────────┴──────────────────────┘                 │  │
│  │                          │                                        │  │
│  │  ┌───────────────────────┴───────────────────────────────────┐   │  │
│  │  │                    Network Layer                           │   │  │
│  │  │                                                            │   │  │
│  │  │  • SecureWiseDriveAnalytics (Encrypted)                   │   │  │
│  │  │  • ReportTransformer                                       │   │  │
│  │  │  • Retry Logic (10 attempts, exponential backoff)         │   │  │
│  │  └───────────────────────┬───────────────────────────────────┘   │  │
│  └───────────────────────────┼───────────────────────────────────────┘  │
└───────────────────────────────┼──────────────────────────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    │    HTTPS (TLS 1.3)    │
                    └───────────┬───────────┘
                                │
┌───────────────────────────────┼──────────────────────────────────────────┐
│                               │         BACKEND SERVERS                   │
│              ┌────────────────┴────────────────┐                         │
│              │                                  │                         │
│  ┌───────────▼───────────┐      ┌──────────────▼──────────────┐         │
│  │   WiseDrive Backend   │      │      Client Backend         │         │
│  │                       │      │                              │         │
│  │  /encrypted endpoint  │      │  Receives encrypted blob    │         │
│  │  WiseDriveDecryptor   │      │  ClientDecryptor            │         │
│  │  WISEDRIVE_PRIVATE_KEY│      │  CLIENT_PRIVATE_KEY         │         │
│  │                       │      │                              │         │
│  │  Can decrypt: WDSW    │      │  Can decrypt: WDSC          │         │
│  │  Cannot: WDSC         │      │  Cannot: WDSW               │         │
│  └───────────────────────┘      └──────────────────────────────┘         │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Module Descriptions

| Module | File | Purpose |
|--------|------|---------|
| **WiseDriveOBD2SDK** | `WiseDriveOBD2SDK.kt` | Main SDK entry point |
| **AdvancedEncryptionManager** | `security/AdvancedEncryptionManager.kt` | RSA+AES hybrid encryption |
| **ObfuscatedKeyStore** | `security/ObfuscatedKeyStore.kt` | Secure key storage |
| **IntegrityChecker** | `security/IntegrityChecker.kt` | Root/emulator detection |
| **SecureWiseDriveAnalytics** | `network/SecureWiseDriveAnalytics.kt` | Encrypted submission |
| **BluetoothClassicAdapter** | `adapter/BluetoothClassicAdapter.kt` | SPP connection |
| **BLEAdapter** | `adapter/BLEAdapter.kt` | BLE connection |
| **DTCParser** | `protocol/DTCParser.kt` | DTC code parsing |

### 1.3 Data Flow

```
1. User initiates scan
       │
       ▼
2. SDK connects to OBD adapter via Bluetooth
       │
       ▼
3. Scanner engine queries ECUs
       │
       ▼
4. Raw OBD data parsed into ScanReport
       │
       ├──────────────────────────────────┐
       ▼                                  ▼
5a. Plain ScanReport                 5b. APIPayload created
    returned to app                       │
       │                                  ▼
       │                          6. Encryption Layer
       │                             • AES-256-GCM (data)
       │                             • RSA-4096 (key)
       │                             • HMAC-SHA512 (integrity)
       │                                  │
       │                                  ▼
       │                          7. Encrypted blob created
       │                             • WDSW for WiseDrive
       │                             • WDSC for Client
       │                                  │
       │                                  ▼
       │                          8. HTTP POST to endpoint
       │                                  │
       ▼                                  ▼
9. App displays results          10. Backend decrypts & processes
```

---

## 2. Security Architecture

### 2.1 Defense in Depth

```
┌─────────────────────────────────────────────────────────────┐
│                    SECURITY LAYERS                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Layer 5: Application Security                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ • ProGuard/R8 obfuscation                              │ │
│  │ • Root detection                                        │ │
│  │ • Emulator detection                                    │ │
│  │ • Frida/Xposed detection                               │ │
│  │ • Debugger detection                                    │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  Layer 4: Transport Security                                │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ • TLS 1.3 with certificate pinning (recommended)       │ │
│  │ • HTTP headers (X-Encryption-Version, X-Key-ID)        │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  Layer 3: Message Integrity                                 │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ • HMAC-SHA512 over entire payload                      │ │
│  │ • AES-GCM authentication tag (128-bit)                 │ │
│  │ • Timestamp for freshness                              │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  Layer 2: Key Encryption                                    │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ • RSA-4096 with OAEP padding                           │ │
│  │ • SHA-256 for MGF1                                      │ │
│  │ • Per-encryption random AES key                        │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  Layer 1: Data Encryption                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ • AES-256-GCM (Galois/Counter Mode)                    │ │
│  │ • 96-bit random IV per encryption                      │ │
│  │ • 128-bit authentication tag                           │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Threat Model

| Threat | Mitigation | Status |
|--------|------------|--------|
| **Data Interception** | AES-256-GCM + TLS | ✅ Mitigated |
| **Key Extraction** | RSA-4096, key never transmitted | ✅ Mitigated |
| **Replay Attacks** | Timestamp + backend validation | ✅ Mitigated |
| **Tampering** | HMAC-SHA512 + GCM auth tag | ✅ Mitigated |
| **Reverse Engineering** | ProGuard + key obfuscation | ✅ Mitigated |
| **Root Exploitation** | Root detection, key in memory only | ✅ Mitigated |
| **MITM** | TLS + encrypted payload | ✅ Mitigated |
| **Brute Force** | 2^256 AES + 2^4096 RSA keyspace | ✅ Infeasible |

---

## 3. Cryptographic Implementation

### 3.1 Algorithm Specifications

| Component | Algorithm | Parameters |
|-----------|-----------|------------|
| **Data Encryption** | AES-256-GCM | Key: 256 bits, IV: 96 bits, Tag: 128 bits |
| **Key Encryption** | RSA-OAEP | Key: 4096 bits, Hash: SHA-256, MGF: MGF1-SHA256 |
| **Integrity** | HMAC-SHA512 | Key: derived from AES key, Output: 512 bits |
| **Key Derivation** | SHA-256 | Input: AES key + "HMAC_KEY_DERIVATION" |

### 3.2 Encrypted Payload Format

```
Offset  Size    Field
──────────────────────────────────────
0       4       Magic ("WDSC" or "WDSW")
4       2       Version (uint16, big-endian)
6       4       Key ID (uint32, big-endian)
10      6       Timestamp (uint48, big-endian, ms since epoch)
16      512     RSA-encrypted AES key (4096-bit RSA)
528     12      IV (random bytes)
540     var     AES-GCM ciphertext + auth tag (16 bytes)
-64     64      HMAC-SHA512 signature
```

### 3.3 Encryption Code Flow

```kotlin
// AdvancedEncryptionManager.kt - Simplified flow

fun encrypt(plaintext: String, rsaPublicKey: PublicKey, magic: String): EncryptedBlob {
    // 1. Generate random AES-256 key
    val aesKey = KeyGenerator.getInstance("AES").apply {
        init(256, SecureRandom())
    }.generateKey()
    
    // 2. Generate random IV
    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
    
    // 3. Encrypt data with AES-GCM
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
    val ciphertext = cipher.doFinal(plaintext.toByteArray())
    
    // 4. Encrypt AES key with RSA-OAEP
    val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
    val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)
    
    // 5. Build header
    val header = buildHeader(magic, System.currentTimeMillis())
    
    // 6. Assemble payload without HMAC
    val payloadWithoutHmac = header + encryptedAesKey + iv + ciphertext
    
    // 7. Calculate HMAC-SHA512
    val hmacKey = deriveHmacKey(aesKey)
    val hmac = Mac.getInstance("HmacSHA512").apply {
        init(hmacKey)
    }.doFinal(payloadWithoutHmac)
    
    // 8. Final payload
    val finalPayload = payloadWithoutHmac + hmac
    
    return EncryptedBlob(
        payload = Base64.encodeToString(finalPayload, Base64.NO_WRAP)
    )
}
```

### 3.4 Decryption Code Flow (Java)

```java
// WiseDriveDecryptor.java - Simplified flow

public JsonNode decrypt(String encryptedBase64) throws DecryptionException {
    // 1. Decode Base64
    byte[] data = Base64.getDecoder().decode(encryptedBase64);
    
    // 2. Parse header
    EncryptedHeader header = parseHeader(data);
    
    // 3. Extract components
    byte[] encryptedAesKey = extractBytes(data, 16, 512);
    byte[] iv = extractBytes(data, 528, 12);
    byte[] ciphertext = extractBytes(data, 540, data.length - 540 - 64);
    byte[] hmacSignature = extractBytes(data, data.length - 64, 64);
    
    // 4. Decrypt AES key with RSA
    Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
    rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
    byte[] aesKey = rsaCipher.doFinal(encryptedAesKey);
    
    // 5. Verify HMAC
    byte[] hmacKey = deriveHmacKey(aesKey);
    byte[] expectedHmac = computeHmac(data, 0, data.length - 64, hmacKey);
    if (!MessageDigest.isEqual(expectedHmac, hmacSignature)) {
        throw new DecryptionException("HMAC verification failed!");
    }
    
    // 6. Decrypt data with AES-GCM
    Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
    aesCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                   new GCMParameterSpec(128, iv));
    byte[] plaintext = aesCipher.doFinal(ciphertext);
    
    // 7. Parse JSON
    return objectMapper.readTree(new String(plaintext, UTF_8));
}
```

---

## 4. Protocol Specifications

### 4.1 HTTP API Protocol

#### Encrypted Endpoint

```http
POST /apiv2/webhook/obdreport/wisedrive/encrypted HTTP/1.1
Host: api.wisedrive.in
Content-Type: application/json
Authorization: Basic <credentials>
X-Encryption-Version: 2
X-Key-ID: 1

{
  "version": 2,
  "keyId": 1,
  "timestamp": 1705312800000,
  "encryptedData": "V0RTVwACAAAAAQGdNBUNdUrQ..."
}
```

#### Response

```json
{
  "result": "SUCCESS",
  "decrypted": true,
  "trackingId": "ORD6894331",
  "dtcCount": 2,
  "timestamp": "2026-03-28T10:00:00Z"
}
```

### 4.2 Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | `invalid_payload` | Missing or malformed encryptedData |
| 400 | `unsupported_version` | Encryption version not supported |
| 400 | `timestamp_expired` | Payload timestamp too old |
| 400 | `duplicate_payload` | Replay attack detected |
| 400 | `decryption_failed` | Could not decrypt payload |
| 400 | `payload_tampered` | HMAC verification failed |
| 400 | `invalid_key` | RSA decryption failed |
| 401 | `unauthorized` | Invalid credentials |
| 500 | `processing_failed` | Internal server error |

### 4.3 Decrypted Payload Schema

```json
{
  "license_plate": "string (required)",
  "tracking_id": "string (required)",
  "report_url": "string (optional)",
  "car_company": "string (optional)",
  "status": "integer",
  "time": "ISO8601 datetime",
  "mechanic_name": "string",
  "mechanic_email": "string",
  "vin": "string (17 chars)",
  "mil_status": "boolean",
  "scan_ended": "string (enum: automatic_success, manual_end, error)",
  "faulty_modules": ["string"],
  "non_faulty_modules": ["string"],
  "code_details": [
    {
      "dtc": "string (e.g., P0503)",
      "meaning": "string",
      "module": "string",
      "status": "string (Confirmed, Pending, etc.)",
      "descriptions": ["string"],
      "causes": ["string"],
      "symptoms": ["string"],
      "solutions": ["string"]
    }
  ],
  "battery_voltage": "number"
}
```

---

## 5. Backend Integration Guide

### 5.1 Java/Spring Boot Integration

#### Step 1: Add Dependencies

```xml
<!-- pom.xml -->
<dependencies>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.15.0</version>
    </dependency>
</dependencies>
```

#### Step 2: Copy Decryptor Classes

Copy the following files to your project:

```
src/main/java/com/wisedrive/inspection/
├── security/
│   └── WiseDriveDecryptor.java
├── dto/
│   ├── EncryptedOBDPayload.java
│   └── OBDScanData.java
└── controllers/
    └── EncryptedObdWebhookController.java
```

#### Step 3: Configure Private Key

```yaml
# application.yml
wisedrive:
  encryption:
    private-key-path: /etc/secrets/wisedrive_private.pem
```

Or via environment variable:

```bash
export WISEDRIVE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----
MIIJQgIBADANBgkqh...
-----END PRIVATE KEY-----"
```

#### Step 4: Deploy and Test

```bash
# Health check
curl https://your-api.com/apiv2/webhook/obdreport/wisedrive/encrypted/health

# Expected response
{
  "service": "WiseDrive OBD Encrypted Webhook",
  "encryptionEnabled": true,
  "supportedVersion": 2,
  "algorithm": "RSA-4096 + AES-256-GCM + HMAC-SHA512"
}
```

### 5.2 Python/Flask Integration

#### Step 1: Install Dependencies

```bash
pip install cryptography flask
```

#### Step 2: Copy Decryptor Module

```bash
cp wisedrive_decryption.py /your/project/
```

#### Step 3: Create Endpoint

```python
from flask import Flask, request, jsonify
from wisedrive_decryption import WiseDriveDecryptor, DecryptionError
import os

app = Flask(__name__)

PRIVATE_KEY = os.environ.get('WISEDRIVE_PRIVATE_KEY')
decryptor = WiseDriveDecryptor(PRIVATE_KEY)

@app.route('/apiv2/webhook/obdreport/wisedrive/encrypted', methods=['POST'])
def receive_encrypted():
    body = request.get_json()
    
    try:
        scan_data = decryptor.decrypt(body['encryptedData'])
        
        # Process decrypted data
        license_plate = scan_data['license_plate']
        tracking_id = scan_data['tracking_id']
        
        return jsonify({
            'result': 'SUCCESS',
            'trackingId': tracking_id
        })
        
    except DecryptionError as e:
        return jsonify({'error': str(e)}), 400

if __name__ == '__main__':
    app.run(port=8082)
```

---

## 6. Key Management

### 6.1 Key Generation

#### Generate RSA-4096 Key Pair

```bash
# Generate private key
openssl genpkey -algorithm RSA -out wisedrive_private.pem -pkeyopt rsa_keygen_bits:4096

# Extract public key
openssl rsa -pubout -in wisedrive_private.pem -out wisedrive_public.pem
```

Or using Python:

```python
from wisedrive_decryption import KeyGenerator

public_pem, private_pem = KeyGenerator.generate_rsa_4096()

# Save keys
with open('wisedrive_public.pem', 'w') as f:
    f.write(public_pem)
    
with open('wisedrive_private.pem', 'w') as f:
    f.write(private_pem)
```

### 6.2 Key Storage Best Practices

| Environment | Recommended Storage |
|-------------|---------------------|
| **AWS** | AWS KMS, Secrets Manager |
| **GCP** | Cloud KMS, Secret Manager |
| **Azure** | Key Vault |
| **On-Premises** | HashiCorp Vault, HSM |
| **Development** | Environment variables (never in code) |

### 6.3 Key Rotation

The SDK supports key rotation via Key ID:

1. Generate new key pair
2. Update SDK with new public key (new Key ID)
3. Deploy backend with both old and new private keys
4. SDK submissions use new Key ID
5. After transition period, remove old private key

```java
// Backend support for multiple keys
Map<Integer, PrivateKey> keys = new HashMap<>();
keys.put(1, loadKey("/keys/wisedrive_private_v1.pem"));
keys.put(2, loadKey("/keys/wisedrive_private_v2.pem"));

// Decrypt with appropriate key
int keyId = header.getKeyId();
PrivateKey key = keys.get(keyId);
```

### 6.4 Embedding Public Key in SDK

Update `ObfuscatedKeyStore.kt`:

```kotlin
// Split your public key Base64 into 8 parts
private fun getWiseDriveKeyPart1(): String {
    return deobfuscate("MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA", 1)
}

private fun getWiseDriveKeyPart2(): String {
    return deobfuscate("YOUR_KEY_PART_2_HERE", 2)
}
// ... parts 3-8
```

---

## 7. API Specifications

### 7.1 SDK Public API

```kotlin
interface WiseDriveOBD2SDK {
    
    companion object {
        /**
         * Initialize SDK instance
         * @param context Application context
         * @param useMock Enable mock mode for testing
         * @return SDK instance
         */
        fun initialize(context: Context, useMock: Boolean = false): WiseDriveOBD2SDK
    }
    
    /**
     * Initialize with API key
     * @param apiKey Your WiseDrive API key
     * @return true if successful
     */
    suspend fun initializeWithKey(apiKey: String): Boolean
    
    /**
     * Discover Bluetooth OBD devices
     * @param onDeviceFound Callback for each device found
     * @param timeoutMs Discovery timeout in milliseconds
     */
    suspend fun discoverDevices(
        onDeviceFound: (BLEDevice) -> Unit,
        timeoutMs: Long = 10000
    )
    
    /**
     * Connect to OBD adapter
     * @param deviceId Bluetooth device ID (MAC address)
     */
    suspend fun connect(deviceId: String)
    
    /**
     * Disconnect from adapter
     */
    suspend fun disconnect()
    
    /**
     * Run full vehicle diagnostic scan
     * @param options Scan configuration
     * @return Scan report with DTCs and data
     */
    suspend fun runFullScan(options: ScanOptions): ScanReport
    
    /**
     * Stop ongoing scan
     */
    fun stopScan()
    
    /**
     * Submit scan report (encrypted automatically)
     * @param report Scan report to submit
     * @return true if submission successful
     */
    suspend fun submitReport(report: ScanReport): Boolean
    
    /**
     * Get encrypted report for client backend
     * @param report Scan report to encrypt
     * @return Base64-encoded encrypted blob
     */
    fun getEncryptedReportForClient(report: ScanReport): String
    
    /**
     * Set callback for analytics payload
     */
    fun setOnAnalyticsPayloadPrepared(callback: (String) -> Unit)
    
    /**
     * Set callback for submission result
     */
    fun setOnAnalyticsSubmissionResult(callback: (Boolean, String) -> Unit)
    
    /**
     * Release SDK resources
     */
    fun cleanup()
}
```

### 7.2 Data Models

```kotlin
data class ScanOptions(
    val registrationNumber: String,      // Required
    val trackingId: String,              // Required
    val manufacturer: String? = null,
    val year: Int? = null,
    val scanLiveData: Boolean = true,
    val onProgress: ((ScanStage) -> Unit)? = null
)

data class ScanReport(
    val scanId: String,
    val inspectionId: String?,
    val vehicle: VehicleInfo,
    val summary: ScanSummary,
    val diagnosticTroubleCodes: List<DTC>,
    val liveData: LiveDataReadings?,
    val moduleResponses: Map<String, ModuleResponse>,
    val scanDuration: Long,
    val timestamp: String,
    val connectionType: String
)

data class VehicleInfo(
    val manufacturer: String?,
    val year: Int?,
    val vin: String?,
    val protocol: String?
)

data class ScanSummary(
    val totalDTCs: Int,
    val faultyModules: List<String>,
    val nonFaultyModules: List<String>,
    val milStatus: Boolean
)

data class DTC(
    val code: String,
    val description: String,
    val module: String,
    val status: String,
    val severity: String,
    val category: String,
    val causes: List<String>,
    val symptoms: List<String>,
    val solutions: List<String>
)
```

---

## 8. Error Handling

### 8.1 SDK Exceptions

```kotlin
sealed class WiseDriveException : Exception() {
    
    class InitializationException(message: String) : WiseDriveException()
    class ConnectionException(message: String) : WiseDriveException()
    class ScanException(message: String) : WiseDriveException()
    class EncryptionException(message: String) : WiseDriveException()
    class NetworkException(message: String) : WiseDriveException()
    class SecurityException(message: String) : WiseDriveException()
}
```

### 8.2 Backend Decryption Exceptions

```java
public class DecryptionException extends Exception {
    
    // Specific error types identifiable from message:
    // - "Invalid magic bytes" - Not a valid encrypted payload
    // - "Key ID mismatch" - Wrong key version
    // - "RSA decryption failed" - Wrong private key
    // - "HMAC verification failed" - Payload tampered
    // - "AES-GCM decryption failed" - Corrupted ciphertext
}
```

### 8.3 Error Recovery

| Error | Recovery Action |
|-------|-----------------|
| `ConnectionException` | Retry connection, check Bluetooth |
| `ScanException` | Retry scan, check adapter connection |
| `EncryptionException` | Check key configuration |
| `NetworkException` | Retry with exponential backoff |
| `DecryptionException` | Log and reject, notify security |

---

## 9. Performance Considerations

### 9.1 Encryption Overhead

| Operation | Time (typical) |
|-----------|----------------|
| AES-256-GCM encryption (2KB) | ~1ms |
| RSA-4096 encryption | ~50ms |
| HMAC-SHA512 | ~1ms |
| **Total encryption** | ~55ms |
| Base64 encoding | ~1ms |

### 9.2 Payload Size

| Component | Size (bytes) |
|-----------|--------------|
| Header | 16 |
| RSA encrypted key | 512 |
| IV | 12 |
| Ciphertext overhead | +16 (GCM tag) |
| HMAC | 64 |
| **Total overhead** | ~604 bytes |

Example: 2KB JSON → 2.6KB encrypted

### 9.3 Network Optimization

- Retry with exponential backoff (2s, 4s, 8s, ... up to 30s)
- Maximum 10 retry attempts
- Automatic submission on scan completion
- Background retry queue

---

## 10. Security Testing Results

### 10.1 Red Team Assessment Summary

**Date:** March 2026  
**Result:** 13/14 attacks BLOCKED

| Attack | Vector | Result |
|--------|--------|--------|
| Brute Force AES-256 | Cryptographic | ✅ SECURE |
| Brute Force RSA-4096 | Cryptographic | ✅ SECURE |
| Wrong Key Decryption | Key Management | ✅ SECURE |
| Single Bit Tampering | Integrity | ✅ SECURE |
| Header Tampering | Integrity | ✅ SECURE |
| Ciphertext Tampering | Integrity | ✅ SECURE |
| HMAC Bypass | Integrity | ✅ SECURE |
| Key Extraction from Public | Cryptographic | ✅ SECURE |
| Known Plaintext | Cryptographic | ✅ SECURE |
| Timing Attack | Side Channel | ✅ SECURE |
| Padding Oracle | Side Channel | ✅ SECURE |
| IV Reuse | Implementation | ✅ SECURE |
| Memory Dump | Runtime | ✅ SECURE |
| Replay Attack | Protocol | ⚠️ APP-LEVEL |

### 10.2 Compliance

- [x] OWASP Mobile Top 10
- [x] NIST Cryptographic Standards
- [x] Industry best practices for key management

---

## Appendix A: File Structure

```
wisedrive-obd2-sdk-android/
├── sdk/
│   └── src/main/java/com/wisedrive/obd2/
│       ├── WiseDriveOBD2SDK.kt
│       ├── adapter/
│       │   ├── BluetoothClassicAdapter.kt
│       │   ├── BLEAdapter.kt
│       │   └── MockAdapter.kt
│       ├── models/
│       │   ├── ScanReport.kt
│       │   ├── DTC.kt
│       │   └── EncryptedPayload.kt
│       ├── network/
│       │   ├── SecureWiseDriveAnalytics.kt
│       │   └── ReportTransformer.kt
│       ├── protocol/
│       │   ├── DTCParser.kt
│       │   └── ELM327Protocol.kt
│       └── security/
│           ├── AdvancedEncryptionManager.kt
│           ├── ObfuscatedKeyStore.kt
│           ├── IntegrityChecker.kt
│           └── SDKSecurityManager.kt
├── sample/
│   └── src/main/java/.../MainActivity.kt
├── backend/
│   ├── wisedrive_decryption.py
│   ├── api_server.py
│   └── red_team_tests.py
├── backend-java/
│   └── src/main/java/com/wisedrive/inspection/
│       ├── security/WiseDriveDecryptor.java
│       ├── controllers/EncryptedObdWebhookController.java
│       └── dto/
├── docs/
│   ├── RELEASE_DOCUMENTATION.md
│   └── TECHNICAL_DOCUMENTATION.md
└── ENCRYPTION_ARCHITECTURE.md
```

---

## Appendix B: Contact & Support

- **SDK Support:** sdk-support@wisedrive.in
- **Security Issues:** security@wisedrive.in
- **Documentation:** https://docs.wisedrive.in/obd2-sdk
- **GitHub:** https://github.com/wisedrive/obd2-sdk-android

---

*Document Version: 2.0.0*  
*Last Updated: March 2026*
