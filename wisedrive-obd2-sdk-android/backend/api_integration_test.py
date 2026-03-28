#!/usr/bin/env python3
"""
API Integration Test - Test encryption/decryption without running server
"""

import sys
import os
import json
import base64
import time

# Add current directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from wisedrive_decryption import WiseDriveDecryptor, KeyGenerator
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding as asym_padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend
import hashlib
import hmac
import struct

def test_api_integration():
    """Test the complete encryption/decryption flow"""
    print("🔧 Testing API Integration...")
    
    # Generate keys
    public_key, private_key = KeyGenerator.generate_rsa_4096()
    decryptor = WiseDriveDecryptor(private_key)
    
    # Sample OBD data
    obd_data = {
        "license_plate": "MH12AB1234",
        "tracking_id": "ORD6894331",
        "vin": "KMHXX00XXXX000000",
        "car_company": "Hyundai",
        "mil_status": True,
        "faulty_modules": ["Engine", "ABS"],
        "code_details": [
            {
                "dtc": "P0503",
                "meaning": "Vehicle Speed Sensor Intermittent",
                "severity": "MEDIUM",
                "ecu_source": "PCM"
            }
        ],
        "battery_voltage": 14.02
    }
    
    # Encrypt data (simulating SDK)
    encrypted_data = encrypt_like_sdk(obd_data, public_key)
    encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
    
    # Create API payload
    api_payload = {
        "version": 2,
        "keyId": 1,
        "timestamp": int(time.time() * 1000),
        "encryptedData": encrypted_b64
    }
    
    print(f"✓ Created API payload: {len(encrypted_b64)} chars")
    
    # Test decryption (simulating backend)
    try:
        decrypted = decryptor.decrypt(encrypted_b64)
        print(f"✓ Decryption successful")
        print(f"  License Plate: {decrypted['license_plate']}")
        print(f"  Tracking ID: {decrypted['tracking_id']}")
        print(f"  DTCs: {len(decrypted['code_details'])}")
        
        # Verify data integrity
        assert decrypted['license_plate'] == obd_data['license_plate']
        assert decrypted['tracking_id'] == obd_data['tracking_id']
        assert len(decrypted['code_details']) == len(obd_data['code_details'])
        
        print("✅ API Integration Test PASSED")
        return True
        
    except Exception as e:
        print(f"❌ API Integration Test FAILED: {e}")
        return False

def encrypt_like_sdk(payload, public_key_pem):
    """Encrypt payload like the Android SDK would"""
    public_key = serialization.load_pem_public_key(
        public_key_pem.encode('utf-8'),
        backend=default_backend()
    )
    
    # Generate random AES key
    aes_key = os.urandom(32)  # 256 bits
    
    # Generate random IV
    iv = os.urandom(12)
    
    # Encrypt data with AES-GCM
    aesgcm = AESGCM(aes_key)
    plaintext = json.dumps(payload).encode('utf-8')
    ciphertext = aesgcm.encrypt(iv, plaintext, None)
    
    # Encrypt AES key with RSA
    encrypted_aes_key = public_key.encrypt(
        aes_key,
        asym_padding.OAEP(
            mgf=asym_padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None
        )
    )
    
    # Build header (16 bytes)
    timestamp = int(time.time() * 1000)
    header = struct.pack('>4sHI', b"WDSW", 2, 1)  # 10 bytes
    header += struct.pack('>6s', struct.pack('>Q', timestamp)[2:])  # 6 bytes for timestamp
    
    # Assemble payload without HMAC
    payload_without_hmac = header + encrypted_aes_key + iv + ciphertext
    
    # Calculate HMAC
    hmac_key = hashlib.sha256(aes_key + b"HMAC_KEY_DERIVATION").digest()
    hmac_sig = hmac.new(hmac_key, payload_without_hmac, hashlib.sha512).digest()
    
    return payload_without_hmac + hmac_sig

if __name__ == "__main__":
    success = test_api_integration()
    sys.exit(0 if success else 1)