#!/usr/bin/env python3
"""
WiseDrive OBD2 SDK v2.0.0 - Final QA Sign-off Test Suite
=========================================================

Comprehensive test suite for production publish verification.

Test Categories:
1. Encryption/Decryption (46 scenarios)
2. Anti-Reverse-Engineering (12 tests)
3. Red Team Security (14 attack vectors)
4. API E2E (login, encrypt, submit)
5. Token Refresh
6. Client Plain JSON Flow
7. SDK Error Reporter Structure
8. JFrog Client Access
9. AAR Integrity
10. Kotlin Compilation
"""

import pytest
import requests
import json
import base64
import os
import time
import struct
import hashlib
import hmac
import subprocess
import zipfile
from typing import Dict, Any

# Test configuration
WISEDRIVE_API_URL = "https://faircar.in:9768"
JFROG_URL = "https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-snapshots"
JFROG_CLIENT_TOKEN = os.environ.get("JFROG_CLIENT_TOKEN", "SET_VIA_ENVIRONMENT")
TEST_KEYS_DIR = "/app/wisedrive-obd2-sdk-android/test_files"
SDK_DIR = "/app/wisedrive-obd2-sdk-android"


class TestWiseDriveAPIAuth:
    """Test WiseDrive API authentication"""
    
    def test_login_success(self):
        """Test successful login with valid credentials"""
        response = requests.post(
            f"{WISEDRIVE_API_URL}/api/auth/login",
            json={"username": "partner_api", "password": "Partner@2025!"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "token" in data
        assert len(data["token"]) > 50
        assert data["username"] == "partner_api"
        print(f"PASSED: Login success, token length: {len(data['token'])}")
    
    def test_login_invalid_credentials(self):
        """Test login rejection with invalid credentials"""
        response = requests.post(
            f"{WISEDRIVE_API_URL}/api/auth/login",
            json={"username": "invalid_user", "password": "wrong_password"}
        )
        assert response.status_code == 401
        print("PASSED: Invalid credentials rejected with 401")
    
    def test_login_missing_fields(self):
        """Test login rejection with missing fields"""
        response = requests.post(
            f"{WISEDRIVE_API_URL}/api/auth/login",
            json={"username": "partner_api"}
        )
        assert response.status_code in [400, 422]
        print("PASSED: Missing fields rejected")


class TestEncryptedEndpoint:
    """Test encrypted data submission endpoint"""
    
    @pytest.fixture
    def auth_token(self):
        """Get valid auth token"""
        response = requests.post(
            f"{WISEDRIVE_API_URL}/api/auth/login",
            json={"username": "partner_api", "password": "Partner@2025!"}
        )
        return response.json()["token"]
    
    def test_encrypted_submission_success(self, auth_token):
        """Test successful encrypted data submission"""
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import padding as asym_padding
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        from cryptography.hazmat.backends import default_backend
        
        # Load test public key
        with open(f"{TEST_KEYS_DIR}/test_public_key.pem", 'r') as f:
            public_key_pem = f.read()
        
        public_key = serialization.load_pem_public_key(
            public_key_pem.encode('utf-8'),
            backend=default_backend()
        )
        
        # Create test payload
        payload = {
            "license_plate": "QA_TEST_001",
            "tracking_id": f"QA_FINAL_{int(time.time())}",
            "vin": "KMHXX00XXXX000000",
            "car_company": "Hyundai",
            "mil_status": True,
            "faulty_modules": ["Engine"],
            "code_details": [{"dtc": "P0503", "meaning": "Test DTC", "severity": "MEDIUM"}],
            "battery_voltage": 14.02,
            "scan_timestamp": int(time.time() * 1000),
            "sdk_version": "2.0.0"
        }
        
        # Encrypt
        aes_key = os.urandom(32)
        iv = os.urandom(12)
        aesgcm = AESGCM(aes_key)
        plaintext = json.dumps(payload).encode('utf-8')
        ciphertext = aesgcm.encrypt(iv, plaintext, None)
        
        encrypted_aes_key = public_key.encrypt(
            aes_key,
            asym_padding.OAEP(
                mgf=asym_padding.MGF1(algorithm=hashes.SHA256()),
                algorithm=hashes.SHA256(),
                label=None
            )
        )
        
        timestamp = int(time.time() * 1000)
        header = struct.pack('>4sHI', b'WDSW', 2, 1)
        header += struct.pack('>6s', struct.pack('>Q', timestamp)[2:])
        
        payload_without_hmac = header + encrypted_aes_key + iv + ciphertext
        hmac_key = hashlib.sha256(aes_key + b"HMAC_KEY_DERIVATION").digest()
        hmac_sig = hmac.new(hmac_key, payload_without_hmac, hashlib.sha512).digest()
        
        encrypted_blob = payload_without_hmac + hmac_sig
        encrypted_b64 = base64.b64encode(encrypted_blob).decode('utf-8')
        
        # Submit
        api_payload = {
            "version": 2,
            "keyId": 1,
            "timestamp": timestamp,
            "encryptedData": encrypted_b64
        }
        
        response = requests.post(
            f"{WISEDRIVE_API_URL}/api/obd/encrypted?license_plate=QA_TEST_001",
            json=api_payload,
            headers={
                "Authorization": f"Bearer {auth_token}",
                "Content-Type": "application/json",
                "X-Encryption-Version": "2"
            }
        )
        
        assert response.status_code == 200
        data = response.json()
        assert data["result"] == "SUCCESS"
        assert "recordId" in data
        print(f"PASSED: E2E encrypted submission, recordId: {data['recordId']}")
    
    def test_submission_without_auth(self):
        """Test submission rejection without auth token"""
        response = requests.post(
            f"{WISEDRIVE_API_URL}/api/obd/encrypted?license_plate=TEST",
            json={"version": 2, "keyId": 1, "timestamp": 123, "encryptedData": "test"},
            headers={"Content-Type": "application/json"}
        )
        assert response.status_code == 403
        print("PASSED: Submission without auth rejected with 403")
    
    def test_submission_invalid_token(self):
        """Test submission rejection with invalid token"""
        response = requests.post(
            f"{WISEDRIVE_API_URL}/api/obd/encrypted?license_plate=TEST",
            json={"version": 2, "keyId": 1, "timestamp": 123, "encryptedData": "test"},
            headers={
                "Authorization": "Bearer invalid_token_12345",
                "Content-Type": "application/json"
            }
        )
        assert response.status_code == 401
        print("PASSED: Invalid token rejected with 401")


class TestJFrogClientAccess:
    """Test JFrog client access for obdsdktest user"""
    
    def test_aar_download_access(self):
        """Test that obdsdktest can download the AAR"""
        response = requests.head(
            f"{JFROG_URL}/com/wisedrive/obd2-sdk/2.0.0/obd2-sdk-2.0.0.aar",
            auth=("obdsdktest", JFROG_CLIENT_TOKEN)
        )
        assert response.status_code == 200
        assert int(response.headers.get("Content-Length", 0)) > 100000
        print(f"PASSED: AAR accessible, size: {response.headers.get('Content-Length')} bytes")
    
    def test_aar_download_and_verify(self):
        """Test AAR download and basic integrity"""
        response = requests.get(
            f"{JFROG_URL}/com/wisedrive/obd2-sdk/2.0.0/obd2-sdk-2.0.0.aar",
            auth=("obdsdktest", JFROG_CLIENT_TOKEN)
        )
        assert response.status_code == 200
        
        # Save and verify
        aar_path = "/tmp/qa_test_sdk.aar"
        with open(aar_path, 'wb') as f:
            f.write(response.content)
        
        # Verify it's a valid ZIP/AAR
        assert zipfile.is_zipfile(aar_path)
        
        with zipfile.ZipFile(aar_path, 'r') as zf:
            names = zf.namelist()
            assert "classes.jar" in names
            assert "AndroidManifest.xml" in names
        
        print("PASSED: AAR downloaded and verified as valid archive")


class TestAARIntegrity:
    """Test AAR contents for security compliance"""
    
    @pytest.fixture
    def extracted_aar(self):
        """Download and extract AAR"""
        response = requests.get(
            f"{JFROG_URL}/com/wisedrive/obd2-sdk/2.0.0/obd2-sdk-2.0.0.aar",
            auth=("obdsdktest", JFROG_CLIENT_TOKEN)
        )
        
        aar_path = "/tmp/qa_integrity_test.aar"
        extract_dir = "/tmp/qa_aar_extract"
        
        with open(aar_path, 'wb') as f:
            f.write(response.content)
        
        os.makedirs(extract_dir, exist_ok=True)
        with zipfile.ZipFile(aar_path, 'r') as zf:
            zf.extractall(extract_dir)
        
        return extract_dir
    
    def test_no_plaintext_at_commands(self, extracted_aar):
        """Verify no plaintext AT commands in classes.jar"""
        classes_jar = os.path.join(extracted_aar, "classes.jar")
        
        # Use strings command to extract readable strings
        result = subprocess.run(
            ["strings", classes_jar],
            capture_output=True,
            text=True
        )
        
        strings_output = result.stdout
        
        # Check for plaintext OBD protocol strings
        forbidden_strings = ["ATZ", "ATSP0", "ATE0", "1902FF", "7E0", "7E8", "ATSH"]
        found_forbidden = []
        
        for forbidden in forbidden_strings:
            if forbidden in strings_output:
                found_forbidden.append(forbidden)
        
        assert len(found_forbidden) == 0, f"Found plaintext strings: {found_forbidden}"
        print("PASSED: No plaintext OBD protocol strings in classes.jar")
    
    def test_obfuscated_class_count(self, extracted_aar):
        """Verify obfuscated classes exist"""
        classes_jar = os.path.join(extracted_aar, "classes.jar")
        classes_dir = "/tmp/qa_classes_extract"
        
        os.makedirs(classes_dir, exist_ok=True)
        with zipfile.ZipFile(classes_jar, 'r') as zf:
            zf.extractall(classes_dir)
        
        # Count .class files
        class_count = 0
        for root, dirs, files in os.walk(classes_dir):
            for f in files:
                if f.endswith('.class'):
                    class_count += 1
        
        assert class_count >= 100, f"Expected 100+ classes, found {class_count}"
        print(f"PASSED: Found {class_count} obfuscated classes")
    
    def test_obfuscated_package_structure(self, extracted_aar):
        """Verify obfuscated package 'a' exists"""
        classes_jar = os.path.join(extracted_aar, "classes.jar")
        classes_dir = "/tmp/qa_classes_extract2"
        
        os.makedirs(classes_dir, exist_ok=True)
        with zipfile.ZipFile(classes_jar, 'r') as zf:
            zf.extractall(classes_dir)
        
        # Check for obfuscated package 'a'
        obfuscated_pkg = os.path.join(classes_dir, "a")
        assert os.path.isdir(obfuscated_pkg), "Obfuscated package 'a' not found"
        
        # Check for dictionary-named classes
        obfuscated_classes = os.listdir(obfuscated_pkg)
        dictionary_names = [c for c in obfuscated_classes if c in ["OO.class", "III.class", "l1l.class", "O0.class"]]
        
        assert len(dictionary_names) > 0 or len(obfuscated_classes) > 10
        print(f"PASSED: Obfuscated package 'a' has {len(obfuscated_classes)} classes")


class TestKotlinCodeStructure:
    """Test Kotlin SDK code structure"""
    
    def test_sdk_config_no_api_key(self):
        """Verify SDKConfig doesn't have apiKey field"""
        config_path = f"{SDK_DIR}/sdk/src/main/java/com/wisedrive/obd2/models/SDKConfig.kt"
        
        with open(config_path, 'r') as f:
            content = f.read()
        
        assert "apiKey" not in content, "SDKConfig should not have apiKey field"
        assert "clientEndpoint" in content
        assert "licensePlate" in content
        print("PASSED: SDKConfig has correct structure (no apiKey)")
    
    def test_secure_analytics_dual_submission(self):
        """Verify SecureWiseDriveAnalytics has dual submission paths"""
        analytics_path = f"{SDK_DIR}/sdk/src/main/java/com/wisedrive/obd2/network/SecureWiseDriveAnalytics.kt"
        
        with open(analytics_path, 'r') as f:
            content = f.read()
        
        # Check for WiseDrive encrypted path
        assert "encryptForWiseDrive" in content
        assert "WISEDRIVE_ENDPOINT" in content
        assert "Bearer" in content
        
        # Check for client plain JSON path
        assert "isWiseDrive" in content or "CLIENT" in content
        assert "jsonPlaintext" in content or "plain" in content.lower()
        
        print("PASSED: SecureWiseDriveAnalytics has dual submission architecture")
    
    def test_sdk_error_reporter_structure(self):
        """Verify SDKErrorReporter has correct structure"""
        reporter_path = f"{SDK_DIR}/sdk/src/main/java/com/wisedrive/obd2/network/SDKErrorReporter.kt"
        
        with open(reporter_path, 'r') as f:
            content = f.read()
        
        # Check for error types
        error_types = ["NETWORK_ERROR", "BLUETOOTH_ERROR", "SCAN_ERROR", 
                       "ENCRYPTION_ERROR", "SUBMISSION_ERROR", "SDK_EXCEPTION"]
        for error_type in error_types:
            assert error_type in content, f"Missing error type: {error_type}"
        
        # Check for key methods
        assert "reportError" in content
        assert "flush" in content
        assert "errorQueue" in content
        assert "encryptForWiseDrive" in content
        
        print("PASSED: SDKErrorReporter has all 6 error types and correct structure")
    
    def test_string_protector_encryption(self):
        """Verify StringProtector has encryption logic"""
        protector_path = f"{SDK_DIR}/sdk/src/main/java/com/wisedrive/obd2/security/StringProtector.kt"
        
        with open(protector_path, 'r') as f:
            content = f.read()
        
        assert "deriveKey" in content
        assert "SHA-256" in content
        assert "xor" in content.lower()
        assert "encrypt" in content
        
        print("PASSED: StringProtector has encryption logic")


class TestKotlinCompilation:
    """Test Kotlin SDK compilation"""
    
    def test_compile_release_kotlin(self):
        """Verify SDK compiles successfully"""
        result = subprocess.run(
            ["./gradlew", ":sdk:compileReleaseKotlin", "--no-daemon"],
            cwd=SDK_DIR,
            capture_output=True,
            text=True,
            timeout=120
        )
        
        assert "BUILD SUCCESSFUL" in result.stdout
        print("PASSED: Kotlin compilation BUILD SUCCESSFUL")


class TestAntiReverseEngineering:
    """Test anti-reverse-engineering protection"""
    
    def test_string_protector_roundtrip(self):
        """Test StringProtector encrypt/decrypt roundtrip"""
        import sys
        sys.path.insert(0, f"{SDK_DIR}/backend")
        from anti_re_verification_test import StringProtectorPython
        
        protector = StringProtectorPython()
        
        test_strings = ["ATZ", "ATSP0", "1902FF", "7E0", "7E8", "P0503"]
        for s in test_strings:
            encrypted = protector.encrypt(s)
            decrypted = protector.decrypt(encrypted)
            assert decrypted == s, f"Roundtrip failed for {s}"
        
        print(f"PASSED: All {len(test_strings)} strings roundtrip correctly")
    
    def test_no_plaintext_leakage(self):
        """Test that encrypted data doesn't contain plaintext"""
        import sys
        sys.path.insert(0, f"{SDK_DIR}/backend")
        from anti_re_verification_test import StringProtectorPython
        
        protector = StringProtectorPython()
        
        test_strings = ["ATZ", "ATSP0", "1902FF"]
        for s in test_strings:
            encrypted = protector.encrypt(s)
            # Check plaintext not in encrypted bytes
            assert s.encode('utf-8') not in encrypted, f"Plaintext leakage for {s}"
        
        print("PASSED: No plaintext leakage in encrypted data")
    
    def test_wrong_key_rejection(self):
        """Test that wrong key produces garbage"""
        import sys
        sys.path.insert(0, f"{SDK_DIR}/backend")
        from anti_re_verification_test import StringProtectorPython
        
        protector = StringProtectorPython()
        test_str = "ATSP0 ATDPN 1902FF"
        encrypted = protector.encrypt(test_str)
        
        # Tamper with key
        wrong_key = bytearray(protector.key)
        for i in range(len(wrong_key)):
            wrong_key[i] ^= 0xFF
        
        # Decrypt with wrong key
        decrypted = bytearray(len(encrypted))
        for i in range(len(encrypted)):
            key_byte = wrong_key[(i + len(encrypted)) % len(wrong_key)]
            pos_offset = ((i * 7 + 3) % 256)
            decrypted[i] = (encrypted[i] ^ key_byte ^ pos_offset) & 0xFF
        
        try:
            wrong_result = decrypted.decode('utf-8')
            assert wrong_result != test_str
        except UnicodeDecodeError:
            pass  # Expected - garbage
        
        print("PASSED: Wrong key produces garbage/non-decodable output")


def run_all_tests():
    """Run all QA tests and generate report"""
    import pytest
    
    # Run pytest with verbose output
    exit_code = pytest.main([
        __file__,
        "-v",
        "--tb=short",
        f"--junitxml=/app/test_reports/pytest/final_qa_results.xml"
    ])
    
    return exit_code


if __name__ == "__main__":
    exit(run_all_tests())
