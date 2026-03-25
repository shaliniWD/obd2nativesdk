# WiseDrive OBD2 Android SDK - Product Requirements Document

## Original Problem Statement
Build a production-ready Android Native SDK (Kotlin) called `wisedrive-obd2-sdk-android` to connect with ELM327 OBD-II adapters via Bluetooth Classic (SPP) and BLE. It must strictly port an existing React Native SDK's protocol logic, AT command sequences, and byte-parsing.

## Core Requirements
1. **Security**: Tamper-proof, AES-256-GCM encryption for WiseDrive analytics, root/emulator/debug detection, in-memory keys fetched from backend, strict ProGuard obfuscation.
2. **Connectivity**: Implement a 5-strategy Bluetooth Classic connection fallback.
3. **Scanning**: Multi-ECU manufacturer scanning, ISO-TP multi-frame reassembly, 19 live data PIDs, 4000+ mapped DTC codes.
4. **Output**: Plain JSON `ScanReport` to client apps, encrypted data to WiseDrive analytics.
5. **Sample App**: A polished Jetpack Compose UI utilizing a Mock API and Mock Bluetooth adapter for testing.
6. **Registration Number**: Mandatory field for vehicle identification (supports all global formats).

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
│   │   ├── network/                   # API client, report transformer, WiseDriveAnalytics
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
- **AES-256-GCM Encryption** - Scan data encrypted for WiseDrive analytics only
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
- [x] Unit tests (52 tests passing)
- [x] Kotlin compilation successful for SDK module

### Updated (Session: 2026-01)
- [x] **Registration Number Mandatory**: Added `registrationNumber` as required field in `ScanOptions`
- [x] **Dual Data Flow**: 
  - Client apps receive plain `ScanReport` JSON
  - WiseDrive analytics receives encrypted data (AES-256-GCM)
- [x] **WiseDrive Analytics**: New `WiseDriveAnalytics` class for automatic background submission
  - Endpoint: `http://164.52.213.170:82/apiv2/webhook/obdreport/wisedrive`
  - Silent retry with exponential backoff until `submitReport()` is called
- [x] **Updated Sample App**: 
  - Added Registration Number input field (mandatory)
  - Updated to display plain `ScanReport` instead of encrypted payload
  - Shows analytics submission status
- [x] **Updated README**: Documented new API with `registrationNumber`

### Test Results (2025-03-22)
- **MockAdapterTest**: 11/11 passed
- **MockAPIClientTest**: 4/4 passed
- **ISOTPAssemblerTest**: 9/9 passed
- **DTCParserTest**: 10/10 passed
- **LiveDataParserTest**: 10/10 passed
- **SDKSecurityManagerTest**: 3/3 passed
- **DTCDescriptionsTest**: 5/5 passed
- **Total**: 52/52 unit tests passing

## Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         runFullScan()                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      OBD Scan Process                           │
│  (ELM327 Init → VIN → MIL → DTCs → ECU Modules → Live Data)    │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────────────┐
│    Plain ScanReport     │     │     WiseDrive Analytics         │
│   (returned to client)  │     │  (encrypted, background send)   │
└─────────────────────────┘     └─────────────────────────────────┘
              │                               │
              ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────────────┐
│   Client's Backend      │     │  http://164.52.213.170:82/      │
│    (their choice)       │     │  apiv2/webhook/obdreport/       │
└─────────────────────────┘     │  wisedrive                      │
                                └─────────────────────────────────┘
```

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

// registrationNumber is MANDATORY
val scanReport = sdk.runFullScan(ScanOptions(
    registrationNumber = "MH12AB1234",  // Required
    manufacturer = "hyundai",
    year = 2022,
    onProgress = { stage -> /* ScanStage updates */ }
))

// Client receives plain ScanReport
println("DTCs: ${scanReport.summary.totalDTCs}")
println("VIN: ${scanReport.vehicle.vin}")

// Confirm submission (ensures analytics delivered)
sdk.submitReport(scanReport)
```

### Analytics Payload Format
```json
{
  "license_plate": "MH12AB1234",
  "report_url": "https://example.com/report.pdf",
  "car_company": "Hyundai",
  "status": 1,
  "time": "2026-01-15T10:30:00.000Z",
  "mechanic_name": "Wisedrive Utils",
  "mechanic_email": "utils@wisedrive.in",
  "vin": "KMHXX00XXXX000000",
  "mil_status": true,
  "scan_ended": "automatic_success",
  "faulty_modules": ["Engine Control Module (ECM)", "ABS/ESP Control Module"],
  "non_faulty_modules": ["Engine", "Transmission", "BCM", ...],
  "code_details": [
    {
      "dtc": "P0503",
      "meaning": "Vehicle Speed Sensor A Circuit Intermittent/Erratic/High",
      "module": "Engine Control Module (ECM)",
      "status": "Confirmed",
      "descriptions": ["..."],
      "causes": ["Sensor malfunction", ...],
      "solutions": ["Diagnose with scanner", ...],
      "symptoms": ["Check engine light", ...]
    }
  ],
  "battery_voltage": 14.02
}
```

## Known Limitations
1. **ARM64 Build Environment**: This preview environment runs on ARM64 Linux, which is incompatible with Android SDK AAPT2 daemon mode. Full APK builds require x86_64 environment.
2. **Mock Mode Only**: Without physical hardware, testing is limited to MockAdapter.

## CI/CD Setup
GitHub Actions workflow (`.github/workflows/android-ci.yml`) provides:
- **Build Job**: Compiles SDK AAR and Sample APK on x86_64 Ubuntu
- **Instrumented Tests Job**: Runs UI and SDK integration tests on Android emulator
- **Lint Job**: Android lint analysis
- **Artifacts**: sdk-release.aar, sample-debug.apk, test-results

## Backlog / Future Tasks
- [ ] Push to GitHub and enable Actions for full APK build
- [ ] Run instrumented tests in CI (MainActivityTest, SDKIntegrationTest)
- [ ] ProGuard/R8 obfuscation verification
- [ ] Real API integration (pending backend endpoints)
- [ ] Android Instrumented Tests on physical devices
- [ ] CI/CD pipeline with release signing
- [ ] Add analytics submission status callback

## Security Considerations
- All encryption keys fetched from backend at runtime
- Keys stored only in memory, never persisted
- IntegrityChecker validates environment (root, emulator, debugger)
- ProGuard rules configured for obfuscation
- Client apps receive plain JSON (for their use)
- WiseDrive analytics receives AES-256-GCM encrypted data
