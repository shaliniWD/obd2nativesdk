# WiseDrive OBD2 Android SDK - Product Requirements Document

## Original Problem Statement
Build a production-ready Android Native SDK (Kotlin) called `wisedrive-obd2-sdk-android` to connect with ELM327 OBD-II adapters via Bluetooth Classic (SPP) and BLE. All OBD JSON data must be encrypted using military-grade encryption (Hybrid RSA-4096 + AES-256-GCM) with dual key system for Client and WiseDrive backends. No one should be able to reverse engineer how the SDK connects to OBD devices and extracts data.

## Core Requirements
1. **Security**: Tamper-proof, RSA-4096 + AES-256-GCM encryption, HMAC-SHA512 integrity, dual key separation
2. **Anti-Reverse-Engineering**: All OBD protocol logic, AT commands, ECU addresses encrypted at runtime
3. **Connectivity**: Implement a 5-strategy Bluetooth Classic connection fallback
4. **Scanning**: Multi-ECU manufacturer scanning, ISO-TP multi-frame reassembly, 19 live data PIDs, 4000+ mapped DTC codes
5. **Output**: Encrypted data to WiseDrive backend, plain JSON to client backend
6. **SDK Publishing**: Private Maven Repository via JFrog Artifactory (wisedrive.jfrog.io)

## Project Architecture
```
/app/wisedrive-obd2-sdk-android/
├── sdk/
│   └── src/main/java/com/wisedrive/obd2/
│       ├── security/
│       │   ├── AdvancedEncryptionManager.kt   # RSA+AES hybrid encryption
│       │   ├── ObfuscatedKeyStore.kt          # Key obfuscation
│       │   ├── StringProtector.kt             # Runtime string encryption (XOR+SHA-256)
│       │   ├── ObfuscatedProtocol.kt          # Encrypted AT commands & OBD constants
│       │   ├── ObfuscatedECUConfig.kt         # Encrypted manufacturer ECU configs
│       │   ├── IntegrityChecker.kt            # Anti-debug/Frida/Xposed/Magisk/hook detection
│       │   └── SDKSecurityManager.kt          # Legacy encryption
│       ├── protocol/                          # ELM327 protocol (uses obfuscated strings)
│       ├── network/                           # Dual submission (encrypted + plain)
│       ├── models/, constants/, adapter/
│   ├── build.gradle.kts                       # JFrog maven-publish config
│   ├── proguard-rules.pro                     # Aggressive obfuscation rules
│   └── proguard-dictionary.txt                # Confusing name dictionary
├── backend/                                   # Python tests & decryption
├── backend-java/                              # Java Spring Boot decryption
├── docs/                                      # Documentation
└── .github/workflows/                         # CI/CD (planned)
```

## Implementation Status

### Completed
- [x] Hybrid RSA-4096 + AES-256-GCM encryption architecture
- [x] Dual key system (Client + WiseDrive)
- [x] **Anti-Reverse-Engineering Protection (April 2026)**
  - [x] StringProtector: XOR encryption with SHA-256 derived key from 3 scattered seeds
  - [x] ObfuscatedProtocol: All 17 AT commands, 11 OBD modes, 6 error strings encrypted
  - [x] ObfuscatedECUConfig: 65+ ECU addresses across 23 manufacturers encrypted
  - [x] ELM327Service migrated to use ObfuscatedProtocol (no plaintext commands)
  - [x] ManufacturerECUs delegates to ObfuscatedECUConfig
  - [x] Enhanced IntegrityChecker: debugger, Frida (4 methods), Xposed, Magisk, hook library detection
  - [x] Aggressive ProGuard: class flattening, log stripping, dictionary naming, MockAdapter exclusion
  - [x] 36/36 attack vectors blocked (12 anti-RE + 10 hack simulation + 14 red team)
- [x] JFrog Artifactory Maven publishing (wisedrive.jfrog.io)
- [x] Python & Java decryption modules
- [x] DTC Knowledge Base with generic fallback
- [x] Comprehensive QA: 46/46 encryption tests, 12/12 anti-RE tests, 10/10 hack tests

### In Progress / Next
- [ ] Client plain JSON submission (modify SecureWiseDriveAnalytics.kt)
- [ ] SDK Error Logging to internal API (encrypted)
- [ ] GitHub Actions CI/CD (snapshot + release workflows)
- [ ] Client Integration Document

### Backlog
- [ ] React Native Bridge
- [ ] iOS SDK + iOS React Native Bridge
- [ ] Certificate Pinning
- [ ] Replace test keys with production RSA-4096 keys
- [ ] Native C/NDK encryption (Level 3 protection)

## Key API Endpoints
- POST `http://faircar.in:82/apiv2/webhook/obdreport/wisedrive/encrypted?license_plate={plate}`
- Client configurable endpoint (plain JSON)

## Security Assessment (April 2026)
- **Encryption**: RSA-4096 + AES-256-GCM + HMAC-SHA512 — 0 vulnerabilities, 14/14 attacks blocked
- **Anti-RE**: StringProtector + ObfuscatedProtocol — 0 vulnerabilities, 22/22 attacks blocked  
- **Anti-Tampering**: IntegrityChecker — debugger, Frida, Xposed, Magisk, hook detection
- **Obfuscation**: ProGuard aggressive + dictionary + log stripping + class flattening

## JFrog Publishing
- User: kalyan@wisedrive.in
- Snapshots: wisedrive-sdk-snapshots
- Releases: wisedrive-sdk-releases
