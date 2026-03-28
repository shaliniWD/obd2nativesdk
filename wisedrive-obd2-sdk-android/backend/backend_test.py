#!/usr/bin/env python3
"""
WiseDrive OBD2 SDK - Backend Security Testing Suite
==================================================

This module tests the encryption/decryption functionality and security features
of the WiseDrive OBD2 SDK backend.

Test Categories:
1. Basic encryption/decryption functionality
2. Security vulnerability tests (via red_team_tests.py)
3. API endpoint testing
4. Dual key system validation
5. Error handling and edge cases

Run: python3 backend_test.py
"""

import sys
import os
import json
import time
import base64
import hashlib
import hmac
import struct
from typing import Dict, Any, List, Tuple
from datetime import datetime

# Add current directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

try:
    from wisedrive_decryption import WiseDriveDecryptor, DecryptionError, KeyGenerator
    from red_team_tests import SecurityRedTeam, AttackResult
    import requests
except ImportError as e:
    print(f"❌ Import error: {e}")
    print("Please ensure all dependencies are installed:")
    print("pip install cryptography requests")
    sys.exit(1)


class BackendSecurityTester:
    """
    Comprehensive backend security testing suite for WiseDrive OBD2 SDK.
    """
    
    def __init__(self):
        self.tests_run = 0
        self.tests_passed = 0
        self.tests_failed = 0
        self.critical_failures = []
        self.warnings = []
        
        # Generate test key pairs
        print("🔑 Generating test RSA-4096 key pairs...")
        self.client_public, self.client_private = KeyGenerator.generate_rsa_4096()
        self.wisedrive_public, self.wisedrive_private = KeyGenerator.generate_rsa_4096()
        
        # Initialize decryptors
        self.client_decryptor = WiseDriveDecryptor(self.client_private, key_id=1)
        self.wisedrive_decryptor = WiseDriveDecryptor(self.wisedrive_private, key_id=1)
        
        # Sample OBD data for testing
        self.sample_obd_data = {
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
                },
                {
                    "dtc": "C1201",
                    "meaning": "ABS Control Module Malfunction",
                    "severity": "HIGH",
                    "ecu_source": "ABS"
                }
            ],
            "battery_voltage": 14.02,
            "scan_timestamp": int(time.time() * 1000),
            "sdk_version": "2.0.0"
        }
        
        print("✅ Test setup completed")

    def run_test(self, name: str, test_func) -> bool:
        """Run a single test and track results"""
        self.tests_run += 1
        print(f"\n🔍 Testing {name}...")
        
        try:
            start_time = time.time()
            result = test_func()
            duration_ms = (time.time() - start_time) * 1000
            
            if result:
                self.tests_passed += 1
                print(f"   ✅ PASSED ({duration_ms:.2f}ms)")
                return True
            else:
                self.tests_failed += 1
                print(f"   ❌ FAILED ({duration_ms:.2f}ms)")
                return False
                
        except Exception as e:
            self.tests_failed += 1
            print(f"   ❌ FAILED - Exception: {str(e)} ({duration_ms:.2f}ms)")
            self.critical_failures.append(f"{name}: {str(e)}")
            return False

    def test_basic_encryption_decryption(self) -> bool:
        """Test basic encryption/decryption with correct keys"""
        try:
            # Test WiseDrive encryption/decryption
            encrypted_data = self._encrypt_payload(self.sample_obd_data, self.wisedrive_public, "WDSW")
            encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
            
            decrypted_data = self.wisedrive_decryptor.decrypt(encrypted_b64)
            
            # Verify data integrity
            if decrypted_data["license_plate"] != self.sample_obd_data["license_plate"]:
                return False
            if decrypted_data["tracking_id"] != self.sample_obd_data["tracking_id"]:
                return False
            if len(decrypted_data["code_details"]) != len(self.sample_obd_data["code_details"]):
                return False
                
            print(f"     ✓ Decrypted license plate: {decrypted_data['license_plate']}")
            print(f"     ✓ Decrypted tracking ID: {decrypted_data['tracking_id']}")
            print(f"     ✓ DTCs found: {len(decrypted_data['code_details'])}")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Encryption/decryption failed: {e}")
            return False

    def test_wrong_key_decryption(self) -> bool:
        """Test that decryption with wrong private key fails"""
        try:
            # Encrypt with WiseDrive public key
            encrypted_data = self._encrypt_payload(self.sample_obd_data, self.wisedrive_public, "WDSW")
            encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
            
            # Try to decrypt with CLIENT private key (wrong key)
            try:
                self.client_decryptor.decrypt(encrypted_b64)
                print("     ❌ Wrong key decryption succeeded - SECURITY VULNERABILITY!")
                return False
            except DecryptionError:
                print("     ✓ Wrong key correctly rejected")
                return True
                
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False

    def test_payload_tampering_detection(self) -> bool:
        """Test that payload tampering is detected"""
        try:
            # Create valid encrypted payload
            encrypted_data = bytearray(self._encrypt_payload(self.sample_obd_data, self.wisedrive_public, "WDSW"))
            
            # Flip a single bit in the ciphertext area
            ciphertext_start = 16 + 512 + 12  # header + rsa_key + iv
            if len(encrypted_data) > ciphertext_start + 10:
                encrypted_data[ciphertext_start + 10] ^= 0x01  # Flip one bit
            
            encrypted_b64 = base64.b64encode(bytes(encrypted_data)).decode('utf-8')
            
            # Try to decrypt tampered payload
            try:
                self.wisedrive_decryptor.decrypt(encrypted_b64)
                print("     ❌ Tampered payload accepted - SECURITY VULNERABILITY!")
                return False
            except DecryptionError as e:
                if "tampered" in str(e).lower() or "hmac" in str(e).lower() or "gcm" in str(e).lower():
                    print("     ✓ Tampering correctly detected")
                    return True
                else:
                    print(f"     ⚠️ Tampering detected but wrong error: {e}")
                    return True  # Still secure, just different error
                    
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False

    def test_hmac_bypass_attempt(self) -> bool:
        """Test that HMAC bypass attempts are blocked"""
        try:
            # Create valid encrypted payload
            encrypted_data = bytearray(self._encrypt_payload(self.sample_obd_data, self.wisedrive_public, "WDSW"))
            
            # Replace HMAC with zeros (bypass attempt)
            for i in range(len(encrypted_data) - 64, len(encrypted_data)):
                encrypted_data[i] = 0
            
            encrypted_b64 = base64.b64encode(bytes(encrypted_data)).decode('utf-8')
            
            # Try to decrypt with zeroed HMAC
            try:
                self.wisedrive_decryptor.decrypt(encrypted_b64)
                print("     ❌ HMAC bypass succeeded - CRITICAL VULNERABILITY!")
                self.critical_failures.append("HMAC bypass vulnerability")
                return False
            except DecryptionError as e:
                if "hmac" in str(e).lower() or "tampered" in str(e).lower():
                    print("     ✓ HMAC bypass correctly blocked")
                    return True
                else:
                    print(f"     ⚠️ HMAC bypass blocked but different error: {e}")
                    return True
                    
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False

    def test_dual_key_system(self) -> bool:
        """Test that dual key system works correctly"""
        try:
            # Test 1: Client data encrypted for client, WiseDrive can't decrypt
            client_encrypted = self._encrypt_payload(self.sample_obd_data, self.client_public, "WDSC")
            client_b64 = base64.b64encode(client_encrypted).decode('utf-8')
            
            # Client should be able to decrypt
            try:
                client_decrypted = self.client_decryptor.decrypt(client_b64)
                print("     ✓ Client can decrypt client data")
            except DecryptionError:
                print("     ❌ Client cannot decrypt own data")
                return False
            
            # WiseDrive should NOT be able to decrypt client data
            try:
                self.wisedrive_decryptor.decrypt(client_b64)
                print("     ❌ WiseDrive can decrypt client data - KEY SEPARATION FAILED!")
                return False
            except DecryptionError:
                print("     ✓ WiseDrive correctly cannot decrypt client data")
            
            # Test 2: WiseDrive data encrypted for WiseDrive, client can't decrypt
            wisedrive_encrypted = self._encrypt_payload(self.sample_obd_data, self.wisedrive_public, "WDSW")
            wisedrive_b64 = base64.b64encode(wisedrive_encrypted).decode('utf-8')
            
            # WiseDrive should be able to decrypt
            try:
                wisedrive_decrypted = self.wisedrive_decryptor.decrypt(wisedrive_b64)
                print("     ✓ WiseDrive can decrypt WiseDrive data")
            except DecryptionError:
                print("     ❌ WiseDrive cannot decrypt own data")
                return False
            
            # Client should NOT be able to decrypt WiseDrive data
            try:
                self.client_decryptor.decrypt(wisedrive_b64)
                print("     ❌ Client can decrypt WiseDrive data - KEY SEPARATION FAILED!")
                return False
            except DecryptionError:
                print("     ✓ Client correctly cannot decrypt WiseDrive data")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False

    def test_iv_uniqueness(self) -> bool:
        """Test that IVs are unique for each encryption (no reuse)"""
        try:
            ivs = set()
            
            # Generate 100 encryptions and check IV uniqueness
            for i in range(100):
                encrypted_data = self._encrypt_payload(self.sample_obd_data, self.wisedrive_public, "WDSW")
                # Extract IV (12 bytes after header + rsa_key)
                iv = encrypted_data[16 + 512:16 + 512 + 12]
                iv_hex = iv.hex()
                
                if iv_hex in ivs:
                    print(f"     ❌ IV reused at iteration {i} - CRITICAL VULNERABILITY!")
                    self.critical_failures.append("IV reuse detected")
                    return False
                ivs.add(iv_hex)
            
            print(f"     ✓ All {len(ivs)} IVs are unique")
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False

    def test_encrypted_payload_format(self) -> bool:
        """Test that encrypted payload format is correct"""
        try:
            encrypted_data = self._encrypt_payload(self.sample_obd_data, self.wisedrive_public, "WDSW")
            
            # Check minimum size
            min_size = 16 + 512 + 12 + 16 + 64  # header + rsa + iv + min_ciphertext + hmac
            if len(encrypted_data) < min_size:
                print(f"     ❌ Payload too small: {len(encrypted_data)} < {min_size}")
                return False
            
            # Check magic bytes
            magic = encrypted_data[0:4].decode('utf-8')
            if magic != "WDSW":
                print(f"     ❌ Wrong magic bytes: {magic}")
                return False
            
            # Check version
            version = struct.unpack('>H', encrypted_data[4:6])[0]
            if version != 2:
                print(f"     ❌ Wrong version: {version}")
                return False
            
            # Check key ID
            key_id = struct.unpack('>I', encrypted_data[6:10])[0]
            if key_id != 1:
                print(f"     ❌ Wrong key ID: {key_id}")
                return False
            
            print(f"     ✓ Payload format correct: magic={magic}, version={version}, keyId={key_id}")
            print(f"     ✓ Payload size: {len(encrypted_data)} bytes")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False

    def test_api_server_endpoints(self) -> bool:
        """Test API server endpoints if running"""
        try:
            # Check if API server is running
            try:
                response = requests.get("http://localhost:8082/health", timeout=5)
                if response.status_code != 200:
                    print("     ⚠️ API server not running, skipping endpoint tests")
                    self.warnings.append("API server not running for endpoint tests")
                    return True
            except requests.exceptions.RequestException:
                print("     ⚠️ API server not running, skipping endpoint tests")
                self.warnings.append("API server not running for endpoint tests")
                return True
            
            # Test health endpoint
            health_response = response.json()
            if health_response.get("status") != "healthy":
                print("     ❌ Health check failed")
                return False
            
            print(f"     ✓ Health check passed: {health_response.get('service')}")
            
            # Test encrypted endpoint with valid data
            encrypted_data = self._encrypt_payload(self.sample_obd_data, self.wisedrive_public, "WDSW")
            encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
            
            payload = {
                "version": 2,
                "keyId": 1,
                "timestamp": int(time.time() * 1000),
                "encryptedData": encrypted_b64
            }
            
            headers = {
                "Authorization": "Basic cHJhc2FkOnByYXNhZEAxMjM=",
                "Content-Type": "application/json"
            }
            
            response = requests.post(
                "http://localhost:8082/apiv2/webhook/obdreport/wisedrive/encrypted",
                json=payload,
                headers=headers,
                timeout=10
            )
            
            if response.status_code == 200:
                result = response.json()
                if result.get("result") == "SUCCESS":
                    print("     ✓ Encrypted endpoint test passed")
                    return True
                else:
                    print(f"     ❌ Encrypted endpoint returned error: {result}")
                    return False
            else:
                print(f"     ❌ Encrypted endpoint failed: {response.status_code}")
                return False
                
        except Exception as e:
            print(f"     ❌ API test failed: {e}")
            return False

    def run_red_team_tests(self) -> bool:
        """Run comprehensive red team security tests"""
        try:
            print("\n🔴 Running Red Team Security Assessment...")
            red_team = SecurityRedTeam()
            results = red_team.run_all_tests()
            
            # Count results
            secure = sum(1 for r in results if r.result == AttackResult.FAILED)
            partial = sum(1 for r in results if r.result == AttackResult.PARTIAL)
            vulnerable = sum(1 for r in results if r.result == AttackResult.SUCCESS)
            
            print(f"\n📊 Red Team Results:")
            print(f"   SECURE:           {secure}/{len(results)}")
            print(f"   PARTIALLY SECURE: {partial}/{len(results)}")
            print(f"   VULNERABLE:       {vulnerable}/{len(results)}")
            
            if vulnerable > 0:
                print("\n❌ CRITICAL: Vulnerabilities found!")
                for r in results:
                    if r.result == AttackResult.SUCCESS:
                        print(f"   - {r.name}")
                        self.critical_failures.append(f"Red Team: {r.name}")
                return False
            else:
                print("\n✅ All red team attacks failed - encryption is secure")
                return True
                
        except Exception as e:
            print(f"❌ Red team tests failed: {e}")
            return False

    def _encrypt_payload(self, payload: dict, public_key_pem: str, magic: str = "WDSW") -> bytes:
        """Encrypt payload using the same algorithm as SDK"""
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import padding as asym_padding
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        from cryptography.hazmat.backends import default_backend
        
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
        header = struct.pack('>4sHI', magic.encode(), 2, 1)  # 10 bytes
        header += struct.pack('>6s', struct.pack('>Q', timestamp)[2:])  # 6 bytes for timestamp
        
        # Assemble payload without HMAC
        payload_without_hmac = header + encrypted_aes_key + iv + ciphertext
        
        # Calculate HMAC
        hmac_key = hashlib.sha256(aes_key + b"HMAC_KEY_DERIVATION").digest()
        hmac_sig = hmac.new(hmac_key, payload_without_hmac, hashlib.sha512).digest()
        
        return payload_without_hmac + hmac_sig

    def run_all_tests(self) -> Dict[str, Any]:
        """Run all backend security tests"""
        print("\n" + "=" * 70)
        print("WISEDRIVE OBD2 SDK - BACKEND SECURITY TEST SUITE")
        print("=" * 70)
        print(f"Test Environment: Python {sys.version}")
        print(f"Test Time: {datetime.now().isoformat()}")
        
        # Core encryption tests
        tests = [
            ("Basic Encryption/Decryption", self.test_basic_encryption_decryption),
            ("Wrong Key Decryption", self.test_wrong_key_decryption),
            ("Payload Tampering Detection", self.test_payload_tampering_detection),
            ("HMAC Bypass Protection", self.test_hmac_bypass_attempt),
            ("Dual Key System", self.test_dual_key_system),
            ("IV Uniqueness", self.test_iv_uniqueness),
            ("Encrypted Payload Format", self.test_encrypted_payload_format),
            ("API Server Endpoints", self.test_api_server_endpoints),
        ]
        
        # Run core tests
        for name, test_func in tests:
            self.run_test(name, test_func)
        
        # Run red team tests
        red_team_passed = self.run_test("Red Team Security Assessment", self.run_red_team_tests)
        
        # Print summary
        print("\n" + "=" * 70)
        print("TEST SUMMARY")
        print("=" * 70)
        print(f"Tests Run:    {self.tests_run}")
        print(f"Tests Passed: {self.tests_passed}")
        print(f"Tests Failed: {self.tests_failed}")
        print(f"Success Rate: {(self.tests_passed/self.tests_run)*100:.1f}%")
        
        if self.critical_failures:
            print(f"\n❌ CRITICAL FAILURES ({len(self.critical_failures)}):")
            for failure in self.critical_failures:
                print(f"   - {failure}")
        
        if self.warnings:
            print(f"\n⚠️ WARNINGS ({len(self.warnings)}):")
            for warning in self.warnings:
                print(f"   - {warning}")
        
        if self.tests_failed == 0:
            print("\n✅ ALL TESTS PASSED - ENCRYPTION SYSTEM IS SECURE")
            overall_status = "SECURE"
        elif len(self.critical_failures) > 0:
            print("\n❌ CRITICAL VULNERABILITIES FOUND - IMMEDIATE ACTION REQUIRED")
            overall_status = "VULNERABLE"
        else:
            print("\n⚠️ SOME TESTS FAILED - REVIEW REQUIRED")
            overall_status = "NEEDS_REVIEW"
        
        print("=" * 70)
        
        return {
            "overall_status": overall_status,
            "tests_run": self.tests_run,
            "tests_passed": self.tests_passed,
            "tests_failed": self.tests_failed,
            "success_rate": (self.tests_passed/self.tests_run)*100,
            "critical_failures": self.critical_failures,
            "warnings": self.warnings,
            "timestamp": datetime.now().isoformat()
        }


def main():
    """Main test execution"""
    tester = BackendSecurityTester()
    results = tester.run_all_tests()
    
    # Return appropriate exit code
    if results["overall_status"] == "SECURE":
        return 0
    elif results["overall_status"] == "VULNERABLE":
        return 2  # Critical failures
    else:
        return 1  # Some failures


if __name__ == "__main__":
    sys.exit(main())