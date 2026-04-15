# WiseDrive OBD2 Android SDK - Product Requirements Document

## Original Problem Statement
Build a production-ready Android Native SDK (Kotlin) called `wisedrive-obd2-sdk-android` to connect with ELM327 OBD-II adapters via Bluetooth Classic (SPP) and BLE. All OBD JSON data must be encrypted using military-grade encryption (Hybrid RSA-4096 + AES-256-GCM) with dual key system for Client and WiseDrive backends.

## Core Requirements
1. **Security**: Tamper-proof, RSA-4096 + AES-256-GCM encryption, HMAC-SHA512 integrity, dual key separation
2. **Connectivity**: Implement a 5-strategy Bluetooth Classic connection fallback
3. **Scanning**: Multi-ECU manufacturer scanning, ISO-TP multi-frame reassembly, 19 live data PIDs, 4000+ mapped DTC codes
4. **Output**: Encrypted data to both Client and WiseDrive backends (different keys)
5. **Sample App**: A polished Jetpack Compose UI utilizing Mock API and Mock Bluetooth adapter
6. **SDK Publishing**: Private Maven Repository via JFrog Artifactory (wisedrive.jfrog.io)

## Project Architecture
```
/app/wisedrive-obd2-sdk-android/
├── build.gradle.kts, settings.gradle.kts
├── sdk/                               # Main SDK module (AAR library)
│   ├── build.gradle.kts              # JFrog maven-publish configuration
│   └── src/main/java/com/wisedrive/obd2/
│       ├── WiseDriveOBD2SDK.kt       # Main entry point
│       ├── security/                  # Encryption & Security
│       │   ├── AdvancedEncryptionManager.kt  # RSA+AES hybrid
│       │   ├── ObfuscatedKeyStore.kt        # Key obfuscation
│       │   ├── SDKSecurityManager.kt        # Legacy encryption
│       │   └── IntegrityChecker.kt          # Anti-tampering
│       ├── network/                   # Networking
│       │   ├── SecureWiseDriveAnalytics.kt  # Dual submission (encrypted)
│       │   └── WiseDriveAnalytics.kt        # Legacy analytics
│       ├── models/                    # Data models
│       ├── protocol/                  # ELM327 OBD-II protocol
│       ├── constants/                 # DTC descriptions & knowledge base
│       └── adapter/                   # Bluetooth adapters (Classic/BLE/Mock)
├── sample/                            # Jetpack Compose Sample App
├── backend/                           # Python backend decryption & tests
│   ├── wisedrive_decryption.py       # Decryption library
│   ├── red_team_tests.py             # 14 security attack tests
│   ├── backend_test.py               # Comprehensive test suite
│   └── api_server.py                 # Flask API example
├── backend-java/                      # Java Spring Boot decryption
├── test_files/                        # Test RSA key pairs
├── docs/                              # Documentation
└── PUBLISHING.md                      # JFrog publishing guide
```

## Implementation Status

### Completed (2026-01 to 2026-04)
- [x] Hybrid RSA-4096 + AES-256-GCM encryption architecture
- [x] Dual key system (Client + WiseDrive separate encryption)
- [x] HMAC-SHA512 integrity verification
- [x] Obfuscated key storage in SDK
- [x] Python decryption library with full test suite
- [x] Java (Spring Boot) decryption module
- [x] Red team security tests (14 attack vectors, 13/14 SECURE, 1 PARTIAL for replay - expected)
- [x] Dual submission architecture (WiseDrive + Client endpoints)
- [x] Internal API at faircar.in:82 with license_plate URL parameter
- [x] DTC Knowledge Base with generic fallback for unknown codes
- [x] Flask API server with replay protection
- [x] Test key pair generation and export
- [x] JFrog Artifactory Maven publishing configuration (wisedrive.jfrog.io)
  - Snapshots: wisedrive-sdk-snapshots
  - Releases: wisedrive-sdk-releases
  - Gradle tasks verified, Kotlin compilation passes
- [x] Comprehensive QA: 46/46 pytest tests, 9/9 backend tests, 18/18 API tests passed

### Backlog
- [ ] P1: React Native Bridge (Android wrapper proposed, awaiting user go-ahead)
- [ ] P1: Test with live ELM327 Bluetooth hardware (user action)
- [ ] P1: Run actual `./gradlew :sdk:publish` on x86_64 machine (user action - ARM64 cloud can't build AAPT2)
- [ ] P2: iOS SDK counterpart
- [ ] P2: iOS React Native Bridge
- [ ] P2: Certificate Pinning for API client
- [ ] P2: Replace test_public_key.pem with production RSA-4096 keys
- [ ] P3: CI/CD pipeline with APK signing
- [ ] P3: HTTPS migration for analytics endpoint

## Key API Endpoints
- POST `http://faircar.in:82/apiv2/webhook/obdreport/wisedrive/encrypted?license_plate={plate}`
- Client configurable endpoint (set in SDKConfig)

## Security Assessment (QA Verified April 2026)
- Overall: SECURE
- 14 attack vectors tested, 0 vulnerabilities found
- Encryption: RSA-4096 + AES-256-GCM + HMAC-SHA512
- Perfect Forward Secrecy, Authenticated Encryption, Key Separation

## JFrog Publishing Configuration
- User: kalyan@wisedrive.in
- Repositories: wisedrive-sdk-snapshots (dev), wisedrive-sdk-releases (prod)
- Domain: wisedrive.jfrog.io
- Credentials in local.properties (not committed to git)
