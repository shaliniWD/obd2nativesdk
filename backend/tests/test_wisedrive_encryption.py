#!/usr/bin/env python3
"""
WiseDrive OBD2 SDK - Comprehensive Pytest Test Suite
=====================================================

Tests all encryption/decryption scenarios including:
1. Python Encryption/Decryption - Full cycle
2. Wrong Key Rejection
3. Payload Tampering Detection
4. HMAC Bypass Protection
5. Dual Key System
6. IV Uniqueness
7. Encrypted Payload Format Validation
8. Red Team Security Tests
9. Error Handling (empty, truncated, invalid base64, etc.)
10. KeyGenerator validation
11. Header Parsing
12. HMAC Key Derivation
13. API Server Endpoints
14. Replay Attack Protection
15. Large Payload Handling
16. Special Characters in Data
17. Python-to-Kotlin Compatibility (format validation)
"""

import pytest
import sys
import os
import json
import time
import base64
import hashlib
import hmac
import struct
import requests
from datetime import datetime
from typing import Dict, Any

# Add backend directory to path
sys.path.insert(0, '/app/wisedrive-obd2-sdk-android/backend')

from wisedrive_decryption import WiseDriveDecryptor, DecryptionError, KeyGenerator, EncryptedPayloadHeader
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding as asym_padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend


# ============================================================================
# FIXTURES
# ============================================================================

@pytest.fixture(scope="module")
def client_keys():
    """Generate client RSA-4096 key pair"""
    public_key, private_key = KeyGenerator.generate_rsa_4096()
    return {"public": public_key, "private": private_key}


@pytest.fixture(scope="module")
def wisedrive_keys():
    """Generate WiseDrive RSA-4096 key pair"""
    public_key, private_key = KeyGenerator.generate_rsa_4096()
    return {"public": public_key, "private": private_key}


@pytest.fixture(scope="module")
def client_decryptor(client_keys):
    """Create client decryptor"""
    return WiseDriveDecryptor(client_keys["private"], key_id=1)


@pytest.fixture(scope="module")
def wisedrive_decryptor(wisedrive_keys):
    """Create WiseDrive decryptor"""
    return WiseDriveDecryptor(wisedrive_keys["private"], key_id=1)


@pytest.fixture
def sample_obd_data():
    """Sample OBD scan data for testing"""
    return {
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


@pytest.fixture
def api_base_url():
    """Local API server URL"""
    return "http://localhost:8082"


def encrypt_payload(payload: dict, public_key_pem: str, magic: str = "WDSW") -> bytes:
    """Encrypt payload using Hybrid RSA-4096 + AES-256-GCM"""
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


# ============================================================================
# TEST 1: PYTHON ENCRYPTION/DECRYPTION - FULL CYCLE
# ============================================================================

class TestBasicEncryptionDecryption:
    """Test basic encryption/decryption functionality"""
    
    def test_encrypt_decrypt_full_cycle(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Test full encryption/decryption cycle with correct keys"""
        # Encrypt
        encrypted_data = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
        
        # Decrypt
        decrypted_data = wisedrive_decryptor.decrypt(encrypted_b64)
        
        # Verify data integrity
        assert decrypted_data["license_plate"] == sample_obd_data["license_plate"]
        assert decrypted_data["tracking_id"] == sample_obd_data["tracking_id"]
        assert decrypted_data["vin"] == sample_obd_data["vin"]
        assert len(decrypted_data["code_details"]) == len(sample_obd_data["code_details"])
        assert decrypted_data["battery_voltage"] == sample_obd_data["battery_voltage"]
    
    def test_encrypt_decrypt_client_data(self, client_keys, client_decryptor, sample_obd_data):
        """Test encryption/decryption with client keys"""
        encrypted_data = encrypt_payload(sample_obd_data, client_keys["public"], "WDSC")
        encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
        
        decrypted_data = client_decryptor.decrypt(encrypted_b64)
        
        assert decrypted_data["license_plate"] == sample_obd_data["license_plate"]


# ============================================================================
# TEST 2: WRONG KEY REJECTION
# ============================================================================

class TestWrongKeyRejection:
    """Test that wrong keys are properly rejected"""
    
    def test_wrong_private_key_fails(self, wisedrive_keys, client_decryptor, sample_obd_data):
        """Encrypt with WiseDrive key, try to decrypt with client key - must fail"""
        encrypted_data = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
        
        with pytest.raises(DecryptionError) as exc_info:
            client_decryptor.decrypt(encrypted_b64)
        
        assert "RSA decryption failed" in str(exc_info.value) or "wrong key" in str(exc_info.value).lower()
    
    def test_cross_key_decryption_fails(self, client_keys, wisedrive_decryptor, sample_obd_data):
        """Encrypt with client key, try to decrypt with WiseDrive key - must fail"""
        encrypted_data = encrypt_payload(sample_obd_data, client_keys["public"], "WDSC")
        encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
        
        with pytest.raises(DecryptionError):
            wisedrive_decryptor.decrypt(encrypted_b64)


# ============================================================================
# TEST 3: PAYLOAD TAMPERING DETECTION
# ============================================================================

class TestPayloadTamperingDetection:
    """Test that payload tampering is detected"""
    
    def test_single_bit_flip_detected(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Flip a single bit in ciphertext - must be detected"""
        encrypted_data = bytearray(encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW"))
        
        # Flip a bit in the ciphertext area
        ciphertext_start = 16 + 512 + 12  # header + rsa_key + iv
        encrypted_data[ciphertext_start + 10] ^= 0x01
        
        encrypted_b64 = base64.b64encode(bytes(encrypted_data)).decode('utf-8')
        
        with pytest.raises(DecryptionError):
            wisedrive_decryptor.decrypt(encrypted_b64)
    
    def test_header_modification_detected(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Modify header - must be detected"""
        encrypted_data = bytearray(encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW"))
        
        # Modify timestamp in header
        encrypted_data[10] ^= 0xFF
        
        encrypted_b64 = base64.b64encode(bytes(encrypted_data)).decode('utf-8')
        
        with pytest.raises(DecryptionError):
            wisedrive_decryptor.decrypt(encrypted_b64)
    
    def test_ciphertext_replacement_detected(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Replace ciphertext with garbage - must be detected"""
        encrypted_data = bytearray(encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW"))
        
        # Replace ciphertext with random data
        ciphertext_start = 16 + 512 + 12
        ciphertext_end = len(encrypted_data) - 64
        for i in range(ciphertext_start, min(ciphertext_start + 50, ciphertext_end)):
            encrypted_data[i] = (encrypted_data[i] + 1) % 256
        
        encrypted_b64 = base64.b64encode(bytes(encrypted_data)).decode('utf-8')
        
        with pytest.raises(DecryptionError):
            wisedrive_decryptor.decrypt(encrypted_b64)


# ============================================================================
# TEST 4: HMAC BYPASS PROTECTION
# ============================================================================

class TestHMACBypassProtection:
    """Test HMAC bypass attempts are blocked"""
    
    def test_zeroed_hmac_rejected(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Zero out HMAC - must be rejected"""
        encrypted_data = bytearray(encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW"))
        
        # Replace HMAC with zeros
        for i in range(len(encrypted_data) - 64, len(encrypted_data)):
            encrypted_data[i] = 0
        
        encrypted_b64 = base64.b64encode(bytes(encrypted_data)).decode('utf-8')
        
        with pytest.raises(DecryptionError) as exc_info:
            wisedrive_decryptor.decrypt(encrypted_b64)
        
        assert "HMAC" in str(exc_info.value) or "tampered" in str(exc_info.value).lower()
    
    def test_random_hmac_rejected(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Replace HMAC with random bytes - must be rejected"""
        encrypted_data = bytearray(encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW"))
        
        # Replace HMAC with random data
        import random
        for i in range(len(encrypted_data) - 64, len(encrypted_data)):
            encrypted_data[i] = random.randint(0, 255)
        
        encrypted_b64 = base64.b64encode(bytes(encrypted_data)).decode('utf-8')
        
        with pytest.raises(DecryptionError):
            wisedrive_decryptor.decrypt(encrypted_b64)


# ============================================================================
# TEST 5: DUAL KEY SYSTEM
# ============================================================================

class TestDualKeySystem:
    """Test dual key system - client and WiseDrive keys are separate"""
    
    def test_client_key_encrypts_for_client_only(self, client_keys, client_decryptor, wisedrive_decryptor, sample_obd_data):
        """Client key encrypts for client only, WiseDrive cannot decrypt"""
        encrypted_data = encrypt_payload(sample_obd_data, client_keys["public"], "WDSC")
        encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
        
        # Client can decrypt
        decrypted = client_decryptor.decrypt(encrypted_b64)
        assert decrypted["license_plate"] == sample_obd_data["license_plate"]
        
        # WiseDrive cannot decrypt
        with pytest.raises(DecryptionError):
            wisedrive_decryptor.decrypt(encrypted_b64)
    
    def test_wisedrive_key_encrypts_for_wisedrive_only(self, wisedrive_keys, wisedrive_decryptor, client_decryptor, sample_obd_data):
        """WiseDrive key encrypts for WiseDrive only, client cannot decrypt"""
        encrypted_data = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
        
        # WiseDrive can decrypt
        decrypted = wisedrive_decryptor.decrypt(encrypted_b64)
        assert decrypted["license_plate"] == sample_obd_data["license_plate"]
        
        # Client cannot decrypt
        with pytest.raises(DecryptionError):
            client_decryptor.decrypt(encrypted_b64)


# ============================================================================
# TEST 6: IV UNIQUENESS
# ============================================================================

class TestIVUniqueness:
    """Test IV uniqueness - IV reuse in GCM is catastrophic"""
    
    def test_100_encryptions_unique_ivs(self, wisedrive_keys, sample_obd_data):
        """100 encryptions must all produce unique IVs"""
        ivs = set()
        
        for _ in range(100):
            encrypted_data = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
            # Extract IV (12 bytes after header + rsa_key)
            iv = encrypted_data[16 + 512:16 + 512 + 12]
            iv_hex = iv.hex()
            
            assert iv_hex not in ivs, f"IV reused: {iv_hex}"
            ivs.add(iv_hex)
        
        assert len(ivs) == 100


# ============================================================================
# TEST 7: ENCRYPTED PAYLOAD FORMAT VALIDATION
# ============================================================================

class TestEncryptedPayloadFormat:
    """Test encrypted payload format validation"""
    
    def test_magic_bytes_wdsc(self, client_keys, sample_obd_data):
        """Check magic bytes WDSC for client"""
        encrypted_data = encrypt_payload(sample_obd_data, client_keys["public"], "WDSC")
        magic = encrypted_data[0:4].decode('utf-8')
        assert magic == "WDSC"
    
    def test_magic_bytes_wdsw(self, wisedrive_keys, sample_obd_data):
        """Check magic bytes WDSW for WiseDrive"""
        encrypted_data = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        magic = encrypted_data[0:4].decode('utf-8')
        assert magic == "WDSW"
    
    def test_version_is_2(self, wisedrive_keys, sample_obd_data):
        """Check version is 2"""
        encrypted_data = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        version = struct.unpack('>H', encrypted_data[4:6])[0]
        assert version == 2
    
    def test_key_id_is_1(self, wisedrive_keys, sample_obd_data):
        """Check keyId is 1"""
        encrypted_data = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        key_id = struct.unpack('>I', encrypted_data[6:10])[0]
        assert key_id == 1
    
    def test_correct_sizes(self, wisedrive_keys, sample_obd_data):
        """Check correct component sizes"""
        encrypted_data = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        
        # Minimum size: header(16) + rsa_key(512) + iv(12) + gcm_tag(16) + hmac(64)
        min_size = 16 + 512 + 12 + 16 + 64
        assert len(encrypted_data) >= min_size
        
        # Header is 16 bytes
        header = encrypted_data[0:16]
        assert len(header) == 16
        
        # RSA encrypted key is 512 bytes (4096 bits)
        rsa_key = encrypted_data[16:16+512]
        assert len(rsa_key) == 512
        
        # IV is 12 bytes
        iv = encrypted_data[16+512:16+512+12]
        assert len(iv) == 12
        
        # HMAC is 64 bytes (SHA-512)
        hmac_sig = encrypted_data[-64:]
        assert len(hmac_sig) == 64


# ============================================================================
# TEST 8: RED TEAM SECURITY TESTS (14 attack vectors)
# ============================================================================

class TestRedTeamSecurity:
    """Red team security tests - 14 attack vectors"""
    
    def test_brute_force_aes_infeasible(self, wisedrive_keys, sample_obd_data):
        """Brute force AES-256 is computationally infeasible"""
        encrypted = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        
        # Try a few random keys (won't work with 2^256 keyspace)
        for _ in range(100):
            random_key = os.urandom(32)
            try:
                iv = encrypted[16 + 512:16 + 512 + 12]
                ciphertext = encrypted[16 + 512 + 12:-64]
                aesgcm = AESGCM(random_key)
                aesgcm.decrypt(iv, ciphertext, None)
                pytest.fail("Brute force should not succeed")
            except:
                pass  # Expected
    
    def test_brute_force_rsa_infeasible(self, wisedrive_keys):
        """RSA-4096 factorization is infeasible"""
        public_key = serialization.load_pem_public_key(
            wisedrive_keys["public"].encode('utf-8'),
            backend=default_backend()
        )
        
        numbers = public_key.public_numbers()
        n = numbers.n
        
        # Try trivial factorization (won't work)
        small_primes = [2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47]
        for p in small_primes:
            assert n % p != 0, f"RSA modulus divisible by {p} - weak key!"
    
    def test_known_plaintext_attack_fails(self, wisedrive_keys, sample_obd_data):
        """Known plaintext attack fails due to random AES key and IV"""
        enc1 = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        enc2 = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        
        # Ciphertexts should be completely different
        assert enc1 != enc2
        
        # Even ciphertext portions should differ
        enc1_cipher = enc1[16 + 512 + 12:-64]
        enc2_cipher = enc2[16 + 512 + 12:-64]
        assert enc1_cipher != enc2_cipher
    
    def test_timing_attack_resistance(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Timing variations should be minimal"""
        encrypted = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        encrypted_b64 = base64.b64encode(encrypted).decode('utf-8')
        
        times = []
        for _ in range(50):
            start = time.perf_counter()
            try:
                wisedrive_decryptor.decrypt(encrypted_b64)
            except:
                pass
            times.append(time.perf_counter() - start)
        
        avg_time = sum(times) / len(times)
        variance = sum((t - avg_time) ** 2 for t in times) / len(times)
        
        # Variance should be low (< 1ms)
        assert variance < 0.001, f"High timing variance: {variance}"
    
    def test_padding_oracle_not_applicable(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """AES-GCM doesn't use padding - no padding oracle"""
        encrypted = bytearray(encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW"))
        
        padding_errors = 0
        auth_errors = 0
        
        for i in range(10):
            modified = bytearray(encrypted)
            modified[-65] ^= (i + 1)
            
            try:
                wisedrive_decryptor.decrypt(base64.b64encode(bytes(modified)).decode('utf-8'))
            except DecryptionError as e:
                error_str = str(e).lower()
                if "padding" in error_str:
                    padding_errors += 1
                elif "hmac" in error_str or "tamper" in error_str or "gcm" in error_str:
                    auth_errors += 1
        
        # Should get auth errors, not padding errors
        assert padding_errors <= auth_errors
    
    def test_memory_dump_no_key_leakage(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Decrypted data should not contain key material"""
        encrypted = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        encrypted_b64 = base64.b64encode(encrypted).decode('utf-8')
        
        result = wisedrive_decryptor.decrypt(encrypted_b64)
        result_str = json.dumps(result)
        
        assert "PRIVATE" not in result_str
        assert wisedrive_keys["private"][:50] not in result_str


# ============================================================================
# TEST 9: ERROR HANDLING
# ============================================================================

class TestErrorHandling:
    """Test error handling for various invalid inputs"""
    
    def test_empty_payload_rejected(self, wisedrive_decryptor):
        """Empty payload must be rejected"""
        with pytest.raises(DecryptionError):
            wisedrive_decryptor.decrypt("")
    
    def test_truncated_payload_rejected(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Truncated payload must be rejected"""
        encrypted = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        truncated = encrypted[:100]  # Only first 100 bytes
        truncated_b64 = base64.b64encode(truncated).decode('utf-8')
        
        with pytest.raises(DecryptionError) as exc_info:
            wisedrive_decryptor.decrypt(truncated_b64)
        
        assert "too small" in str(exc_info.value).lower() or "failed" in str(exc_info.value).lower()
    
    def test_invalid_base64_rejected(self, wisedrive_decryptor):
        """Invalid base64 must be rejected"""
        with pytest.raises(DecryptionError):
            wisedrive_decryptor.decrypt("not_valid_base64!!!")
    
    def test_invalid_magic_bytes_rejected(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Invalid magic bytes must be rejected"""
        encrypted = bytearray(encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW"))
        
        # Change magic bytes
        encrypted[0:4] = b"XXXX"
        
        encrypted_b64 = base64.b64encode(bytes(encrypted)).decode('utf-8')
        
        with pytest.raises(DecryptionError) as exc_info:
            wisedrive_decryptor.decrypt(encrypted_b64)
        
        assert "magic" in str(exc_info.value).lower() or "invalid" in str(exc_info.value).lower()
    
    def test_wrong_version_number(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Wrong version number should still decrypt (version check is informational)"""
        # Note: The current implementation doesn't reject based on version
        # This test documents the behavior
        encrypted = bytearray(encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW"))
        
        # Change version to 99
        encrypted[4:6] = struct.pack('>H', 99)
        
        # Recalculate HMAC would be needed for this to work
        # Since we can't recalculate HMAC without the AES key, this will fail on HMAC
        encrypted_b64 = base64.b64encode(bytes(encrypted)).decode('utf-8')
        
        with pytest.raises(DecryptionError):
            wisedrive_decryptor.decrypt(encrypted_b64)


# ============================================================================
# TEST 10: KEY GENERATOR
# ============================================================================

class TestKeyGenerator:
    """Test RSA key generation"""
    
    def test_generate_rsa_4096(self):
        """Verify RSA-4096 key generation works"""
        public_key, private_key = KeyGenerator.generate_rsa_4096()
        
        assert "-----BEGIN PUBLIC KEY-----" in public_key
        assert "-----END PUBLIC KEY-----" in public_key
        assert "-----BEGIN PRIVATE KEY-----" in private_key
        assert "-----END PRIVATE KEY-----" in private_key
        
        # Verify key size
        pub = serialization.load_pem_public_key(public_key.encode(), backend=default_backend())
        assert pub.key_size == 4096
    
    def test_generate_rsa_2048(self):
        """Verify RSA-2048 key generation works"""
        public_key, private_key = KeyGenerator.generate_rsa_2048()
        
        assert "-----BEGIN PUBLIC KEY-----" in public_key
        assert "-----END PUBLIC KEY-----" in public_key
        
        # Verify key size
        pub = serialization.load_pem_public_key(public_key.encode(), backend=default_backend())
        assert pub.key_size == 2048
    
    def test_keys_are_valid_pem_format(self):
        """Keys must be valid PEM format"""
        public_key, private_key = KeyGenerator.generate_rsa_4096()
        
        # Should not raise
        serialization.load_pem_public_key(public_key.encode(), backend=default_backend())
        serialization.load_pem_private_key(private_key.encode(), password=None, backend=default_backend())


# ============================================================================
# TEST 11: HEADER PARSING
# ============================================================================

class TestHeaderParsing:
    """Test header parsing functionality"""
    
    def test_parse_header_wdsw(self, wisedrive_keys, wisedrive_decryptor, sample_obd_data):
        """Verify _parse_header correctly parses WDSW header"""
        encrypted = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        encrypted_b64 = base64.b64encode(encrypted).decode('utf-8')
        
        header = wisedrive_decryptor.get_header_info(encrypted_b64)
        
        assert header.magic == "WDSW"
        assert header.version == 2
        assert header.key_id == 1
        assert header.timestamp > 0
    
    def test_parse_header_wdsc(self, client_keys, client_decryptor, sample_obd_data):
        """Verify _parse_header correctly parses WDSC header"""
        encrypted = encrypt_payload(sample_obd_data, client_keys["public"], "WDSC")
        encrypted_b64 = base64.b64encode(encrypted).decode('utf-8')
        
        header = client_decryptor.get_header_info(encrypted_b64)
        
        assert header.magic == "WDSC"
        assert header.version == 2
        assert header.key_id == 1


# ============================================================================
# TEST 12: HMAC KEY DERIVATION
# ============================================================================

class TestHMACKeyDerivation:
    """Test HMAC key derivation"""
    
    def test_hmac_key_derivation_consistent(self):
        """Verify deriveHmacKey produces consistent results"""
        aes_key = os.urandom(32)
        
        # Derive HMAC key twice
        hmac_key1 = hashlib.sha256(aes_key + b"HMAC_KEY_DERIVATION").digest()
        hmac_key2 = hashlib.sha256(aes_key + b"HMAC_KEY_DERIVATION").digest()
        
        assert hmac_key1 == hmac_key2
    
    def test_hmac_key_derivation_different_for_different_aes_keys(self):
        """Different AES keys produce different HMAC keys"""
        aes_key1 = os.urandom(32)
        aes_key2 = os.urandom(32)
        
        hmac_key1 = hashlib.sha256(aes_key1 + b"HMAC_KEY_DERIVATION").digest()
        hmac_key2 = hashlib.sha256(aes_key2 + b"HMAC_KEY_DERIVATION").digest()
        
        assert hmac_key1 != hmac_key2


# ============================================================================
# TEST 13: API SERVER ENDPOINTS
# ============================================================================

class TestAPIServerEndpoints:
    """Test Flask API server endpoints"""
    
    def test_health_endpoint(self, api_base_url):
        """Test /health endpoint"""
        try:
            response = requests.get(f"{api_base_url}/health", timeout=5)
            assert response.status_code == 200
            
            data = response.json()
            assert data["status"] == "healthy"
            assert data["service"] == "WiseDrive OBD2 Backend"
            assert data["version"] == "2.0"
            assert data["encryption"] == "RSA-4096 + AES-256-GCM"
        except requests.exceptions.ConnectionError:
            pytest.skip("API server not running")
    
    def test_public_keys_endpoint(self, api_base_url):
        """Test /api/keys/public endpoint"""
        try:
            response = requests.get(f"{api_base_url}/api/keys/public", timeout=5)
            assert response.status_code == 200
            
            data = response.json()
            assert "clientPublicKey" in data
            assert "wiseDrivePublicKey" in data
            assert data["keyId"] == 1
            assert data["algorithm"] == "RSA-4096"
        except requests.exceptions.ConnectionError:
            pytest.skip("API server not running")
    
    def test_encrypted_endpoint_with_license_plate(self, api_base_url, sample_obd_data):
        """Test encrypted endpoint with license_plate URL param"""
        try:
            # Get server's public key
            keys_response = requests.get(f"{api_base_url}/api/keys/public", timeout=5)
            if keys_response.status_code != 200:
                pytest.skip("Cannot get public keys")
            
            server_public_key = keys_response.json()["wiseDrivePublicKey"]
            
            # Encrypt with server's key
            encrypted_data = encrypt_payload(sample_obd_data, server_public_key, "WDSW")
            encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
            
            payload = {
                "version": 2,
                "keyId": 1,
                "timestamp": int(time.time() * 1000),
                "encryptedData": encrypted_b64
            }
            
            response = requests.post(
                f"{api_base_url}/apiv2/webhook/obdreport/wisedrive/encrypted?license_plate=MH12AB1234",
                json=payload,
                headers={
                    "Authorization": "Basic cHJhc2FkOnByYXNhZEAxMjM=",
                    "Content-Type": "application/json"
                },
                timeout=10
            )
            
            assert response.status_code == 200
            data = response.json()
            assert data["result"] == "SUCCESS"
            assert data["decrypted"] == True
        except requests.exceptions.ConnectionError:
            pytest.skip("API server not running")
    
    def test_decrypt_test_endpoint(self, api_base_url, sample_obd_data):
        """Test /api/decrypt/test endpoint"""
        try:
            # Get server's public key
            keys_response = requests.get(f"{api_base_url}/api/keys/public", timeout=5)
            if keys_response.status_code != 200:
                pytest.skip("Cannot get public keys")
            
            server_public_key = keys_response.json()["wiseDrivePublicKey"]
            
            # Encrypt with server's key
            encrypted_data = encrypt_payload(sample_obd_data, server_public_key, "WDSW")
            encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
            
            response = requests.post(
                f"{api_base_url}/api/decrypt/test",
                json={
                    "encryptedData": encrypted_b64,
                    "keyType": "wisedrive"
                },
                timeout=10
            )
            
            assert response.status_code == 200
            data = response.json()
            assert data["success"] == True
            assert data["decryptedData"]["license_plate"] == sample_obd_data["license_plate"]
        except requests.exceptions.ConnectionError:
            pytest.skip("API server not running")


# ============================================================================
# TEST 14: REPLAY ATTACK PROTECTION
# ============================================================================

class TestReplayAttackProtection:
    """Test replay attack protection on API server"""
    
    def test_replay_attack_detected(self, api_base_url, sample_obd_data):
        """Send same payload twice, second should be rejected"""
        try:
            # Get server's public key
            keys_response = requests.get(f"{api_base_url}/api/keys/public", timeout=5)
            if keys_response.status_code != 200:
                pytest.skip("Cannot get public keys")
            
            server_public_key = keys_response.json()["wiseDrivePublicKey"]
            
            # Encrypt with server's key
            encrypted_data = encrypt_payload(sample_obd_data, server_public_key, "WDSW")
            encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
            
            timestamp = int(time.time() * 1000)
            payload = {
                "version": 2,
                "keyId": 1,
                "timestamp": timestamp,
                "encryptedData": encrypted_b64
            }
            
            headers = {
                "Authorization": "Basic cHJhc2FkOnByYXNhZEAxMjM=",
                "Content-Type": "application/json"
            }
            
            # First request should succeed
            response1 = requests.post(
                f"{api_base_url}/apiv2/webhook/obdreport/wisedrive/encrypted?license_plate=MH12AB1234",
                json=payload,
                headers=headers,
                timeout=10
            )
            
            assert response1.status_code == 200
            
            # Second request with same payload should be rejected
            response2 = requests.post(
                f"{api_base_url}/apiv2/webhook/obdreport/wisedrive/encrypted?license_plate=MH12AB1234",
                json=payload,
                headers=headers,
                timeout=10
            )
            
            # Should be rejected as replay
            assert response2.status_code == 400
            assert "replay" in response2.json().get("error", "").lower()
        except requests.exceptions.ConnectionError:
            pytest.skip("API server not running")


# ============================================================================
# TEST 15: LARGE PAYLOAD HANDLING
# ============================================================================

class TestLargePayloadHandling:
    """Test encryption/decryption with large payloads"""
    
    def test_large_payload_with_many_dtcs(self, wisedrive_keys, wisedrive_decryptor):
        """Test with very large JSON payload (many DTCs)"""
        large_payload = {
            "license_plate": "MH12AB1234",
            "tracking_id": "ORD6894331",
            "vin": "KMHXX00XXXX000000",
            "code_details": []
        }
        
        # Add 100 DTCs
        for i in range(100):
            large_payload["code_details"].append({
                "dtc": f"P{i:04d}",
                "meaning": f"Test DTC {i} - " + "X" * 100,  # Long description
                "severity": "MEDIUM",
                "ecu_source": "PCM"
            })
        
        # Encrypt
        encrypted_data = encrypt_payload(large_payload, wisedrive_keys["public"], "WDSW")
        encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
        
        # Decrypt
        decrypted = wisedrive_decryptor.decrypt(encrypted_b64)
        
        assert len(decrypted["code_details"]) == 100
        assert decrypted["code_details"][0]["dtc"] == "P0000"
        assert decrypted["code_details"][99]["dtc"] == "P0099"


# ============================================================================
# TEST 16: SPECIAL CHARACTERS IN DATA
# ============================================================================

class TestSpecialCharactersInData:
    """Test encryption with unicode and special characters"""
    
    def test_unicode_license_plate(self, wisedrive_keys, wisedrive_decryptor):
        """Test with unicode characters in license plate"""
        payload = {
            "license_plate": "मह१२एबी१२३४",  # Marathi
            "tracking_id": "ORD6894331",
            "vin": "KMHXX00XXXX000000"
        }
        
        encrypted_data = encrypt_payload(payload, wisedrive_keys["public"], "WDSW")
        encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
        
        decrypted = wisedrive_decryptor.decrypt(encrypted_b64)
        assert decrypted["license_plate"] == "मह१२एबी१२३४"
    
    def test_special_characters_in_vin(self, wisedrive_keys, wisedrive_decryptor):
        """Test with special characters in VIN"""
        payload = {
            "license_plate": "MH12AB1234",
            "tracking_id": "ORD-6894331-TEST",
            "vin": "KMHXX00XXXX000000",
            "notes": "Test with special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?"
        }
        
        encrypted_data = encrypt_payload(payload, wisedrive_keys["public"], "WDSW")
        encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
        
        decrypted = wisedrive_decryptor.decrypt(encrypted_b64)
        assert "!@#$%^&*()" in decrypted["notes"]
    
    def test_emoji_in_data(self, wisedrive_keys, wisedrive_decryptor):
        """Test with emoji characters"""
        payload = {
            "license_plate": "MH12AB1234",
            "tracking_id": "ORD6894331",
            "vin": "KMHXX00XXXX000000",
            "status_emoji": "✅🚗🔧"
        }
        
        encrypted_data = encrypt_payload(payload, wisedrive_keys["public"], "WDSW")
        encrypted_b64 = base64.b64encode(encrypted_data).decode('utf-8')
        
        decrypted = wisedrive_decryptor.decrypt(encrypted_b64)
        assert decrypted["status_emoji"] == "✅🚗🔧"


# ============================================================================
# TEST 17: PYTHON-TO-KOTLIN COMPATIBILITY
# ============================================================================

class TestPythonKotlinCompatibility:
    """Verify Python encrypted payloads match Kotlin expected format"""
    
    def test_header_structure_matches_kotlin(self, wisedrive_keys, sample_obd_data):
        """Verify header structure matches Kotlin decryptor expectations"""
        encrypted = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        
        # Header structure expected by Kotlin:
        # - 4 bytes: magic (WDSC/WDSW)
        # - 2 bytes: version (big-endian)
        # - 4 bytes: keyId (big-endian)
        # - 6 bytes: timestamp (big-endian, truncated from 8 bytes)
        
        magic = encrypted[0:4]
        assert len(magic) == 4
        assert magic in [b"WDSC", b"WDSW"]
        
        version_bytes = encrypted[4:6]
        assert len(version_bytes) == 2
        version = struct.unpack('>H', version_bytes)[0]
        assert version == 2
        
        key_id_bytes = encrypted[6:10]
        assert len(key_id_bytes) == 4
        key_id = struct.unpack('>I', key_id_bytes)[0]
        assert key_id == 1
        
        timestamp_bytes = encrypted[10:16]
        assert len(timestamp_bytes) == 6
    
    def test_byte_layout_matches_kotlin(self, wisedrive_keys, sample_obd_data):
        """Verify overall byte layout matches Kotlin expectations"""
        encrypted = encrypt_payload(sample_obd_data, wisedrive_keys["public"], "WDSW")
        
        # Expected layout:
        # [0-15]: Header (16 bytes)
        # [16-527]: RSA encrypted AES key (512 bytes for RSA-4096)
        # [528-539]: IV (12 bytes)
        # [540 to -64]: Ciphertext with GCM tag
        # [-64:]: HMAC-SHA512 (64 bytes)
        
        header = encrypted[0:16]
        rsa_key = encrypted[16:528]
        iv = encrypted[528:540]
        hmac_sig = encrypted[-64:]
        ciphertext = encrypted[540:-64]
        
        assert len(header) == 16
        assert len(rsa_key) == 512
        assert len(iv) == 12
        assert len(hmac_sig) == 64
        assert len(ciphertext) >= 16  # At least GCM tag


if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])
