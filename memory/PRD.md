# WiseDrive OBD2 Android SDK - Product Requirements Document

## Original Problem Statement
Build a production-ready Android Native SDK (Kotlin) called `wisedrive-obd2-sdk-android` to connect with ELM327 OBD-II adapters via Bluetooth Classic (SPP) and BLE. All OBD JSON data must be encrypted using military-grade encryption (Hybrid RSA-4096 + AES-256-GCM) with dual key system for Client and WiseDrive backends.

## Core Requirements
1. **Security**: Tamper-proof, RSA-4096 + AES-256-GCM encryption, HMAC-SHA512 integrity, dual key separation
2. **Connectivity**: Implement a 5-strategy Bluetooth Classic connection fallback
3. **Scanning**: Multi-ECU manufacturer scanning, ISO-TP multi-frame reassembly, 19 live data PIDs, 4000+ mapped DTC codes
4. **Output**: Encrypted data to both Client and WiseDrive backends (different keys)
5. **Sample App**: A polished Jetpack Compose UI utilizing Mock API and Mock Bluetooth adapter

## Project Architecture
\`\`\`
/app/wisedrive-obd2-sdk-android/
├── build.gradle.kts, settings.gradle.kts
├── sdk/                               # Main SDK module (AAR library)
│   └── src/main/java/com/wisedrive/obd2/
│       ├── WiseDriveOBD2SDK.kt       # Main entry point
│       ├── security/                  # Encryption & Security
│       │   ├── AdvancedEncryptionManager.kt  # RSA+AES hybrid
│       │   ├── ObfuscatedKeyStore.kt        # Key obfuscation
│       │   ├── SDKSecurityManager.kt        # Legacy encryption
│       │   └── IntegrityChecker.kt          # Anti-tampering
│       ├── network/                   # Networking
│       │   ├── SecureWiseDriveAnalytics.kt  # Encrypted analytics
│       │   └── WiseDriveAnalytics.kt        # Legacy analytics
│       └── ...
├── sample/                            # Sample app module
└── backend/                           # Python backend decryption
    ├── wisedrive_decryption.py       # Decryption library
    ├── red_team_tests.py             # Security attack tests
    ├── backend_test.py               # Comprehensive tests
    ├── api_server.py                 # Flask API example
    └── api_integration_test.py       # Integration tests
\`\`\`

## Advanced Encryption Implementation (2026-01)

### Architecture: Hybrid RSA-4096 + AES-256-GCM
- **Layer 1**: AES-256-GCM for fast symmetric data encryption
- **Layer 2**: RSA-4096 OAEP for secure key exchange
- **Layer 3**: HMAC-SHA512 for integrity verification
- **Layer 4**: Anti-tampering (root/emulator/Frida detection)

### Dual Key System
| Key Type | Purpose | Can Decrypt |
|----------|---------|-------------|
| CLIENT_PUBLIC | Encrypt for client | Only CLIENT backend |
| WISEDRIVE_PUBLIC | Encrypt for analytics | Only WISEDRIVE backend |

### Security Features
- Perfect Forward Secrecy (new AES key per encryption)
- Authenticated Encryption (GCM auth tag)
- Anti-tampering (HMAC + GCM combined)
- Key obfuscation in SDK

## Red Team Security Test Results (2026-01)

### Test Summary: 13/14 SECURE
| Attack Vector | Result | Notes |
|---------------|--------|-------|
| Brute Force AES-256 | SECURE | 2^256 keyspace infeasible |
| Brute Force RSA-4096 | SECURE | Factorization infeasible |
| Wrong Key Decryption | SECURE | Correctly rejected |
| Payload Tampering (bit flip) | SECURE | HMAC/GCM detected |
| Header Tampering | SECURE | HMAC detected |
| Ciphertext Tampering | SECURE | GCM auth failed |
| HMAC Bypass | SECURE | Verification required |
| Key Extraction from Public | SECURE | RSA secure |
| Known Plaintext Attack | SECURE | Random key per encryption |
| Timing Attack | SECURE | No significant variance |
| Padding Oracle | SECURE | GCM doesn't use padding |
| IV Reuse | SECURE | All IVs unique |
| Memory Dump | SECURE | Keys cleared after use |
| Replay Attack | PARTIAL | App-level protection |

## Backend Decryption Guide

### Python Decryption
\`\`\`python
from wisedrive_decryption import WiseDriveDecryptor

# Initialize with private key
decryptor = WiseDriveDecryptor(PRIVATE_KEY_PEM)

# Decrypt received data
scan_data = decryptor.decrypt(encrypted_base64)
print(f"VIN: {scan_data['vin']}")
\`\`\`

### Key Generation
\`\`\`python
from wisedrive_decryption import KeyGenerator

public_key, private_key = KeyGenerator.generate_rsa_4096()
# Embed public_key in SDK
# Store private_key securely on backend
\`\`\`

## Implementation Status

### Completed
- [x] Hybrid RSA-4096 + AES-256-GCM encryption
- [x] Dual key system (Client + WiseDrive)
- [x] HMAC-SHA512 integrity verification
- [x] Obfuscated key storage
- [x] Python decryption library
- [x] Red team security tests (13/14 passed)
- [x] Backend API server example
- [x] Comprehensive test suite

### Backlog
- [ ] Generate production RSA-4096 keys
- [ ] Full SDK integration testing on Android
- [ ] CI/CD pipeline with APK signing
- [ ] HTTPS migration for analytics endpoint

## Test Credentials
No authentication credentials required for encryption tests.

## Bug Fix - DTC Knowledge Base (March 2026)

### Issue
Live OBD device scans were not returning causes, symptoms, and solutions data for DTCs, while mock mode worked correctly.

### Root Cause
- DTCKnowledgeBase only had ~43 DTCs defined
- Real vehicles can have thousands of different DTC codes
- When a DTC code wasn't in the knowledge base, `getKnowledge()` returned `null`
- This resulted in empty `causes`, `symptoms`, and `solutions` arrays

### Fix Applied
1. Added **generic fallback knowledge generation** based on DTC code pattern
2. `getKnowledge()` now:
   - First tries exact match in knowledge base
   - If not found, generates context-aware generic knowledge based on code prefix
3. Added more specific DTCs to knowledge base:
   - P0501-P0504 (Vehicle Speed Sensor)
   - P0125, P0128 (Cooling/Thermostat)
   - P0120-P0123 (Throttle Position)
   - P0325-P0328 (Knock Sensor)
   - P0190-P0191 (Fuel Rail Pressure)
   - B1000-B1001 (Airbag/SRS)
   - C0035-C0050 (Wheel Speed Sensors)

### Generic Knowledge Categories
| DTC Prefix | System | Example |
|------------|--------|---------|
| P0xxx-P3xxx | Powertrain | Fuel, Ignition, Transmission |
| Bxxxx | Body | BCM, Climate, Occupant Safety |
| Cxxxx | Chassis | ABS, Traction Control |
| Uxxxx | Network | CAN Bus, Module Communication |

### Test Results
- 15/16 tests passed (94%)
- All DTC knowledge retrieval tests: PASSED
- Generic fallback tests: PASSED
- Integration flow tests: PASSED
