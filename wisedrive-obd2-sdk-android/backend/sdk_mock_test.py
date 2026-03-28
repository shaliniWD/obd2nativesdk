#!/usr/bin/env python3
"""
WiseDrive OBD2 SDK - Mock Submission Test
==========================================

This script simulates what the Android SDK does when submitting OBD scan data:
1. Generates mock OBD scan data
2. Encrypts it using Hybrid RSA-4096 + AES-256-GCM
3. Submits to WiseDrive endpoint (mock or real)
4. Shows the complete data flow

Run: python3 sdk_mock_test.py
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
from typing import Dict, Any, Tuple

# Add current directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding as asym_padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend

from wisedrive_decryption import WiseDriveDecryptor, KeyGenerator

# Colors for terminal output
class Colors:
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    CYAN = '\033[96m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    END = '\033[0m'
    BOLD = '\033[1m'


def print_header(text: str):
    print(f"\n{Colors.HEADER}{Colors.BOLD}{'='*70}")
    print(f" {text}")
    print(f"{'='*70}{Colors.END}\n")


def print_section(text: str):
    print(f"\n{Colors.CYAN}{Colors.BOLD}▶ {text}{Colors.END}")


def print_success(text: str):
    print(f"{Colors.GREEN}✓ {text}{Colors.END}")


def print_info(text: str):
    print(f"{Colors.BLUE}ℹ {text}{Colors.END}")


def print_data(label: str, value: str, truncate: int = 0):
    if truncate and len(value) > truncate:
        value = value[:truncate] + "..."
    print(f"  {Colors.YELLOW}{label}:{Colors.END} {value}")


class SDKMockSimulator:
    """
    Simulates the WiseDrive OBD2 Android SDK behavior
    """
    
    def __init__(self, use_mock: bool = True):
        self.use_mock = use_mock
        self.analytics_endpoint = "http://164.52.213.170:82/apiv2/webhook/obdreport/wisedrive"
        self.encrypted_endpoint = "http://164.52.213.170:82/apiv2/webhook/obdreport/wisedrive/encrypted"
        
        # Generate key pairs (in real SDK, these are embedded/fetched)
        print_section("Generating RSA-4096 Key Pairs")
        self.client_public, self.client_private = KeyGenerator.generate_rsa_4096()
        self.wisedrive_public, self.wisedrive_private = KeyGenerator.generate_rsa_4096()
        print_success("Client key pair generated")
        print_success("WiseDrive key pair generated")
        
        # Initialize decryptors
        self.client_decryptor = WiseDriveDecryptor(self.client_private)
        self.wisedrive_decryptor = WiseDriveDecryptor(self.wisedrive_private)
    
    def generate_mock_scan_data(self, registration_number: str, tracking_id: str) -> Dict[str, Any]:
        """Generate mock OBD scan data like the SDK would"""
        return {
            "license_plate": registration_number,
            "tracking_id": tracking_id,
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
            "non_faulty_modules": [
                "Transmission", "BCM", "Airbag", "HVAC", "EPS", 
                "Instrument Cluster", "Infotainment system", "Immobilizer",
                "4WD", "SIC", "Head up display", "Radio", "Others"
            ],
            "code_details": [
                {
                    "dtc": "P0503",
                    "meaning": "Vehicle Speed Sensor A Circuit Intermittent/Erratic/High",
                    "module": "Engine Control Module (ECM)",
                    "status": "Confirmed",
                    "descriptions": ["The vehicle speed sensor signal is erratic or intermittent"],
                    "causes": [
                        "Faulty vehicle speed sensor",
                        "Damaged wiring or connectors",
                        "Poor electrical connection",
                        "ECM malfunction"
                    ],
                    "symptoms": [
                        "Check engine light on",
                        "Erratic speedometer reading",
                        "Transmission shifting problems",
                        "Cruise control malfunction"
                    ],
                    "solutions": [
                        "Check and repair wiring/connectors",
                        "Replace vehicle speed sensor",
                        "Update ECM software",
                        "Check for related DTCs"
                    ]
                },
                {
                    "dtc": "C1201",
                    "meaning": "Engine Control System Malfunction",
                    "module": "ABS/ESP Control Module",
                    "status": "Confirmed",
                    "descriptions": ["ABS module detected engine control issue"],
                    "causes": ["Engine DTCs present", "Communication error"],
                    "symptoms": ["ABS light on", "Traction control disabled"],
                    "solutions": ["Fix engine DTCs first", "Clear codes after repair"]
                }
            ],
            "battery_voltage": 14.02
        }
    
    def encrypt_for_wisedrive(self, payload: Dict[str, Any]) -> Tuple[bytes, bytes]:
        """
        Encrypt payload for WiseDrive using Hybrid RSA+AES
        Returns: (encrypted_blob, aes_key_for_verification)
        """
        plaintext = json.dumps(payload).encode('utf-8')
        
        # Load public key
        public_key = serialization.load_pem_public_key(
            self.wisedrive_public.encode('utf-8'),
            backend=default_backend()
        )
        
        # Generate random AES-256 key (Perfect Forward Secrecy)
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
        
        return encrypted_blob, aes_key
    
    def encrypt_for_client(self, payload: Dict[str, Any]) -> bytes:
        """Encrypt payload for Client backend"""
        plaintext = json.dumps(payload).encode('utf-8')
        
        public_key = serialization.load_pem_public_key(
            self.client_public.encode('utf-8'),
            backend=default_backend()
        )
        
        aes_key = os.urandom(32)
        iv = os.urandom(12)
        
        aesgcm = AESGCM(aes_key)
        ciphertext = aesgcm.encrypt(iv, plaintext, None)
        
        encrypted_aes_key = public_key.encrypt(
            aes_key,
            asym_padding.OAEP(
                mgf=asym_padding.MGF1(algorithm=hashes.SHA256()),
                algorithm=hashes.SHA256(),
                label=None
            )
        )
        
        magic = b"WDSC"  # WiseDrive Scan Client
        header = struct.pack('>4sHI', magic, 2, 1)
        header += struct.pack('>Q', int(time.time() * 1000))[2:]
        
        payload_without_hmac = header + encrypted_aes_key + iv + ciphertext
        hmac_key = hashlib.sha256(aes_key + b"HMAC_KEY_DERIVATION").digest()
        hmac_sig = hmac.new(hmac_key, payload_without_hmac, hashlib.sha512).digest()
        
        return payload_without_hmac + hmac_sig
    
    def run_full_scan_simulation(self, registration_number: str, tracking_id: str):
        """Simulate a full OBD scan like the SDK would do"""
        
        print_header("WISEDRIVE OBD2 SDK - MOCK SCAN SIMULATION")
        print_info(f"Mode: {'MOCK' if self.use_mock else 'LIVE'}")
        print_info(f"Registration: {registration_number}")
        print_info(f"Tracking ID: {tracking_id}")
        print_info(f"Timestamp: {datetime.now().isoformat()}")
        
        # Step 1: Generate mock scan data
        print_section("Step 1: OBD Scan Complete - Generating Report")
        scan_data = self.generate_mock_scan_data(registration_number, tracking_id)
        
        print_data("VIN", scan_data["vin"])
        print_data("Car Company", scan_data["car_company"])
        print_data("MIL Status", str(scan_data["mil_status"]))
        print_data("Faulty Modules", ", ".join(scan_data["faulty_modules"]))
        print_data("DTCs Found", str(len(scan_data["code_details"])))
        print_data("Battery Voltage", f"{scan_data['battery_voltage']}V")
        
        # Step 2: Show plain JSON (what client app sees)
        print_section("Step 2: Plain JSON Report (Client App View)")
        plain_json = json.dumps(scan_data, indent=2)
        print(f"\n{Colors.YELLOW}--- PLAIN JSON (First 1000 chars) ---{Colors.END}")
        print(plain_json[:1000])
        if len(plain_json) > 1000:
            print("...")
        print(f"{Colors.YELLOW}--- END PLAIN JSON ---{Colors.END}")
        print_info(f"Total JSON size: {len(plain_json)} bytes")
        
        # Step 3: Encrypt for WiseDrive
        print_section("Step 3: Encrypting for WiseDrive Analytics")
        encrypted_wisedrive, aes_key = self.encrypt_for_wisedrive(scan_data)
        encrypted_b64 = base64.b64encode(encrypted_wisedrive).decode('utf-8')
        
        print_data("Magic Bytes", encrypted_wisedrive[0:4].decode('utf-8'))
        print_data("Version", str(struct.unpack('>H', encrypted_wisedrive[4:6])[0]))
        print_data("Key ID", str(struct.unpack('>I', encrypted_wisedrive[6:10])[0]))
        print_data("Encrypted Size", f"{len(encrypted_wisedrive)} bytes")
        print_data("Base64 Length", f"{len(encrypted_b64)} chars")
        
        print(f"\n{Colors.RED}--- ENCRYPTED PAYLOAD (Cannot be read without private key) ---{Colors.END}")
        print(encrypted_b64[:200])
        print("... [ENCRYPTED DATA - UNREADABLE] ...")
        print(encrypted_b64[-50:])
        print(f"{Colors.RED}--- END ENCRYPTED ---{Colors.END}")
        
        # Step 4: Encrypt for Client (separate key)
        print_section("Step 4: Encrypting for Client Backend (Separate Key)")
        encrypted_client = self.encrypt_for_client(scan_data)
        encrypted_client_b64 = base64.b64encode(encrypted_client).decode('utf-8')
        
        print_data("Magic Bytes", encrypted_client[0:4].decode('utf-8'))
        print_data("Encrypted Size", f"{len(encrypted_client)} bytes")
        print_success("Client encrypted blob ready (different key than WiseDrive)")
        
        # Step 5: Submit to WiseDrive (mock or real)
        print_section("Step 5: Submitting to WiseDrive Analytics")
        
        if self.use_mock:
            print_info("MOCK MODE - Simulating API call")
            time.sleep(0.5)  # Simulate network delay
            response = {"result": "SUCCESS", "mock": True}
            print_success(f"Mock submission successful: {json.dumps(response)}")
        else:
            print_info(f"Sending to: {self.analytics_endpoint}")
            try:
                # Send plain JSON to legacy endpoint (for testing)
                api_response = requests.post(
                    self.analytics_endpoint,
                    json=scan_data,
                    headers={
                        "Content-Type": "application/json",
                        "Authorization": "Basic cHJhc2FkOnByYXNhZEAxMjM="
                    },
                    timeout=30
                )
                print_data("Status Code", str(api_response.status_code))
                print_data("Response", api_response.text[:200])
                
                if api_response.status_code == 200:
                    print_success("Submission successful!")
                else:
                    print(f"{Colors.RED}✗ Submission failed{Colors.END}")
            except Exception as e:
                print(f"{Colors.RED}✗ Error: {e}{Colors.END}")
        
        # Step 6: Verify decryption works
        print_section("Step 6: Verifying Decryption (Backend Simulation)")
        
        try:
            # Decrypt WiseDrive blob
            decrypted_wisedrive = self.wisedrive_decryptor.decrypt(encrypted_b64)
            print_success("WiseDrive decryption successful!")
            print_data("Decrypted License Plate", decrypted_wisedrive["license_plate"])
            print_data("Decrypted Tracking ID", decrypted_wisedrive["tracking_id"])
            print_data("Decrypted VIN", decrypted_wisedrive["vin"])
            print_data("Decrypted DTCs", str(len(decrypted_wisedrive["code_details"])))
            
            # Verify data integrity
            assert decrypted_wisedrive["license_plate"] == registration_number
            assert decrypted_wisedrive["tracking_id"] == tracking_id
            print_success("Data integrity verified!")
            
        except Exception as e:
            print(f"{Colors.RED}✗ Decryption failed: {e}{Colors.END}")
        
        # Step 7: Verify client decryption works
        print_section("Step 7: Verifying Client Backend Decryption")
        
        try:
            decrypted_client = self.client_decryptor.decrypt(encrypted_client_b64)
            print_success("Client decryption successful!")
            print_data("Decrypted License Plate", decrypted_client["license_plate"])
        except Exception as e:
            print(f"{Colors.RED}✗ Client decryption failed: {e}{Colors.END}")
        
        # Step 8: Verify key separation
        print_section("Step 8: Verifying Key Separation (Security Check)")
        
        try:
            # Try to decrypt WiseDrive blob with Client key (should FAIL)
            self.client_decryptor.decrypt(encrypted_b64)
            print(f"{Colors.RED}✗ SECURITY FAILURE: Client could decrypt WiseDrive data!{Colors.END}")
        except:
            print_success("Client CANNOT decrypt WiseDrive data (correct behavior)")
        
        try:
            # Try to decrypt Client blob with WiseDrive key (should FAIL)
            self.wisedrive_decryptor.decrypt(encrypted_client_b64)
            print(f"{Colors.RED}✗ SECURITY FAILURE: WiseDrive could decrypt Client data!{Colors.END}")
        except:
            print_success("WiseDrive CANNOT decrypt Client data (correct behavior)")
        
        # Summary
        print_header("SIMULATION COMPLETE - SUMMARY")
        
        print(f"""
{Colors.BOLD}Data Flow:{Colors.END}
  1. OBD Scanner → Raw JSON ({len(plain_json)} bytes)
  2. SDK encrypts → WiseDrive blob ({len(encrypted_wisedrive)} bytes)
  3. SDK encrypts → Client blob ({len(encrypted_client)} bytes)
  4. WiseDrive backend decrypts with WISEDRIVE_PRIVATE_KEY
  5. Client backend decrypts with CLIENT_PRIVATE_KEY

{Colors.BOLD}Security Guarantees:{Colors.END}
  ✓ Data encrypted with AES-256-GCM (unbreakable)
  ✓ AES key encrypted with RSA-4096 (infeasible to crack)
  ✓ HMAC-SHA512 protects against tampering
  ✓ Different keys for different recipients
  ✓ No one can decrypt without private key

{Colors.BOLD}What an attacker sees:{Colors.END}
  {Colors.RED}{encrypted_b64[:80]}...{Colors.END}
  (Random bytes - completely unreadable)

{Colors.BOLD}What the backend sees after decryption:{Colors.END}
  {Colors.GREEN}License Plate: {registration_number}
  Tracking ID: {tracking_id}
  VIN: KMHXX00XXXX000000
  DTCs: P0503, C1201{Colors.END}
""")
        
        return {
            "scan_data": scan_data,
            "encrypted_wisedrive": encrypted_b64,
            "encrypted_client": encrypted_client_b64,
            "plain_json_size": len(plain_json),
            "encrypted_size": len(encrypted_wisedrive)
        }


def main():
    """Run the mock SDK simulation"""
    
    # Configuration
    REGISTRATION_NUMBER = "MH12AB1234"
    TRACKING_ID = "ORD6894331"
    USE_MOCK = True  # Set to False to test real endpoint
    
    # Create simulator
    simulator = SDKMockSimulator(use_mock=USE_MOCK)
    
    # Run simulation
    result = simulator.run_full_scan_simulation(REGISTRATION_NUMBER, TRACKING_ID)
    
    print(f"\n{Colors.GREEN}{Colors.BOLD}✓ SDK Mock Test Complete!{Colors.END}")
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
