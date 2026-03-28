# WiseDrive OBD2 SDK - Backend Integration Guide

## Quick Start

### 1. Install Dependencies

```bash
pip install cryptography flask
```

### 2. Generate Your Keys (One-Time Setup)

```python
from wisedrive_decryption import KeyGenerator

# Generate RSA-4096 key pair for your organization
public_key, private_key = KeyGenerator.generate_rsa_4096()

# Save keys securely
with open('wisedrive_public.pem', 'w') as f:
    f.write(public_key)
    
with open('wisedrive_private.pem', 'w') as f:
    f.write(private_key)  # KEEP THIS SECRET!

print("PUBLIC KEY (embed in SDK):")
print(public_key)
```

### 3. Embed Public Key in SDK

Update `/sdk/src/main/java/com/wisedrive/obd2/security/ObfuscatedKeyStore.kt`:

```kotlin
// Replace the demo key parts with your actual public key
// Split the base64 content into 8 parts for obfuscation
```

### 4. Set Up Backend Endpoint

```python
from flask import Flask, request, jsonify
from wisedrive_decryption import WiseDriveDecryptor, DecryptionError

app = Flask(__name__)

# Load your private key
with open('wisedrive_private.pem', 'r') as f:
    PRIVATE_KEY = f.read()

decryptor = WiseDriveDecryptor(PRIVATE_KEY)

@app.route('/api/obd/report', methods=['POST'])
def receive_report():
    body = request.get_json()
    encrypted_data = body['encryptedData']
    
    try:
        # Decrypt the OBD scan data
        scan_data = decryptor.decrypt(encrypted_data)
        
        # Process the data
        print(f"VIN: {scan_data['vin']}")
        print(f"DTCs: {len(scan_data['code_details'])}")
        
        return jsonify({'result': 'SUCCESS'})
        
    except DecryptionError as e:
        return jsonify({'error': str(e)}), 400

if __name__ == '__main__':
    app.run(port=8082)
```

## Encrypted Payload Format

The SDK sends data in this format:

```json
{
  "version": 2,
  "keyId": 1,
  "timestamp": 1705312800000,
  "encryptedData": "V0RTVwACAAAAAQAAASjh4gJ..."
}
```

### Payload Structure (Binary)

```
┌────────────────────────────────────────────────────────┐
│ Header (16 bytes)                                       │
│   - Magic: "WDSW" (4 bytes)                            │
│   - Version: 2 (2 bytes)                               │
│   - Key ID: 1 (4 bytes)                                │
│   - Timestamp: 6 bytes                                 │
├────────────────────────────────────────────────────────┤
│ RSA Encrypted AES Key (512 bytes for RSA-4096)         │
├────────────────────────────────────────────────────────┤
│ IV (12 bytes)                                          │
├────────────────────────────────────────────────────────┤
│ AES-GCM Ciphertext + Auth Tag (variable + 16 bytes)    │
├────────────────────────────────────────────────────────┤
│ HMAC-SHA512 Signature (64 bytes)                       │
└────────────────────────────────────────────────────────┘
```

## Decrypted Data Format

After decryption, you get the OBD scan data:

```json
{
  "license_plate": "MH12AB1234",
  "tracking_id": "ORD6894331",
  "report_url": "https://example.com/report.pdf",
  "car_company": "Hyundai",
  "vin": "KMHXX00XXXX000000",
  "mil_status": true,
  "scan_ended": "automatic_success",
  "faulty_modules": ["Engine", "ABS"],
  "non_faulty_modules": ["Transmission", "BCM", "Airbag", ...],
  "code_details": [
    {
      "dtc": "P0503",
      "meaning": "Vehicle Speed Sensor A Circuit Intermittent",
      "module": "Engine Control Module (ECM)",
      "status": "Confirmed",
      "descriptions": ["..."],
      "causes": ["Sensor malfunction", ...],
      "symptoms": ["Check engine light", ...],
      "solutions": ["Replace sensor", ...]
    }
  ],
  "battery_voltage": 14.02
}
```

## Security Best Practices

### Key Management

| Do | Don't |
|----|-------|
| Store private key in secure vault (AWS KMS, HashiCorp Vault) | Commit private key to git |
| Use environment variables | Hardcode keys in code |
| Rotate keys periodically | Use same key forever |
| Keep old keys for historical data | Delete old keys immediately |

### Replay Protection

```python
from datetime import datetime, timedelta

# Track seen payloads
seen_timestamps = {}
REPLAY_WINDOW = timedelta(minutes=5)

def check_replay(timestamp_ms: int, payload_hash: str) -> bool:
    timestamp = datetime.fromtimestamp(timestamp_ms / 1000)
    
    # Reject old timestamps
    if datetime.now() - timestamp > REPLAY_WINDOW:
        return False  # Too old
    
    # Reject duplicates
    key = f"{timestamp_ms}:{payload_hash}"
    if key in seen_timestamps:
        return False  # Duplicate
    
    seen_timestamps[key] = True
    return True
```

### Error Handling

```python
try:
    data = decryptor.decrypt(encrypted_data)
except DecryptionError as e:
    if "HMAC" in str(e):
        # Payload was tampered with
        log_security_event("tampering_detected", e)
    elif "RSA" in str(e):
        # Wrong private key or corrupted data
        log_security_event("decryption_failed", e)
    return error_response()
```

## Files Reference

| File | Purpose |
|------|---------|
| `wisedrive_decryption.py` | Main decryption module |
| `api_server.py` | Flask backend example |
| `red_team_tests.py` | Security testing suite |

## Running Tests

```bash
# Run security red team tests
cd backend
python3 red_team_tests.py

# Expected output: 13/14 SECURE, 1/14 PARTIALLY SECURE
# (Replay is partial because it's app-level, not crypto-level)
```

## Support

For technical support: support@wisedrive.in
