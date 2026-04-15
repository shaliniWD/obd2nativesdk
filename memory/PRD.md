# WiseDrive OBD2 Android SDK - Product Requirements Document

## Original Problem Statement
Build a production-ready Android Native SDK (Kotlin) for OBD-II vehicle diagnostics via Bluetooth, with military-grade encryption (RSA-4096 + AES-256-GCM), anti-reverse-engineering protection, dual submission architecture, and private Maven publishing.

## Architecture
```
/app/wisedrive-obd2-sdk-android/
├── sdk/src/main/java/com/wisedrive/obd2/
│   ├── security/          # Encryption, StringProtector, ObfuscatedProtocol, IntegrityChecker
│   ├── network/           # SecureWiseDriveAnalytics (dual submit), SDKErrorReporter
│   ├── protocol/          # ELM327, DTCParser, ISOTPAssembler, LiveDataParser
│   ├── models/, constants/, adapter/
├── .github/workflows/     # CI/CD (snapshot + release)
├── backend/               # Python tests & decryption
├── backend-java/          # Java decryption
├── docs/                  # CLIENT_INTEGRATION_GUIDE.md, etc.
```

## Implementation Status

### Completed
- [x] Hybrid RSA-4096 + AES-256-GCM + HMAC-SHA512 encryption
- [x] Anti-Reverse-Engineering (StringProtector, ObfuscatedProtocol, ObfuscatedECUConfig)
- [x] Enhanced IntegrityChecker (debugger, Frida, Xposed, Magisk, hook detection)
- [x] Aggressive ProGuard obfuscation
- [x] **Dual Submission: WiseDrive=encrypted, Client=plain JSON**
- [x] **SDK Error Logging** (encrypted, queued, batched to internal API)
- [x] **GitHub Actions CI/CD** (snapshot auto-push + release on tag/manual)
- [x] **Client Integration Guide** (docs/CLIENT_INTEGRATION_GUIDE.md)
- [x] JWT Bearer token auth for internal API (faircar.in:9768)
- [x] Token auto-refresh on expiry
- [x] JFrog Artifactory publishing config (wisedrive.jfrog.io)
- [x] 23+ manufacturer ECU configs, 4000+ DTC codes, 19 live PIDs

### Test Results
- 46/46 encryption tests, 12/12 anti-RE tests, 10/10 hack tests, 14/14 red team
- 47/47 feature tests (plain JSON, error reporter, CI/CD, client guide)
- Kotlin compilation: BUILD SUCCESSFUL

### Backlog
- [ ] React Native Bridge
- [ ] iOS SDK + iOS RN Bridge
- [ ] Certificate Pinning
- [ ] Replace test keys with production RSA-4096 keys
- [ ] Native C/NDK encryption (Level 3)
- [ ] Run ./gradlew :sdk:publish on x86_64 machine

## Key Endpoints
- Internal: POST https://faircar.in:9768/api/obd/encrypted?license_plate=XX (JWT auth)
- Auth: POST https://faircar.in:9768/api/auth/login (partner_api)
- Client: Configurable via SDKConfig.clientEndpoint (plain JSON)

## JFrog
- User: kalyan@wisedrive.in
- Snapshots: wisedrive-sdk-snapshots (auto on push)
- Releases: wisedrive-sdk-releases (on tag/manual)
