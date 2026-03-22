# WiseDrive OBD2 SDK for Android

A production-ready Android Native SDK (Kotlin) for connecting to ELM327 OBD-II adapters via Bluetooth Classic (SPP) and BLE, scanning vehicle diagnostic trouble codes (DTCs), reading live sensor data, and returning encrypted scan reports.

## Features

- **Bluetooth Classic SPP** - Primary connection method with 5 fallback strategies for maximum device compatibility
- **BLE Support** - Secondary adapter for BLE-only ELM327 devices
- **Multi-ECU Scanning** - Scan across all vehicle ECU modules (Engine, Transmission, ABS, Airbag, BCM, etc.)
- **20+ Manufacturer Configs** - Pre-configured ECU addresses for major manufacturers (Tata, Hyundai, Toyota, Ford, BMW, etc.)
- **4000+ DTC Descriptions** - Comprehensive database of diagnostic trouble codes with descriptions
- **Knowledge Base** - Causes, symptoms, and solutions for common DTCs
- **19 Live Data PIDs** - Real-time sensor data (RPM, Speed, Temperature, Fuel, etc.)
- **AES-256-GCM Encryption** - Secure, tamper-proof scan reports
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

// 5. Run full scan
val encryptedResult = sdk.runFullScan(ScanOptions(
    orderId = "ORDER-12345",
    manufacturer = "hyundai",
    year = 2022,
    onProgress = { stage ->
        println("Stage: ${stage.label} - ${stage.status}")
    }
))

// 6. Submit encrypted report to backend
val submitted = sdk.submitReport(encryptedResult)

// 7. Disconnect
sdk.disconnect()
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

<uses-feature android:name="android.hardware.bluetooth" android:required="true" />
```

Request permissions at runtime:

```kotlin
sdk.requestPermissions(activity)
```

## Advanced Usage

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

## Security

### Encryption

All scan reports are encrypted with AES-256-GCM before being returned to the host app. The encryption key is fetched from the backend during SDK initialization and stored only in memory.

```kotlin
// Host app only sees encrypted data
val encryptedResult = sdk.runFullScan(options)
// encryptedResult.payload is a Base64-encoded AES-GCM ciphertext
// Only the backend can decrypt it
```

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
