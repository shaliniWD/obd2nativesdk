# WiseDrive OBD2 Android SDK - Product Requirements Document

## Original Problem Statement
Build a production-ready Android Native SDK (Kotlin) called `wisedrive-obd2-sdk-android` to connect with ELM327 OBD-II adapters via Bluetooth Classic (SPP) and BLE. It must strictly port an existing React Native SDK's protocol logic, AT command sequences, and byte-parsing.

## Core Requirements
1. **Security**: Tamper-proof, AES-256-GCM encryption, root/emulator/debug detection, in-memory keys fetched from backend, strict ProGuard obfuscation. Host apps should only receive opaque encrypted payloads.
2. **Connectivity**: Implement a 5-strategy Bluetooth Classic connection fallback.
3. **Scanning**: Multi-ECU manufacturer scanning, ISO-TP multi-frame reassembly, 19 live data PIDs, 4000+ mapped DTC codes.
4. **Output**: Exact JSON payload transformation matching legacy API expectations.
5. **Sample App**: A polished Jetpack Compose UI utilizing a Mock API and Mock Bluetooth adapter for testing.

## Project Architecture
```
/app/wisedrive-obd2-sdk-android/
├── build.gradle.kts, settings.gradle.kts, gradle.properties
├── sdk/                               # Main SDK module (AAR library)
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   ├── src/main/AndroidManifest.xml
│   ├── src/main/java/com/wisedrive/obd2/
│   │   ├── WiseDriveOBD2SDK.kt        # Main entry point
│   │   ├── models/                    # Data models
│   │   ├── constants/                 # DTC databases (4000+ codes), ECUs, PIDs
│   │   ├── adapter/                   # Bluetooth adapters (Classic, BLE, Mock)
│   │   ├── protocol/                  # ELM327 protocol, parsers
│   │   ├── security/                  # Encryption, integrity checks
│   │   ├── network/                   # API client, report transformer
│   │   └── util/                      # Logger
│   └── src/test/                      # Unit tests
└── sample/                            # Sample app module
    └── src/main/.../MainActivity.kt   # Jetpack Compose UI
```

## Key Technical Components
- **Kotlin** with Coroutines for async operations
- **Jetpack Compose** for sample app UI
- **Bluetooth Classic (SPP/RFCOMM)** - Primary adapter with 5 fallback strategies
- **BLE GATT** - Secondary adapter for newer ELM327 devices
- **AES-256-GCM Encryption** - All scan data encrypted before leaving SDK
- **ISO-15031-6** - DTC parsing standard
- **UDS Service 0x19** - Enhanced diagnostics
- **ISO-TP (ISO 15765-2)** - Multi-frame message reassembly

## Implementation Status

### Completed (Session: 2025-03-22)
- [x] Project scaffolding (Gradle, manifests, proguard)
- [x] SDK Data Models (BLEDevice, ScanReport, EncryptedPayload, etc.)
- [x] Protocol Engine (ELM327Service, DTCParser, LiveDataParser, ISOTPAssembler)
- [x] Security Layer (SDKSecurityManager, IntegrityChecker)
- [x] Constants & DTC Database (4000+ codes split into parts)
- [x] Manufacturer ECU definitions
- [x] Adapter implementations (BluetoothClassicAdapter, BLEAdapter, MockAdapter)
- [x] API Client with Mock support
- [x] Sample App scaffolding (Jetpack Compose)
- [x] Unit tests (28 tests passing)
- [x] Kotlin compilation successful for SDK module
- [x] Fixed all compilation errors from bulk file generation

### Test Results (2025-03-22)
- **MockAdapterTest**: 11/11 passed
- **MockAPIClientTest**: 4/4 passed
- **ISOTPAssemblerTest**: 9/9 passed
- **DTCParserTest**: 10/10 passed
- **LiveDataParserTest**: 10/10 passed
- **SDKSecurityManagerTest**: 3/3 passed
- **DTCDescriptionsTest**: 5/5 passed
- **Total**: 52/52 unit tests passing

## Known Limitations
1. **ARM64 Build Environment**: This preview environment runs on ARM64 Linux, which is incompatible with Android SDK build tools (AAPT2). Full APK builds require x86_64 environment.
2. **Mock Mode Only**: Without physical hardware, testing is limited to MockAdapter.

## API Reference

### Initialization
```kotlin
val sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
sdk.initializeWithKey(apiKey, baseUrl)
```

### Device Discovery
```kotlin
sdk.discoverDevices { device ->
    // BLEDevice(id, name, rssi, isConnectable)
}
```

### Connection & Scan
```kotlin
sdk.connect(deviceId)
val encryptedResult = sdk.runFullScan(ScanOptions(
    orderId = "12345",
    manufacturer = "hyundai",
    onProgress = { stage -> /* ScanStage updates */ }
))
sdk.submitReport(encryptedResult)
```

## Backlog / Future Tasks
- [ ] ProGuard/R8 obfuscation verification
- [ ] Real API integration (pending backend endpoints)
- [ ] Full APK build on x86_64 environment
- [ ] Android Instrumented Tests
- [ ] CI/CD pipeline setup

## Security Considerations
- All encryption keys fetched from backend at runtime
- Keys stored only in memory, never persisted
- IntegrityChecker validates environment (root, emulator, debugger)
- ProGuard rules configured for obfuscation
- Host apps receive only encrypted payloads
