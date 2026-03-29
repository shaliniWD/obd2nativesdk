# WiseDrive OBD2 SDK - Backend Integration Quick Start

## Java/Spring Boot Integration (5 Minutes)

### Step 1: Copy Files

Copy these files to your Spring Boot project:

```
src/main/java/com/wisedrive/inspection/
├── security/
│   └── WiseDriveDecryptor.java          # Core decryption class
├── controllers/
│   └── EncryptedObdWebhookController.java  # REST endpoint
└── dto/
    ├── EncryptedOBDPayload.java         # Request DTO
    └── OBDScanData.java                 # Decrypted data DTO
```

### Step 2: Configure Private Key

Add to `application.yml`:

```yaml
wisedrive:
  encryption:
    private-key-path: /etc/secrets/wisedrive_private.pem
```

Or use environment variable:

```bash
export WISEDRIVE_PRIVATE_KEY_PATH=/path/to/wisedrive_private.pem
```

### Step 3: Generate Keys (First Time Only)

```bash
# Generate RSA-4096 private key
openssl genpkey -algorithm RSA -out wisedrive_private.pem -pkeyopt rsa_keygen_bits:4096

# Extract public key (give to SDK team)
openssl rsa -pubout -in wisedrive_private.pem -out wisedrive_public.pem

# Store private key securely
sudo mv wisedrive_private.pem /etc/secrets/
sudo chmod 600 /etc/secrets/wisedrive_private.pem
```

### Step 4: Test the Endpoint

```bash
# Health check
curl http://localhost:8080/apiv2/webhook/obdreport/wisedrive/encrypted/health

# Expected response:
{
  "service": "WiseDrive OBD Encrypted Webhook",
  "encryptionEnabled": true,
  "supportedVersion": 2,
  "algorithm": "RSA-4096 + AES-256-GCM + HMAC-SHA512"
}
```

### Step 5: Integrate with Existing Code

In `EncryptedObdWebhookController.java`, find the TODO comment and add your processing logic:

```java
// After decryption, call your existing service
JsonNode scanData = decryptor.decrypt(payload.getEncryptedData());

// Convert to your existing DTO if needed
OBDScanData data = objectMapper.treeToValue(scanData, OBDScanData.class);

// Call your existing processing logic
obdService.processReport(data);
```

---

## Python/Flask Integration (5 Minutes)

### Step 1: Install Dependencies

```bash
pip install cryptography flask
```

### Step 2: Copy Decryption Module

```bash
cp wisedrive_decryption.py /your/project/
```

### Step 3: Create Endpoint

```python
from flask import Flask, request, jsonify
from wisedrive_decryption import WiseDriveDecryptor
import os

app = Flask(__name__)

# Load private key
PRIVATE_KEY = open('/etc/secrets/wisedrive_private.pem').read()
decryptor = WiseDriveDecryptor(PRIVATE_KEY)

@app.route('/apiv2/webhook/obdreport/wisedrive/encrypted', methods=['POST'])
def receive_encrypted():
    body = request.get_json()
    
    try:
        # Decrypt
        scan_data = decryptor.decrypt(body['encryptedData'])
        
        # Process
        license_plate = scan_data['license_plate']
        tracking_id = scan_data['tracking_id']
        dtc_count = len(scan_data.get('code_details', []))
        
        # Your existing processing logic here
        # process_obd_report(scan_data)
        
        return jsonify({
            'result': 'SUCCESS',
            'trackingId': tracking_id,
            'dtcCount': dtc_count
        })
        
    except Exception as e:
        return jsonify({'error': str(e)}), 400

if __name__ == '__main__':
    app.run(port=8082)
```

---

## Verifying Integration

### Test with Sample Encrypted Payload

Run the test script to generate a valid encrypted payload:

```bash
cd backend
python3 sdk_mock_test.py
```

This will output an encrypted payload you can use for testing.

### Expected Server Logs

When receiving encrypted data, you should see:

```
INFO  - Received encrypted OBD report
DEBUG - Encrypted payload header: magic=WDSW, version=2, keyId=1
INFO  - === DECRYPTED OBD SCAN DATA ===
INFO  - License Plate: MH12AB1234
INFO  - Tracking ID: ORD6894331
INFO  - VIN: KMHXX00XXXX000000
INFO  - DTCs Found: 2
INFO  - ================================
```

NOT plain JSON like:
```
DEBUG - Received payload: {license_plate=MH12AB1234...}  # WRONG - This is unencrypted!
```

---

## Troubleshooting

### "Private key not configured"

1. Check the key file path exists
2. Check file permissions (must be readable by app)
3. Verify environment variable is set

### "RSA decryption failed"

1. Verify you're using the correct private key
2. Ensure public key in SDK matches your private key
3. Check key format is PKCS#8 PEM

### "HMAC verification failed"

1. Payload was tampered in transit
2. Check for any proxies modifying data
3. Verify SDK and backend are using same encryption version

---

## Security Checklist

- [ ] Private key stored securely (not in code/git)
- [ ] Private key file has restricted permissions (600)
- [ ] Using HTTPS in production
- [ ] Replay protection enabled
- [ ] Logging doesn't expose sensitive data
- [ ] Regular key rotation planned
