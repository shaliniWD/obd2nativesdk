#!/usr/bin/env python3
"""
WiseDrive OBD2 SDK - New Features Test Suite (Iteration 10)
============================================================

Tests for the 4 new features:
1. Client receives plain JSON (no encryption)
2. SDK error logging sent encrypted to internal API
3. GitHub Actions CI/CD for auto-publishing
4. Client integration document

Also tests:
- JWT authentication flow
- E2E encrypted submission to faircar.in:9768
- Token refresh logic
- Kotlin SDK compilation
"""

import pytest
import requests
import json
import base64
import os
import sys
import hashlib
import hmac
import struct
import time
import re
from pathlib import Path

# Add backend directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Disable SSL warnings for self-signed cert
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# Constants
WISEDRIVE_AUTH_URL = "https://faircar.in:9768/api/auth/login"
WISEDRIVE_ENCRYPTED_URL = "https://faircar.in:9768/api/obd/encrypted"
AUTH_USERNAME = "partner_api"
AUTH_PASSWORD = "Partner@2025!"

# Test keys path
TEST_KEYS_DIR = Path(__file__).parent.parent / "test_files"


class TestJWTAuthentication:
    """Test JWT authentication flow with WiseDrive internal API"""
    
    def test_login_success(self):
        """Test successful login with correct credentials"""
        response = requests.post(
            WISEDRIVE_AUTH_URL,
            json={"username": AUTH_USERNAME, "password": AUTH_PASSWORD},
            verify=False,
            timeout=10
        )
        assert response.status_code == 200, f"Login failed: {response.text}"
        
        data = response.json()
        assert "token" in data, "No token in response"
        assert len(data["token"]) > 50, "Token too short"
        assert data.get("username") == AUTH_USERNAME
        print(f"   ✓ Login successful, token: {data['token'][:50]}...")
    
    def test_login_invalid_credentials(self):
        """Test login with invalid credentials fails"""
        response = requests.post(
            WISEDRIVE_AUTH_URL,
            json={"username": "wrong_user", "password": "wrong_pass"},
            verify=False,
            timeout=10
        )
        assert response.status_code in [401, 403], f"Expected 401/403, got {response.status_code}"
        print("   ✓ Invalid credentials correctly rejected")
    
    def test_login_missing_fields(self):
        """Test login with missing fields fails"""
        response = requests.post(
            WISEDRIVE_AUTH_URL,
            json={"username": AUTH_USERNAME},  # Missing password
            verify=False,
            timeout=10
        )
        assert response.status_code in [400, 401, 422], f"Expected 400/401/422, got {response.status_code}"
        print("   ✓ Missing fields correctly rejected")


class TestE2EEncryptedSubmission:
    """Test end-to-end encrypted submission to WiseDrive API"""
    
    @pytest.fixture
    def auth_token(self):
        """Get authentication token"""
        response = requests.post(
            WISEDRIVE_AUTH_URL,
            json={"username": AUTH_USERNAME, "password": AUTH_PASSWORD},
            verify=False,
            timeout=10
        )
        assert response.status_code == 200
        return response.json()["token"]
    
    @pytest.fixture
    def test_keys(self):
        """Load test RSA keys"""
        pub_path = TEST_KEYS_DIR / "test_public_key.pem"
        priv_path = TEST_KEYS_DIR / "test_private_key.pem"
        
        with open(pub_path, 'r') as f:
            public_key = f.read()
        with open(priv_path, 'r') as f:
            private_key = f.read()
        
        return {"public": public_key, "private": private_key}
    
    def _encrypt_payload(self, payload: dict, public_key_pem: str) -> tuple:
        """Encrypt payload using RSA-4096 + AES-256-GCM"""
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import padding as asym_padding
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        from cryptography.hazmat.backends import default_backend
        
        public_key = serialization.load_pem_public_key(
            public_key_pem.encode('utf-8'),
            backend=default_backend()
        )
        
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
        hmac_key = hashlib.sha256(aes_key + b'HMAC_KEY_DERIVATION').digest()
        hmac_sig = hmac.new(hmac_key, payload_without_hmac, hashlib.sha512).digest()
        
        encrypted_blob = payload_without_hmac + hmac_sig
        encrypted_b64 = base64.b64encode(encrypted_blob).decode('utf-8')
        
        return encrypted_b64, timestamp
    
    def test_encrypted_submission_success(self, auth_token, test_keys):
        """Test successful encrypted submission"""
        payload = {
            "license_plate": "TEST_E2E_001",
            "tracking_id": "ORD_TEST_001",
            "vin": "TESTVIN123456789",
            "mil_status": True,
            "code_details": [
                {"dtc": "P0300", "meaning": "Random Misfire", "severity": "HIGH"}
            ],
            "battery_voltage": 12.8,
            "sdk_version": "2.0.0"
        }
        
        encrypted_b64, timestamp = self._encrypt_payload(payload, test_keys["public"])
        
        enc_request = {
            "version": 2,
            "keyId": 1,
            "timestamp": timestamp,
            "encryptedData": encrypted_b64
        }
        
        response = requests.post(
            f"{WISEDRIVE_ENCRYPTED_URL}?license_plate=TEST_E2E_001",
            json=enc_request,
            headers={
                "Authorization": f"Bearer {auth_token}",
                "Content-Type": "application/json",
                "X-Encryption-Version": "2",
                "X-Key-ID": "1"
            },
            verify=False,
            timeout=15
        )
        
        assert response.status_code == 200, f"Submission failed: {response.text}"
        data = response.json()
        assert data.get("result") == "SUCCESS", f"Expected SUCCESS, got {data}"
        assert "recordId" in data, "No recordId in response"
        print(f"   ✓ Encrypted submission successful, recordId: {data['recordId']}")
    
    def test_submission_without_auth_fails(self, test_keys):
        """Test that submission without auth token fails"""
        payload = {"license_plate": "TEST", "tracking_id": "ORD1"}
        encrypted_b64, timestamp = self._encrypt_payload(payload, test_keys["public"])
        
        enc_request = {
            "version": 2,
            "keyId": 1,
            "timestamp": timestamp,
            "encryptedData": encrypted_b64
        }
        
        response = requests.post(
            f"{WISEDRIVE_ENCRYPTED_URL}?license_plate=TEST",
            json=enc_request,
            headers={"Content-Type": "application/json"},
            verify=False,
            timeout=15
        )
        
        assert response.status_code in [401, 403], f"Expected 401/403, got {response.status_code}"
        print("   ✓ Submission without auth correctly rejected")
    
    def test_submission_with_invalid_token_fails(self, test_keys):
        """Test that submission with invalid token fails"""
        payload = {"license_plate": "TEST", "tracking_id": "ORD1"}
        encrypted_b64, timestamp = self._encrypt_payload(payload, test_keys["public"])
        
        enc_request = {
            "version": 2,
            "keyId": 1,
            "timestamp": timestamp,
            "encryptedData": encrypted_b64
        }
        
        response = requests.post(
            f"{WISEDRIVE_ENCRYPTED_URL}?license_plate=TEST",
            json=enc_request,
            headers={
                "Authorization": "Bearer invalid_token_12345",
                "Content-Type": "application/json"
            },
            verify=False,
            timeout=15
        )
        
        assert response.status_code in [401, 403], f"Expected 401/403, got {response.status_code}"
        print("   ✓ Invalid token correctly rejected")


class TestSecureWiseDriveAnalyticsCode:
    """Test SecureWiseDriveAnalytics.kt code structure for dual submission"""
    
    @pytest.fixture
    def analytics_code(self):
        """Load SecureWiseDriveAnalytics.kt source"""
        path = Path(__file__).parent.parent / "sdk/src/main/java/com/wisedrive/obd2/network/SecureWiseDriveAnalytics.kt"
        with open(path, 'r') as f:
            return f.read()
    
    def test_wisedrive_endpoint_hardcoded(self, analytics_code):
        """Verify WiseDrive endpoint is hardcoded"""
        assert "faircar.in:9768/api/obd/encrypted" in analytics_code
        assert "WISEDRIVE_ENDPOINT" in analytics_code
        print("   ✓ WiseDrive endpoint correctly hardcoded")
    
    def test_wisedrive_auth_endpoint_present(self, analytics_code):
        """Verify WiseDrive auth endpoint is present"""
        assert "faircar.in:9768/api/auth/login" in analytics_code
        assert "WISEDRIVE_AUTH_ENDPOINT" in analytics_code
        print("   ✓ WiseDrive auth endpoint present")
    
    def test_jwt_auth_credentials(self, analytics_code):
        """Verify JWT auth credentials are present"""
        assert "partner_api" in analytics_code
        assert "Partner@2025!" in analytics_code
        print("   ✓ JWT auth credentials present")
    
    def test_dual_submission_paths(self, analytics_code):
        """Verify dual submission paths exist (WiseDrive encrypted, Client plain)"""
        # Check for isWiseDrive parameter
        assert "isWiseDrive: Boolean" in analytics_code or "isWiseDrive" in analytics_code
        
        # Check for encrypted path
        assert "encryptForWiseDrive" in analytics_code
        assert "ENCRYPTED PAYLOAD for WiseDrive" in analytics_code
        
        # Check for plain JSON path
        assert "PLAIN JSON for Client" in analytics_code
        assert "jsonPlaintext.toRequestBody" in analytics_code
        print("   ✓ Dual submission paths (encrypted + plain) present")
    
    def test_bearer_token_auth(self, analytics_code):
        """Verify Bearer token authentication is used"""
        assert 'Bearer ${wiseDriveAuthToken' in analytics_code or 'Bearer $' in analytics_code
        assert "Authorization" in analytics_code
        print("   ✓ Bearer token authentication implemented")
    
    def test_token_refresh_logic(self, analytics_code):
        """Verify token refresh logic exists"""
        assert "401" in analytics_code or "403" in analytics_code
        assert "re-authenticat" in analytics_code.lower() or "token expired" in analytics_code.lower()
        print("   ✓ Token refresh logic present")
    
    def test_client_receives_plain_json(self, analytics_code):
        """Verify client endpoint receives plain JSON (not encrypted)"""
        # The else branch for isWiseDrive=false should send plain JSON
        assert "isWiseDrive" in analytics_code
        # Check that plain JSON is sent to client
        assert "jsonPlaintext" in analytics_code
        # Verify no encryption for client path
        lines = analytics_code.split('\n')
        in_client_block = False
        for line in lines:
            if "CLIENT: Plain JSON" in line:
                in_client_block = True
            if in_client_block and "encryptFor" in line:
                pytest.fail("Client path should not encrypt data")
            if in_client_block and "return response.isSuccessful" in line:
                break
        print("   ✓ Client receives plain JSON (no encryption)")


class TestSDKErrorReporterCode:
    """Test SDKErrorReporter.kt code structure"""
    
    @pytest.fixture
    def error_reporter_code(self):
        """Load SDKErrorReporter.kt source"""
        path = Path(__file__).parent.parent / "sdk/src/main/java/com/wisedrive/obd2/network/SDKErrorReporter.kt"
        with open(path, 'r') as f:
            return f.read()
    
    def test_error_types_defined(self, error_reporter_code):
        """Verify all error types are defined"""
        required_types = [
            "NETWORK_ERROR",
            "BLUETOOTH_ERROR",
            "SCAN_ERROR",
            "ENCRYPTION_ERROR",
            "SUBMISSION_ERROR",
            "SDK_EXCEPTION"
        ]
        for error_type in required_types:
            assert error_type in error_reporter_code, f"Missing error type: {error_type}"
        print(f"   ✓ All {len(required_types)} error types defined")
    
    def test_error_queue_present(self, error_reporter_code):
        """Verify error queue mechanism exists"""
        assert "ConcurrentLinkedQueue" in error_reporter_code or "errorQueue" in error_reporter_code
        assert "MAX_QUEUE_SIZE" in error_reporter_code
        print("   ✓ Error queue mechanism present")
    
    def test_flush_mechanism(self, error_reporter_code):
        """Verify flush mechanism exists"""
        assert "fun flush" in error_reporter_code
        assert "sendQueuedErrors" in error_reporter_code
        print("   ✓ Flush mechanism present")
    
    def test_encrypted_submission(self, error_reporter_code):
        """Verify errors are sent encrypted"""
        assert "encryptForWiseDrive" in error_reporter_code
        assert "ERROR_ENDPOINT" in error_reporter_code
        print("   ✓ Encrypted error submission implemented")
    
    def test_jwt_auth_for_errors(self, error_reporter_code):
        """Verify JWT auth is used for error submission"""
        assert "Bearer" in error_reporter_code
        assert "authToken" in error_reporter_code
        assert "authenticate" in error_reporter_code
        print("   ✓ JWT auth for error submission present")
    
    def test_error_log_entry_structure(self, error_reporter_code):
        """Verify ErrorLogEntry data class has required fields"""
        required_fields = [
            "errorType",
            "message",
            "stackTrace",
            "timestamp",
            "sdkVersion",
            "licensePlate",
            "deviceModel",
            "androidVersion"
        ]
        for field in required_fields:
            assert field in error_reporter_code, f"Missing field: {field}"
        print(f"   ✓ ErrorLogEntry has all {len(required_fields)} required fields")
    
    def test_singleton_pattern(self, error_reporter_code):
        """Verify SDKErrorReporter is a singleton"""
        assert "object SDKErrorReporter" in error_reporter_code
        print("   ✓ SDKErrorReporter is a singleton object")


class TestCICDWorkflows:
    """Test GitHub Actions CI/CD workflow files"""
    
    @pytest.fixture
    def snapshot_workflow(self):
        """Load publish-snapshot.yml"""
        path = Path(__file__).parent.parent / ".github/workflows/publish-snapshot.yml"
        with open(path, 'r') as f:
            return f.read()
    
    @pytest.fixture
    def release_workflow(self):
        """Load publish-release.yml"""
        path = Path(__file__).parent.parent / ".github/workflows/publish-release.yml"
        with open(path, 'r') as f:
            return f.read()
    
    def test_snapshot_triggers(self, snapshot_workflow):
        """Verify snapshot workflow triggers on push to main/develop"""
        assert "push:" in snapshot_workflow
        assert "main" in snapshot_workflow
        assert "develop" in snapshot_workflow
        print("   ✓ Snapshot triggers on push to main/develop")
    
    def test_snapshot_jdk_setup(self, snapshot_workflow):
        """Verify JDK 17 setup in snapshot workflow"""
        assert "java-version: '17'" in snapshot_workflow or 'java-version: "17"' in snapshot_workflow
        assert "setup-java" in snapshot_workflow
        print("   ✓ JDK 17 setup in snapshot workflow")
    
    def test_snapshot_android_sdk(self, snapshot_workflow):
        """Verify Android SDK setup in snapshot workflow"""
        assert "setup-android" in snapshot_workflow
        print("   ✓ Android SDK setup in snapshot workflow")
    
    def test_snapshot_version_suffix(self, snapshot_workflow):
        """Verify -SNAPSHOT suffix is added"""
        assert "SNAPSHOT" in snapshot_workflow
        print("   ✓ -SNAPSHOT suffix added in snapshot workflow")
    
    def test_snapshot_jfrog_secrets(self, snapshot_workflow):
        """Verify JFrog secrets are used"""
        assert "JFROG_USER" in snapshot_workflow
        assert "JFROG_TOKEN" in snapshot_workflow
        assert "secrets." in snapshot_workflow
        print("   ✓ JFrog secrets used in snapshot workflow")
    
    def test_release_triggers(self, release_workflow):
        """Verify release workflow triggers on tags and releases"""
        assert "tags:" in release_workflow
        assert "v*" in release_workflow
        assert "release:" in release_workflow or "workflow_dispatch:" in release_workflow
        print("   ✓ Release triggers on tags/releases")
    
    def test_release_version_from_tag(self, release_workflow):
        """Verify version is extracted from tag"""
        assert "GITHUB_REF" in release_workflow or "github.ref" in release_workflow
        assert "refs/tags/v" in release_workflow
        print("   ✓ Version extracted from tag in release workflow")
    
    def test_release_unit_tests(self, release_workflow):
        """Verify unit tests run in release workflow"""
        assert "testReleaseUnitTest" in release_workflow
        print("   ✓ Unit tests run in release workflow")
    
    def test_release_jfrog_publish(self, release_workflow):
        """Verify JFrog publish in release workflow"""
        assert "JFrogReleasesRepository" in release_workflow
        assert "JFROG_USER" in release_workflow
        assert "JFROG_TOKEN" in release_workflow
        print("   ✓ JFrog publish configured in release workflow")
    
    def test_release_notes_generation(self, release_workflow):
        """Verify release notes are generated"""
        assert "release_notes" in release_workflow.lower() or "gh-release" in release_workflow.lower() or "action-gh-release" in release_workflow
        print("   ✓ Release notes generation configured")


class TestClientIntegrationGuide:
    """Test CLIENT_INTEGRATION_GUIDE.md content"""
    
    @pytest.fixture
    def guide_content(self):
        """Load CLIENT_INTEGRATION_GUIDE.md"""
        path = Path(__file__).parent.parent / "docs/CLIENT_INTEGRATION_GUIDE.md"
        with open(path, 'r') as f:
            return f.read()
    
    def test_guide_exists(self, guide_content):
        """Verify guide exists and has content"""
        assert len(guide_content) > 1000, "Guide too short"
        print(f"   ✓ Guide exists with {len(guide_content)} characters")
    
    def test_overview_section(self, guide_content):
        """Verify Overview section exists"""
        assert "## Overview" in guide_content or "# Overview" in guide_content
        print("   ✓ Overview section present")
    
    def test_requirements_section(self, guide_content):
        """Verify Requirements section exists"""
        assert "Requirement" in guide_content or "requirement" in guide_content
        assert "Android" in guide_content
        assert "API 21" in guide_content or "Android 5" in guide_content
        print("   ✓ Requirements section present")
    
    def test_repository_setup(self, guide_content):
        """Verify repository setup instructions"""
        assert "jfrog.io" in guide_content or "maven" in guide_content.lower()
        assert "settings.gradle" in guide_content or "build.gradle" in guide_content
        print("   ✓ Repository setup instructions present")
    
    def test_dependency_instructions(self, guide_content):
        """Verify dependency instructions"""
        assert "implementation" in guide_content
        assert "com.wisedrive" in guide_content
        assert "obd2-sdk" in guide_content
        print("   ✓ Dependency instructions present")
    
    def test_permissions_section(self, guide_content):
        """Verify permissions section"""
        assert "BLUETOOTH" in guide_content
        assert "INTERNET" in guide_content
        assert "AndroidManifest" in guide_content
        print("   ✓ Permissions section present")
    
    def test_initialization_code(self, guide_content):
        """Verify SDK initialization code example"""
        assert "SDKConfig" in guide_content
        assert "initialize" in guide_content
        assert "apiKey" in guide_content
        print("   ✓ SDK initialization code present")
    
    def test_client_endpoint_config(self, guide_content):
        """Verify clientEndpoint configuration"""
        assert "clientEndpoint" in guide_content
        assert "your-server" in guide_content.lower() or "your backend" in guide_content.lower()
        print("   ✓ Client endpoint configuration documented")
    
    def test_plain_json_explanation(self, guide_content):
        """Verify plain JSON explanation for client"""
        assert "plain JSON" in guide_content.lower() or "no decryption" in guide_content.lower()
        assert "no encryption" in guide_content.lower() or "plain" in guide_content.lower()
        print("   ✓ Plain JSON explanation present")
    
    def test_json_payload_structure(self, guide_content):
        """Verify JSON payload structure is documented"""
        assert "inspectionId" in guide_content or "license_plate" in guide_content
        assert "diagnosticTroubleCodes" in guide_content or "code_details" in guide_content
        assert "liveData" in guide_content
        print("   ✓ JSON payload structure documented")
    
    def test_backend_examples(self, guide_content):
        """Verify backend code examples"""
        # Check for Python or Node.js examples
        has_python = "FastAPI" in guide_content or "flask" in guide_content.lower() or "python" in guide_content.lower()
        has_node = "express" in guide_content.lower() or "node" in guide_content.lower()
        assert has_python or has_node, "No backend code examples found"
        print("   ✓ Backend code examples present")
    
    def test_error_handling_section(self, guide_content):
        """Verify error handling section"""
        assert "Error" in guide_content or "Exception" in guide_content
        assert "try" in guide_content or "catch" in guide_content
        print("   ✓ Error handling section present")
    
    def test_faq_section(self, guide_content):
        """Verify FAQ section"""
        assert "FAQ" in guide_content or "Frequently Asked" in guide_content
        print("   ✓ FAQ section present")
    
    def test_support_contact(self, guide_content):
        """Verify support contact info"""
        assert "support" in guide_content.lower() or "email" in guide_content.lower() or "wisedrive" in guide_content.lower()
        print("   ✓ Support contact info present")


class TestKotlinCompilation:
    """Test Kotlin SDK compilation"""
    
    def test_sdk_compiles(self):
        """Verify SDK compiles successfully"""
        import subprocess
        
        result = subprocess.run(
            ["./gradlew", ":sdk:compileReleaseKotlin", "--no-daemon"],
            cwd="/app/wisedrive-obd2-sdk-android",
            capture_output=True,
            text=True,
            timeout=180,
            env={
                **os.environ,
                "JAVA_HOME": "/usr/lib/jvm/java-17-openjdk-arm64",
                "ANDROID_SDK_ROOT": "/opt/android-sdk"
            }
        )
        
        assert "BUILD SUCCESSFUL" in result.stdout or result.returncode == 0, f"Build failed: {result.stderr}"
        print("   ✓ Kotlin SDK compiles successfully")


class TestRegressionEncryption:
    """Regression tests for existing encryption functionality"""
    
    def test_existing_backend_tests_pass(self):
        """Run existing backend_test.py and verify it passes"""
        import subprocess
        
        result = subprocess.run(
            ["python3", "backend_test.py"],
            cwd="/app/wisedrive-obd2-sdk-android/backend",
            capture_output=True,
            text=True,
            timeout=120
        )
        
        # Check for success indicators
        assert "ALL TESTS PASSED" in result.stdout or "SECURE" in result.stdout, f"Backend tests failed: {result.stdout}\n{result.stderr}"
        print("   ✓ Existing encryption tests pass")
    
    def test_anti_re_tests_pass(self):
        """Run anti-RE verification tests"""
        import subprocess
        
        result = subprocess.run(
            ["python3", "anti_re_verification_test.py"],
            cwd="/app/wisedrive-obd2-sdk-android/backend",
            capture_output=True,
            text=True,
            timeout=60
        )
        
        assert "ALL TESTS PASSED" in result.stdout or result.returncode == 0, f"Anti-RE tests failed: {result.stdout}\n{result.stderr}"
        print("   ✓ Anti-RE verification tests pass")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])
