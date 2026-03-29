#!/usr/bin/env python3
"""
WiseDrive OBD2 SDK - ENCRYPTED Endpoint Test
=============================================

This test sends ENCRYPTED data to WiseDrive endpoint.
The endpoint should receive encrypted blob, NOT plain JSON.
"""

import os
import sys
import json
import time
import base64
import hashlib
import hmac
import struct
import requests
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding as asym_padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend

from wisedrive_decryption import KeyGenerator

class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    CYAN = '\033[96m'
    MAGENTA = '\033[95m'
    END = '\033[0m'
    BOLD = '\033[1m'


def encrypt_payload(payload: dict, public_key_pem: str) -> str:
    """
    Encrypt payload using Hybrid RSA-4096 + AES-256-GCM
    Returns Base64 encoded encrypted blob
    """
    plaintext = json.dumps(payload).encode('utf-8')
    
    # Load public key
    public_key = serialization.load_pem_public_key(
        public_key_pem.encode('utf-8'),
        backend=default_backend()
    )
    
    # Generate random AES-256 key
    aes_key = os.urandom(32)
    
    # Generate random IV
    iv = os.urandom(12)
    
    # Encrypt data with AES-256-GCM
    aesgcm = AESGCM(aes_key)
    ciphertext = aesgcm.encrypt(iv, plaintext, None)
    
    # Encrypt AES key with RSA-4096-OAEP
    encrypted_aes_key = public_key.encrypt(
        aes_key,
        asym_padding.OAEP(
            mgf=asym_padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None
        )
    )
    
    # Build header (16 bytes)
    magic = b"WDSW"  # WiseDrive Scan WiseDrive
    version = 2
    key_id = 1
    timestamp = int(time.time() * 1000)
    
    header = struct.pack('>4sHI', magic, version, key_id)
    header += struct.pack('>Q', timestamp)[2:]  # 6 bytes for timestamp
    
    # Assemble payload without HMAC
    payload_without_hmac = header + encrypted_aes_key + iv + ciphertext
    
    # Calculate HMAC-SHA512
    hmac_key = hashlib.sha256(aes_key + b"HMAC_KEY_DERIVATION").digest()
    hmac_sig = hmac.new(hmac_key, payload_without_hmac, hashlib.sha512).digest()
    
    # Final encrypted blob
    encrypted_blob = payload_without_hmac + hmac_sig
    
    return base64.b64encode(encrypted_blob).decode('utf-8')


def test_encrypted_endpoint():
    """Test submission of ENCRYPTED data to WiseDrive endpoint"""
    
    print(f"\n{Colors.BOLD}{Colors.MAGENTA}{'='*70}")
    print(" TESTING WISEDRIVE ENCRYPTED ENDPOINT")
    print(f"{'='*70}{Colors.END}\n")
    
    # Generate WiseDrive key pair (in production, WiseDrive provides their public key)
    print(f"{Colors.CYAN}Generating RSA-4096 key pair for WiseDrive...{Colors.END}")
    wisedrive_public, wisedrive_private = KeyGenerator.generate_rsa_4096()
    print(f"{Colors.GREEN}✓ Keys generated{Colors.END}")
    
    # Endpoint for encrypted data
    encrypted_endpoint = "http://164.52.213.170:82/apiv2/webhook/obdreport/wisedrive/encrypted"
    legacy_endpoint = "http://164.52.213.170:82/apiv2/webhook/obdreport/wisedrive"
    
    # OBD scan data
    scan_data = {
        "license_plate": "MH12AB1234",
        "tracking_id": "ORD6894331",
        "report_url": "https://example.com/report.pdf",
        "car_company": "Hyundai",
        "status": 1,
        "time": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z",
        "mechanic_name": "Wisedrive Utils",
        "mechanic_email": "utils@wisedrive.in",
        "vin": "KMHXX00XXXX000000",
        "mil_status": True,
        "scan_ended": "automatic_success",
        "faulty_modules": ["Engine Control Module (ECM)", "ABS/ESP Control Module"],
        "non_faulty_modules": ["Transmission", "BCM", "Airbag", "HVAC"],
        "code_details": [
            {
                "dtc": "P0503",
                "meaning": "Vehicle Speed Sensor Intermittent",
                "module": "ECM",
                "status": "Confirmed"
            }
        ],
        "battery_voltage": 14.02
    }
    
    # Show plain data
    print(f"\n{Colors.YELLOW}PLAIN DATA (what SDK sees internally):{Colors.END}")
    print(f"  License Plate: {scan_data['license_plate']}")
    print(f"  Tracking ID: {scan_data['tracking_id']}")
    print(f"  VIN: {scan_data['vin']}")
    plain_json = json.dumps(scan_data)
    print(f"  JSON Size: {len(plain_json)} bytes")
    
    # Encrypt the data
    print(f"\n{Colors.CYAN}Encrypting data with RSA-4096 + AES-256-GCM...{Colors.END}")
    encrypted_b64 = encrypt_payload(scan_data, wisedrive_public)
    encrypted_bytes = base64.b64decode(encrypted_b64)
    
    print(f"{Colors.GREEN}✓ Encryption complete{Colors.END}")
    print(f"  Encrypted Size: {len(encrypted_bytes)} bytes")
    print(f"  Base64 Length: {len(encrypted_b64)} chars")
    print(f"  Magic: {encrypted_bytes[0:4].decode('utf-8')}")
    print(f"  Version: {struct.unpack('>H', encrypted_bytes[4:6])[0]}")
    
    # Show encrypted payload (what will be sent)
    print(f"\n{Colors.RED}ENCRYPTED PAYLOAD (what endpoint will receive):{Colors.END}")
    print(f"  {encrypted_b64[:100]}...")
    print(f"  ...{encrypted_b64[-50:]}")
    print(f"\n  {Colors.RED}^^^ This is UNREADABLE without the private key ^^^{Colors.END}")
    
    # Create API request payload
    api_payload = {
        "version": 2,
        "keyId": 1,
        "timestamp": int(time.time() * 1000),
        "encryptedData": encrypted_b64
    }
    
    print(f"\n{Colors.CYAN}API Request Body:{Colors.END}")
    print(json.dumps({
        "version": api_payload["version"],
        "keyId": api_payload["keyId"],
        "timestamp": api_payload["timestamp"],
        "encryptedData": encrypted_b64[:80] + "... [ENCRYPTED]"
    }, indent=2))
    
    # Send to encrypted endpoint
    print(f"\n{Colors.BOLD}Sending ENCRYPTED data to endpoint...{Colors.END}")
    print(f"  Endpoint: {encrypted_endpoint}")
    
    try:
        response = requests.post(
            encrypted_endpoint,
            json=api_payload,
            headers={
                "Content-Type": "application/json",
                "Authorization": "Basic cHJhc2FkOnByYXNhZEAxMjM=",
                "X-Encryption-Version": "2",
                "X-Key-ID": "1"
            },
            timeout=30
        )
        
        print(f"\n{Colors.BOLD}Response from encrypted endpoint:{Colors.END}")
        print(f"  Status: {response.status_code}")
        print(f"  Body: {response.text}")
        
        if response.status_code == 404:
            print(f"\n{Colors.YELLOW}Note: /encrypted endpoint may not exist yet on WiseDrive server{Colors.END}")
            print(f"{Colors.YELLOW}The server needs to implement decryption on their side{Colors.END}")
            
    except Exception as e:
        print(f"{Colors.RED}Error: {e}{Colors.END}")
    
    # Also try sending to legacy endpoint to show the difference
    print(f"\n{Colors.BOLD}{'='*70}")
    print(" COMPARISON: Legacy endpoint (receives PLAIN JSON)")
    print(f"{'='*70}{Colors.END}")
    
    print(f"\n{Colors.YELLOW}Sending PLAIN JSON to legacy endpoint...{Colors.END}")
    print(f"  Endpoint: {legacy_endpoint}")
    
    try:
        response2 = requests.post(
            legacy_endpoint,
            json=scan_data,  # Plain JSON, NOT encrypted
            headers={
                "Content-Type": "application/json",
                "Authorization": "Basic cHJhc2FkOnByYXNhZEAxMjM="
            },
            timeout=30
        )
        
        print(f"\n{Colors.BOLD}Response from legacy endpoint:{Colors.END}")
        print(f"  Status: {response2.status_code}")
        print(f"  Body: {response2.text}")
        
    except Exception as e:
        print(f"{Colors.RED}Error: {e}{Colors.END}")
    
    # Summary
    print(f"\n{Colors.BOLD}{Colors.MAGENTA}{'='*70}")
    print(" SUMMARY")
    print(f"{'='*70}{Colors.END}")
    
    print(f"""
{Colors.BOLD}Current State:{Colors.END}
  • SDK generates ENCRYPTED blobs (working ✓)
  • Encryption uses RSA-4096 + AES-256-GCM (military-grade ✓)
  • Legacy endpoint receives PLAIN JSON (insecure!)
  • Encrypted endpoint needs implementation on WiseDrive server

{Colors.BOLD}What WiseDrive Server Needs:{Colors.END}
  1. Create new endpoint: /apiv2/webhook/obdreport/wisedrive/encrypted
  2. Accept JSON with 'encryptedData' field
  3. Use wisedrive_decryption.py to decrypt
  4. Process decrypted data same as before

{Colors.BOLD}Example Server Code:{Colors.END}
  from wisedrive_decryption import WiseDriveDecryptor
  
  decryptor = WiseDriveDecryptor(WISEDRIVE_PRIVATE_KEY)
  
  @app.post("/apiv2/webhook/obdreport/wisedrive/encrypted")
  def receive_encrypted(body):
      encrypted_data = body["encryptedData"]
      scan_data = decryptor.decrypt(encrypted_data)
      # Now process scan_data as normal
""")
    
    print(f"{Colors.GREEN}✓ Test complete{Colors.END}\n")


if __name__ == "__main__":
    test_encrypted_endpoint()
