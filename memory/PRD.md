# WiseDrive OBD2 Android SDK - Product Requirements Document

## Original Problem Statement
Android Native SDK (Kotlin) for OBD-II vehicle diagnostics with military-grade encryption, anti-reverse-engineering, and private Maven publishing.

## Implementation Status - ALL COMPLETE

### Core SDK
- [x] Hybrid RSA-4096 + AES-256-GCM + HMAC-SHA512 encryption
- [x] Anti-Reverse-Engineering (StringProtector, ObfuscatedProtocol, ObfuscatedECUConfig)
- [x] Enhanced IntegrityChecker (debugger, Frida, Xposed, Magisk, hook detection)
- [x] Aggressive ProGuard (174 obfuscated classes, 0 plaintext in binary)
- [x] Dual Submission: WiseDrive=encrypted(JWT), Client=plain JSON
- [x] SDK Error Logging (encrypted, queued, batched)
- [x] 23 manufacturers, 4000+ DTCs, 19 live PIDs

### Publishing & CI/CD
- [x] JFrog Artifactory: Published v2.0.0 to BOTH snapshots AND releases
- [x] Client credentials verified (obdsdktest can download from releases)
- [x] GitHub Actions CI/CD (snapshot auto-push + release on tag/manual)

### Documentation
- [x] Client Integration Guide DOCX (no encryption/internal API mentions)
- [x] Internal Technical Documentation DOCX (full architecture, troubleshooting, security audit)

### QA Sign-Off (April 15, 2026)
- 51/51 tests passed, 0 failures
- 14/14 red team attacks blocked
- 12/12 anti-RE tests passed
- AAR integrity verified: 0 plaintext protocol strings

## Endpoints
- Internal: POST https://faircar.in:9768/api/obd/encrypted (JWT Bearer)
- Auth: POST https://faircar.in:9768/api/auth/login (partner_api)
- Client: Configurable via SDKConfig.clientEndpoint (plain JSON)

## JFrog Repositories
- Snapshots: wisedrive-sdk-snapshots (dev)
- Releases: wisedrive-sdk-releases (prod) - v2.0.0 PUBLISHED
- Client test user: obdsdktest

## Backlog
- [ ] React Native Bridge
- [ ] iOS SDK
- [ ] Certificate Pinning
- [ ] Replace test keys with production RSA-4096 keys
- [ ] Native C/NDK encryption (Level 3)
