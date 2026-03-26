# WiseDrive OBD2 SDK for Android

A production-ready Android Native SDK (Kotlin) for connecting to ELM327 OBD-II adapters via Bluetooth Classic (SPP) and BLE, scanning vehicle diagnostic trouble codes (DTCs), reading live sensor data, and returning plain scan reports with automatic analytics submission.

## Features

- **Bluetooth Classic SPP** - Primary connection method with 5 fallback strategies for maximum device compatibility
- **BLE Support** - Secondary adapter for BLE-only ELM327 devices
- **Multi-ECU Scanning** - Scan across all vehicle ECU modules (Engine, Transmission, ABS, Airbag, BCM, etc.)
- **20+ Manufacturer Configs** - Pre-configured ECU addresses for major manufacturers (Tata, Hyundai, Toyota, Ford, BMW, etc.)
- **4000+ DTC Descriptions** - Comprehensive database of diagnostic trouble codes with descriptions
- **Knowledge Base** - Causes, symptoms, and solutions for common DTCs
- **19 Live Data PIDs** - Real-time sensor data (RPM, Speed, Temperature, Fuel, etc.)
- **Automatic Analytics** - Background submission to WiseDrive analytics with retry mechanism
- **Anti-Tampering** - Root detection, Frida detection, emulator detection

## Installation

### Gradle (Module)

```kotlin
dependencies {
    implementation("com.wisedrive:obd2-sdk:1.0.0")
}
```

### Local AAR

```kotlin
dependencies {
    implementation(files("libs/wisedrive-obd2-sdk.aar"))
}
```

## Quick Start

```kotlin
import com.wisedrive.obd2.WiseDriveOBD2SDK
import com.wisedrive.obd2.models.*

// 1. Initialize SDK
val sdk = WiseDriveOBD2SDK.initialize(context, useMock = false)
sdk.setLoggingEnabled(true)

// 2. Initialize with encryption key from backend
val success = sdk.initializeWithKey("your-api-key", "https://api.wisedrive.com")

// 3. Discover OBD devices
sdk.discoverDevices(
    onDeviceFound = { device ->
        println("Found: ${device.name} (${device.id})")
    },
    timeoutMs = 8000
)

// 4. Connect to device
sdk.connect("00:11:22:33:44:55")

// 5. Run full scan (BOTH fields are MANDATORY)
val scanReport = sdk.runFullScan(ScanOptions(
    registrationNumber = "MH12AB1234",  // Required - Vehicle registration/license plate
    trackingId = "ORD6894331",          // Required - WiseDrive Tracking/Order ID
    manufacturer = "hyundai",
    year = 2022,
    onProgress = { stage ->
        println("Stage: ${stage.label} - ${stage.status}")
    }
))

// 6. Access plain scan report
println("Total DTCs: ${scanReport.summary.totalDTCs}")
println("VIN: ${scanReport.vehicle.vin}")

// 7. Submit report (confirms analytics submission)
sdk.submitReport(scanReport)

// 8. Disconnect
sdk.disconnect()
```

## Important Notes

### Mandatory Fields (ScanOptions)

Both fields are **MANDATORY** in `ScanOptions`:

| Field | Description | Example |
|-------|-------------|---------|
| `registrationNumber` | Vehicle registration/license plate number | `"MH12AB1234"`, `"KA01XY9999"` |
| `trackingId` | WiseDrive Tracking/Order ID | `"ORD6894331"`, `"WD2025ABC123"` |

### Analytics Payload

The SDK sends the following data to WiseDrive analytics:

```json
{
  "license_plate": "MH12AB1234",      // From registrationNumber
  "tracking_id": "ORD6894331",        // From trackingId  
  "report_url": "https://...",
  "car_company": "Hyundai",
  "vin": "KMHXX00XXXX000000",
  "mil_status": true,
  "faulty_modules": ["Engine", "ABS"],
  "non_faulty_modules": ["Transmission", ...],
  "code_details": [...],
  "battery_voltage": 14.02
}
```

### Data Flow
1. **Client App** receives plain `ScanReport` JSON for immediate use
2. **WiseDrive Analytics** automatically receives plain JSON to analytics endpoint
3. Analytics is sent in background with automatic retry (exponential backoff)
4. `submitReport()` ensures analytics delivery before confirmation

### Network Security Configuration

The SDK includes a network security config to allow HTTP communication to the WiseDrive analytics endpoint. This is already configured in the SDK and sample app manifests.

If you're integrating the SDK into your app, ensure your `AndroidManifest.xml` includes:

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

## Scan Stages

The `runFullScan()` method executes these stages in order:

1. **INIT** - Initialize ELM327 with 13 AT commands
2. **VIN** - Fetch Vehicle Identification Number
3. **MIL_STATUS** - Check Malfunction Indicator Lamp status
4. **DTC_STORED** - Scan stored DTCs (Mode 03)
5. **DTC_PENDING** - Scan pending DTCs (Mode 07)
6. **DTC_PERMANENT** - Scan permanent DTCs (Mode 0A)
7. **MANUFACTURER** - Scan manufacturer-specific ECU modules
8. **LIVE_DATA** - Read live sensor data
9. **COMPLETE** - Scan finished

## Permissions

Add to AndroidManifest.xml:

```xml
<!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Android <12 -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />

<!-- Internet for analytics -->
<uses-permission android:name="android.permission.INTERNET" />

<uses-feature android:name="android.hardware.bluetooth" android:required="true" />
```

Request permissions at runtime:

```kotlin
sdk.requestPermissions(activity)
```

## Advanced Usage

### Analytics Callbacks

```kotlin
// Get notified when analytics payload is prepared
sdk.setOnAnalyticsPayloadPrepared { json ->
    println("Payload: $json")
}

// Get notified of submission result
sdk.setOnAnalyticsSubmissionResult { success, response ->
    if (success) {
        println("Analytics submitted: $response")
    } else {
        println("Analytics failed: $response")
    }
}

// Check analytics status
val isSubmitted = sdk.isAnalyticsSubmitted()
val lastPayload = sdk.getLastAnalyticsPayloadJson()
val lastResponse = sdk.getLastAnalyticsResponse()
```

### Direct ELM327 Access

```kotlin
// Read VIN
val vinResult = sdk.fetchVIN()
println("VIN: ${vinResult.vin}")

// Read MIL status
val milStatus = sdk.readMILStatus()
println("MIL: ${if (milStatus.milOn) "ON" else "OFF"}, DTCs: ${milStatus.dtcCount}")

// Scan specific manufacturer modules
val mfgResult = sdk.scanManufacturerModules("toyota") { progress ->
    println("Scanning ${progress.component}...")
}

// Read enhanced DTCs via UDS
val enhancedDTCs = sdk.readEnhancedDTCs("7E0") // Engine ECU
```

### Custom Manufacturer Configuration

The SDK supports 20+ manufacturers out of the box:
- **Indian**: Tata, Mahindra, Maruti Suzuki
- **Korean**: Hyundai, Kia
- **Japanese**: Toyota, Honda, Nissan, Mitsubishi
- **German**: VW, Audi, BMW, Mercedes-Benz
- **American**: Ford, GM, Chrysler
- **Others**: Volvo, Jaguar Land Rover, Renault, Peugeot, Fiat, SEAT, Skoda

## Testing

### Mock Mode

Enable mock mode for testing without physical hardware:

```kotlin
val sdk = WiseDriveOBD2SDK.initialize(context, useMock = true)
```

### Test Tracking ID

Use `ORD6894331` as tracking ID for testing against the live WiseDrive analytics endpoint.

### Unit Tests

The SDK includes comprehensive test suites:

- **WhiteBox Tests**: Internal logic validation
  - `ScanOptionsTest` - Field validation and formats
  - `APIPayloadTest` - Payload structure tests
  - `ReportTransformerTest` - Transformation logic
  - `DTCParserTest`, `LiveDataParserTest`, etc.

- **BlackBox Tests**: User-facing API testing
  - `MainActivityTest` - UI integration tests
  - `SDKIntegrationTest` - SDK API tests

Run tests:
```bash
./gradlew :sdk:test           # Unit tests
./gradlew :sample:connectedAndroidTest  # Instrumented tests
```

## Security

### Anti-Tampering

The SDK includes checks for:
- Debug builds
- Emulator environment
- Root access
- Frida instrumentation
- Xposed framework
- APK repackaging

## ProGuard

The SDK is already obfuscated. For consuming apps, add:

```proguard
-keep public class com.wisedrive.obd2.WiseDriveOBD2SDK { *; }
-keep public class com.wisedrive.obd2.models.** { *; }
```

## Requirements

- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **Kotlin**: 1.9+
- **Bluetooth**: Required
- **BLE**: Optional

## License

Proprietary - WiseDrive Technologies Pvt. Ltd.

## Support

For technical support, contact: support@wisedrive.in
