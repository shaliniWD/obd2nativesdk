# WiseDrive OBD2 SDK - Client Integration Guide

## Overview

The WiseDrive OBD2 SDK enables your Android app to perform vehicle diagnostics via Bluetooth-connected ELM327 OBD-II adapters. The SDK handles:
- Bluetooth device discovery and connection (Classic SPP + BLE)
- OBD-II protocol communication (ELM327)
- DTC (Diagnostic Trouble Code) scanning across all ECU modules
- Live vehicle data reading (RPM, speed, temperature, etc.)
- Data submission to your backend API as **plain JSON**

## Requirements

| Requirement | Version |
|-------------|---------|
| Android | API 21+ (Android 5.0+) |
| Kotlin | 1.9+ |
| Gradle | 8.0+ |
| Bluetooth | Classic SPP or BLE |

---

## Step 1: Add Repository

In your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        
        // WiseDrive Private Repository
        maven {
            url = uri("https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-releases")
            credentials {
                username = "YOUR_CLIENT_USERNAME"  // Provided by WiseDrive
                password = "YOUR_CLIENT_TOKEN"     // Provided by WiseDrive
            }
        }
    }
}
```

## Step 2: Add Dependency

In your `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.wisedrive:obd2-sdk:2.0.0")
}
```

## Step 3: Add Permissions

In your `AndroidManifest.xml`:

```xml
<!-- Bluetooth permissions -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Internet (for data submission) -->
<uses-permission android:name="android.permission.INTERNET" />
```

---

## Step 4: Initialize SDK

```kotlin
import com.wisedrive.obd2.WiseDriveOBD2SDK
import com.wisedrive.obd2.models.SDKConfig

class MyActivity : AppCompatActivity() {

    private lateinit var sdk: WiseDriveOBD2SDK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val config = SDKConfig(
            apiKey = "your-api-key",            // Provided by WiseDrive
            useMock = false,                     // true for testing without OBD device
            clientEndpoint = "https://your-server.com/api/obd-data",  // YOUR backend URL
            licensePlate = "MH12AB1234"          // Vehicle registration number
        )
        
        sdk = WiseDriveOBD2SDK.initialize(this, config)
    }
}
```

### Configuration Options

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `apiKey` | String | Yes | Your WiseDrive API key |
| `useMock` | Boolean | No | Use mock data for testing (default: false) |
| `clientEndpoint` | String | No | Your backend URL to receive scan data |
| `licensePlate` | String | Yes | Vehicle registration/license plate number |

---

## Step 5: Scan for OBD Devices

```kotlin
// Start scanning for Bluetooth OBD devices
sdk.startDeviceScan { device ->
    // Called for each discovered OBD device
    println("Found: ${device.name} (${device.id})")
    
    // Show in your UI list
    addDeviceToList(device)
}

// Stop scanning when done
sdk.stopDeviceScan()
```

## Step 6: Connect and Scan Vehicle

```kotlin
// Connect to selected OBD device
sdk.connect(deviceId = selectedDevice.id)

// Run full diagnostic scan
sdk.runFullScan(
    licensePlate = "MH12AB1234",
    trackingId = "ORDER-12345"  // Your order/tracking reference
) { progress ->
    // Progress updates
    println("Phase: ${progress.phase}, Status: ${progress.status}")
    updateProgressUI(progress)
}
```

---

## Step 7: Receive Data on Your Backend

The SDK sends scan data to your `clientEndpoint` as **plain JSON** via HTTP POST. No decryption needed on your side.

### JSON Payload Structure

```json
{
  "inspectionId": "MH12AB1234",
  "scanId": "scan_1234567890",
  "scanTimestamp": "2026-04-15T10:30:00Z",
  "scanDuration": 45000,
  "vehicle": {
    "vin": "KMHXX00XXXX000000",
    "manufacturer": "Hyundai",
    "protocol": "ISO 15765-4 CAN (11 bit ID, 500 kbps)"
  },
  "protocol": "ISO 15765-4 CAN (11 bit ID, 500 kbps)",
  "milStatus": {
    "on": true,
    "dtcCount": 3
  },
  "diagnosticTroubleCodes": [
    {
      "code": "P0300",
      "category": "P",
      "description": "Random/Multiple Cylinder Misfire Detected",
      "severity": "Critical",
      "possibleCauses": [
        "Faulty spark plugs",
        "Ignition coil failure",
        "Fuel injector malfunction"
      ],
      "symptoms": [
        "Engine misfiring",
        "Rough idle",
        "Loss of power"
      ],
      "solutions": [
        "Replace spark plugs",
        "Check ignition coils",
        "Inspect fuel injectors"
      ],
      "isManufacturerSpecific": false,
      "ecuSource": "Engine/PCM"
    }
  ],
  "dtcsByCategory": {
    "history": [...],
    "current": [...],
    "pending": [...]
  },
  "liveData": [
    {
      "pid": "0C",
      "name": "Engine RPM",
      "value": 850.0,
      "displayValue": "850 RPM",
      "unit": "RPM",
      "category": "engine"
    },
    {
      "pid": "0D",
      "name": "Vehicle Speed",
      "value": 0.0,
      "displayValue": "0 km/h",
      "unit": "km/h",
      "category": "speed"
    },
    {
      "pid": "05",
      "name": "Coolant Temperature",
      "value": 85.0,
      "displayValue": "85.0 C",
      "unit": "C",
      "category": "temperature"
    }
  ],
  "summary": {
    "totalDTCs": 3,
    "byType": {
      "critical": 1,
      "important": 1,
      "nonCritical": 1
    },
    "byCategory": {
      "history": 2,
      "current": 1,
      "pending": 0
    },
    "totalLiveReadings": 19,
    "scanCycles": 1
  },
  "apiVersion": "2.0",
  "generatedAt": "2026-04-15T10:30:45Z"
}
```

### Sample Backend (Python/FastAPI)

```python
from fastapi import FastAPI, Request
import json

app = FastAPI()

@app.post("/api/obd-data")
async def receive_obd_data(request: Request):
    scan_data = await request.json()
    
    # Access scan results
    license_plate = scan_data["inspectionId"]
    dtc_count = scan_data["summary"]["totalDTCs"]
    dtcs = scan_data["diagnosticTroubleCodes"]
    live_data = scan_data["liveData"]
    
    print(f"Vehicle: {license_plate}")
    print(f"DTCs Found: {dtc_count}")
    
    for dtc in dtcs:
        print(f"  {dtc['code']}: {dtc['description']} ({dtc['severity']})")
    
    for reading in live_data:
        print(f"  {reading['name']}: {reading['displayValue']}")
    
    # Store in your database
    # db.save_scan(scan_data)
    
    return {"status": "received", "dtcCount": dtc_count}
```

### Sample Backend (Node.js/Express)

```javascript
const express = require('express');
const app = express();
app.use(express.json({ limit: '10mb' }));

app.post('/api/obd-data', (req, res) => {
    const scanData = req.body;
    
    console.log(`Vehicle: ${scanData.inspectionId}`);
    console.log(`DTCs: ${scanData.summary.totalDTCs}`);
    console.log(`MIL: ${scanData.milStatus.on ? 'ON' : 'OFF'}`);
    
    scanData.diagnosticTroubleCodes.forEach(dtc => {
        console.log(`  ${dtc.code}: ${dtc.description}`);
    });
    
    // Store in your database
    // await db.collection('scans').insertOne(scanData);
    
    res.json({ status: 'received', dtcCount: scanData.summary.totalDTCs });
});

app.listen(3000);
```

---

## Live Data PIDs

The SDK reads 19 live data parameters:

| PID | Name | Unit | Range |
|-----|------|------|-------|
| 0C | Engine RPM | RPM | 0 - 16,383 |
| 0D | Vehicle Speed | km/h | 0 - 255 |
| 05 | Coolant Temperature | C | -40 to 215 |
| 11 | Throttle Position | % | 0 - 100 |
| 04 | Engine Load | % | 0 - 100 |
| 0F | Intake Air Temperature | C | -40 to 215 |
| 0B | Intake Manifold Pressure | kPa | 0 - 255 |
| 2F | Fuel Tank Level | % | 0 - 100 |
| 42 | Control Module Voltage | V | 0 - 65.5 |
| 46 | Ambient Air Temperature | C | -40 to 215 |
| 10 | MAF Air Flow Rate | g/s | 0 - 655 |
| 0E | Timing Advance | deg | -64 to 63.5 |
| 0A | Fuel Pressure | kPa | 0 - 765 |
| 5C | Engine Oil Temperature | C | -40 to 210 |
| 5E | Engine Fuel Rate | L/h | 0 - 3,276 |
| 1F | Run Time Since Start | sec | 0 - 65,535 |
| 21 | Distance with MIL On | km | 0 - 65,535 |
| 06 | Short Term Fuel Trim B1 | % | -100 to 99.2 |
| 07 | Long Term Fuel Trim B1 | % | -100 to 99.2 |

---

## Testing with Mock Mode

For development without a physical OBD device:

```kotlin
val config = SDKConfig(
    apiKey = "your-api-key",
    useMock = true,  // Generates realistic mock data
    clientEndpoint = "https://your-server.com/api/obd-data",
    licensePlate = "TEST1234"
)

val sdk = WiseDriveOBD2SDK.initialize(this, config)

// Run scan - uses mock data, sends to your endpoint
sdk.runFullScan(licensePlate = "TEST1234", trackingId = "TEST-001") { progress ->
    println("${progress.phase}: ${progress.status}")
}
```

---

## Error Handling

```kotlin
try {
    sdk.connect(deviceId = device.id)
    sdk.runFullScan(licensePlate = plate, trackingId = orderId) { progress ->
        // Handle progress
    }
} catch (e: BluetoothNotAvailableException) {
    // Bluetooth not enabled
} catch (e: DeviceConnectionException) {
    // Failed to connect to OBD device
} catch (e: ScanTimeoutException) {
    // Scan timed out
} catch (e: Exception) {
    // General error
}
```

---

## Supported Manufacturers

The SDK has optimized ECU scanning for 23+ manufacturers:

**Indian:** Tata, Mahindra, Maruti Suzuki
**Korean:** Hyundai, Kia
**Japanese:** Toyota, Honda, Nissan, Mitsubishi
**German:** Volkswagen, Audi, BMW, Mercedes-Benz
**American:** Ford, General Motors, Chrysler
**European:** Volvo, Jaguar Land Rover, Renault, Peugeot, Fiat, SEAT, Skoda

For unknown manufacturers, the SDK uses a generic fallback that scans 7 common ECU modules.

---

## FAQ

**Q: Do I need to decrypt the data on my backend?**
A: No. Your backend receives plain JSON. Only the WiseDrive internal analytics receives encrypted data.

**Q: What if the OBD device disconnects mid-scan?**
A: The SDK has automatic retry logic and will report partial results if the connection is lost.

**Q: Can I filter which data fields I receive?**
A: The SDK sends the full scan report. You can filter/extract only the fields you need on your backend.

**Q: What Bluetooth adapters are supported?**
A: Any ELM327-compatible OBD-II adapter (Bluetooth Classic SPP or BLE). Popular brands: VEEPEAK, BAFX, Konnwei, Carista, BlueDriver.

---

## Support

- Email: sdk@wisedrive.in
- Documentation: https://wisedrive.in/sdk/docs
- SDK Version: 2.0.0
