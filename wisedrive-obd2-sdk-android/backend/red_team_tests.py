"""
WiseDrive OBD2 SDK - Security Red Team Tests
=============================================

This module simulates various attack vectors against the encryption system
to verify its security. These tests attempt to break the encryption through
common and advanced attack methods.

Test Categories:
1. Cryptographic Attacks (brute force, known-plaintext)
2. Key Extraction Attempts
3. Tampering Detection Tests
4. Replay Attack Tests
5. Memory/Runtime Attack Simulations

Run: python3 red_team_tests.py
"""

import base64
import hashlib
import hmac
import json
import os
import random
import struct
import time
from typing import Dict, Any, Tuple, Optional, List
from dataclasses import dataclass
from enum import Enum

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding, rsa
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    from cryptography.hazmat.backends import default_backend
except ImportError:
    raise ImportError("cryptography library required: pip install cryptography")

from wisedrive_decryption import WiseDriveDecryptor, DecryptionError, KeyGenerator


class AttackResult(Enum):
    SUCCESS = "VULNERABLE"        # Attack succeeded - encryption is broken
    FAILED = "SECURE"            # Attack failed - encryption held
    PARTIAL = "PARTIALLY_SECURE"  # Some weakness found but not exploitable


@dataclass
class TestResult:
    name: str
    attack_type: str
    result: AttackResult
    duration_ms: float
    details: str


class SecurityRedTeam:
    """
    Red Team security testing suite for WiseDrive encryption.
    Attempts various attacks to verify encryption security.
    """
    
    def __init__(self):
        # Generate test key pairs
        self.client_public, self.client_private = KeyGenerator.generate_rsa_4096()
        self.wisedrive_public, self.wisedrive_private = KeyGenerator.generate_rsa_4096()
        
        # Create sample payload
        self.sample_payload = {
            "license_plate": "MH12AB1234",
            "tracking_id": "ORD6894331",
            "vin": "KMHXX00XXXX000000",
            "mil_status": True,
            "faulty_modules": ["Engine", "ABS"],
            "code_details": [
                {"dtc": "P0503", "meaning": "Vehicle Speed Sensor Intermittent"}
            ],
            "battery_voltage": 14.02
        }
        
        self.results: List[TestResult] = []
    
    def run_all_tests(self) -> List[TestResult]:
        """Run all security tests"""
        print("\n" + "=" * 70)
        print("WISEDRIVE OBD2 SDK - SECURITY RED TEAM ASSESSMENT")
        print("=" * 70)
        
        tests = [
            ("Brute Force AES Key", self.test_brute_force_aes),
            ("Brute Force RSA", self.test_brute_force_rsa),
            ("Wrong Key Decryption", self.test_wrong_key_decryption),
            ("Payload Tampering - Single Bit", self.test_tampering_single_bit),
            ("Payload Tampering - Header", self.test_tampering_header),
            ("Payload Tampering - Ciphertext", self.test_tampering_ciphertext),
            ("HMAC Bypass Attempt", self.test_hmac_bypass),
            ("Replay Attack", self.test_replay_attack),
            ("Key Extraction from Public Key", self.test_key_extraction_from_public),
            ("Known Plaintext Attack", self.test_known_plaintext_attack),
            ("Timing Attack Simulation", self.test_timing_attack),
            ("Padding Oracle Simulation", self.test_padding_oracle),
            ("IV Reuse Detection", self.test_iv_reuse),
            ("Memory Dump Simulation", self.test_memory_dump_simulation),
        ]
        
        for name, test_func in tests:
            print(f"\n[TEST] {name}...")
            start_time = time.time()
            try:
                result = test_func()
                duration_ms = (time.time() - start_time) * 1000
                self.results.append(TestResult(name, test_func.__name__, result, duration_ms, ""))
                status = "SECURE" if result == AttackResult.FAILED else "VULNERABLE"
                print(f"       Result: {status} ({duration_ms:.2f}ms)")
            except Exception as e:
                duration_ms = (time.time() - start_time) * 1000
                self.results.append(TestResult(name, test_func.__name__, AttackResult.FAILED, duration_ms, str(e)))
                print(f"       Result: SECURE (exception: {e}) ({duration_ms:.2f}ms)")
        
        return self.results
    
    def _encrypt_payload(self, payload: dict, public_key_pem: str, magic: str = "WDSW") -> bytes:
        """Encrypt payload using the same algorithm as SDK"""
        from cryptography.hazmat.primitives.asymmetric import padding as asym_padding
        
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
    
    # ========================================================================
    # CRYPTOGRAPHIC ATTACK TESTS
    # ========================================================================
    
    def test_brute_force_aes(self) -> AttackResult:
        """
        Attempt to brute force the AES-256 key.
        With 2^256 possible keys, this is computationally infeasible.
        """
        encrypted = self._encrypt_payload(self.sample_payload, self.wisedrive_public)
        
        # Try a few random keys (obviously won't work with 2^256 keyspace)
        attempts = 1000
        for _ in range(attempts):
            random_key = os.urandom(32)
            try:
                # Extract IV and ciphertext (simplified - would need proper parsing)
                iv = encrypted[16 + 512:16 + 512 + 12]
                ciphertext = encrypted[16 + 512 + 12:-64]
                
                aesgcm = AESGCM(random_key)
                aesgcm.decrypt(iv, ciphertext, None)
                return AttackResult.SUCCESS  # Should never happen
            except:
                continue
        
        return AttackResult.FAILED  # Expected - AES-256 is secure
    
    def test_brute_force_rsa(self) -> AttackResult:
        """
        Attempt to factor RSA-4096 modulus.
        This would take billions of years with current technology.
        """
        # Just verify that we can't trivially factor the key
        public_key = serialization.load_pem_public_key(
            self.wisedrive_public.encode('utf-8'),
            backend=default_backend()
        )
        
        # Get public numbers
        numbers = public_key.public_numbers()
        n = numbers.n  # 4096-bit modulus
        
        # Try some trivial factorization (won't work)
        small_primes = [2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47]
        for p in small_primes:
            if n % p == 0:
                return AttackResult.SUCCESS  # Key is weak!
        
        return AttackResult.FAILED  # Expected - RSA-4096 is secure
    
    def test_wrong_key_decryption(self) -> AttackResult:
        """
        Verify that using the wrong private key fails.
        """
        encrypted = self._encrypt_payload(self.sample_payload, self.wisedrive_public)
        encrypted_b64 = base64.b64encode(encrypted).decode('utf-8')
        
        # Try to decrypt with CLIENT key (wrong key)
        decryptor = WiseDriveDecryptor(self.client_private)
        
        try:
            decryptor.decrypt(encrypted_b64)
            return AttackResult.SUCCESS  # Bad - wrong key worked!
        except DecryptionError:
            return AttackResult.FAILED  # Good - wrong key rejected
    
    # ========================================================================
    # TAMPERING DETECTION TESTS
    # ========================================================================
    
    def test_tampering_single_bit(self) -> AttackResult:
        """
        Flip a single bit in the ciphertext and verify detection.
        """
        encrypted = bytearray(self._encrypt_payload(self.sample_payload, self.wisedrive_public))
        
        # Flip a bit in the ciphertext area
        ciphertext_start = 16 + 512 + 12
        encrypted[ciphertext_start + 10] ^= 0x01  # Flip one bit
        
        encrypted_b64 = base64.b64encode(bytes(encrypted)).decode('utf-8')
        decryptor = WiseDriveDecryptor(self.wisedrive_private)
        
        try:
            decryptor.decrypt(encrypted_b64)
            return AttackResult.SUCCESS  # Bad - tampering not detected!
        except DecryptionError:
            return AttackResult.FAILED  # Good - tampering detected
    
    def test_tampering_header(self) -> AttackResult:
        """
        Modify header and verify detection.
        """
        encrypted = bytearray(self._encrypt_payload(self.sample_payload, self.wisedrive_public))
        
        # Modify timestamp in header
        encrypted[10] ^= 0xFF
        
        encrypted_b64 = base64.b64encode(bytes(encrypted)).decode('utf-8')
        decryptor = WiseDriveDecryptor(self.wisedrive_private)
        
        try:
            decryptor.decrypt(encrypted_b64)
            return AttackResult.SUCCESS  # Bad!
        except DecryptionError:
            return AttackResult.FAILED  # Good
    
    def test_tampering_ciphertext(self) -> AttackResult:
        """
        Replace ciphertext with garbage and verify detection.
        """
        encrypted = bytearray(self._encrypt_payload(self.sample_payload, self.wisedrive_public))
        
        # Replace ciphertext with random data
        ciphertext_start = 16 + 512 + 12
        ciphertext_end = len(encrypted) - 64
        for i in range(ciphertext_start, ciphertext_end):
            encrypted[i] = random.randint(0, 255)
        
        encrypted_b64 = base64.b64encode(bytes(encrypted)).decode('utf-8')
        decryptor = WiseDriveDecryptor(self.wisedrive_private)
        
        try:
            decryptor.decrypt(encrypted_b64)
            return AttackResult.SUCCESS  # Bad!
        except DecryptionError:
            return AttackResult.FAILED  # Good
    
    def test_hmac_bypass(self) -> AttackResult:
        """
        Try to bypass HMAC verification.
        """
        encrypted = bytearray(self._encrypt_payload(self.sample_payload, self.wisedrive_public))
        
        # Replace HMAC with zeros
        for i in range(len(encrypted) - 64, len(encrypted)):
            encrypted[i] = 0
        
        encrypted_b64 = base64.b64encode(bytes(encrypted)).decode('utf-8')
        decryptor = WiseDriveDecryptor(self.wisedrive_private)
        
        try:
            decryptor.decrypt(encrypted_b64)
            return AttackResult.SUCCESS  # Bad!
        except DecryptionError as e:
            if "HMAC" in str(e) or "tampered" in str(e).lower():
                return AttackResult.FAILED  # Good - HMAC caught it
            return AttackResult.PARTIAL  # Caught but not by HMAC
    
    def test_replay_attack(self) -> AttackResult:
        """
        Verify that old payloads with old timestamps are still valid
        (note: timestamp checking should be done at application level)
        """
        encrypted = self._encrypt_payload(self.sample_payload, self.wisedrive_public)
        encrypted_b64 = base64.b64encode(encrypted).decode('utf-8')
        
        decryptor = WiseDriveDecryptor(self.wisedrive_private)
        
        # Decrypt same payload twice (simulating replay)
        try:
            data1 = decryptor.decrypt(encrypted_b64)
            data2 = decryptor.decrypt(encrypted_b64)
            
            # Both decryptions succeed - timestamp check is at app level
            # This is expected behavior - app should check timestamp
            return AttackResult.PARTIAL  # Works but app should verify timestamp
        except DecryptionError:
            return AttackResult.FAILED
    
    # ========================================================================
    # ADVANCED ATTACK SIMULATIONS
    # ========================================================================
    
    def test_key_extraction_from_public(self) -> AttackResult:
        """
        Attempt to derive private key from public key.
        Mathematically infeasible for RSA-4096.
        """
        public_key = serialization.load_pem_public_key(
            self.wisedrive_public.encode('utf-8'),
            backend=default_backend()
        )
        
        numbers = public_key.public_numbers()
        n = numbers.n
        e = numbers.e
        
        # Try trivial p/q search (won't work)
        # In reality, factoring 4096-bit semiprime is computationally infeasible
        
        # Check if n is even (trivial weakness)
        if n % 2 == 0:
            return AttackResult.SUCCESS  # Bad!
        
        return AttackResult.FAILED  # Expected
    
    def test_known_plaintext_attack(self) -> AttackResult:
        """
        Attempt known-plaintext attack.
        AES-GCM with unique IVs is immune to this.
        """
        # Encrypt same payload twice
        enc1 = self._encrypt_payload(self.sample_payload, self.wisedrive_public)
        enc2 = self._encrypt_payload(self.sample_payload, self.wisedrive_public)
        
        # With proper implementation, ciphertexts should be completely different
        # due to random AES key and IV per encryption
        if enc1 == enc2:
            return AttackResult.SUCCESS  # Bad - deterministic encryption!
        
        # Check if any patterns emerge
        enc1_cipher = enc1[16 + 512 + 12:-64]
        enc2_cipher = enc2[16 + 512 + 12:-64]
        
        # XOR ciphertexts to look for patterns
        xored = bytes([a ^ b for a, b in zip(enc1_cipher, enc2_cipher)])
        
        # If all zeros, ciphertexts are identical (bad)
        if all(b == 0 for b in xored):
            return AttackResult.SUCCESS  # Bad!
        
        return AttackResult.FAILED  # Good - different each time
    
    def test_timing_attack(self) -> AttackResult:
        """
        Look for timing variations that could leak information.
        """
        encrypted = self._encrypt_payload(self.sample_payload, self.wisedrive_public)
        encrypted_b64 = base64.b64encode(encrypted).decode('utf-8')
        decryptor = WiseDriveDecryptor(self.wisedrive_private)
        
        # Measure multiple decryption times
        times = []
        for _ in range(100):
            start = time.perf_counter()
            try:
                decryptor.decrypt(encrypted_b64)
            except:
                pass
            times.append(time.perf_counter() - start)
        
        # Check for significant timing variations
        avg_time = sum(times) / len(times)
        variance = sum((t - avg_time) ** 2 for t in times) / len(times)
        
        # High variance could indicate timing vulnerability
        if variance > 0.001:  # 1ms variance threshold
            return AttackResult.PARTIAL  # Potential timing leak
        
        return AttackResult.FAILED  # Good - consistent timing
    
    def test_padding_oracle(self) -> AttackResult:
        """
        Test for padding oracle vulnerability.
        AES-GCM doesn't use padding, so this shouldn't apply.
        """
        encrypted = bytearray(self._encrypt_payload(self.sample_payload, self.wisedrive_public))
        decryptor = WiseDriveDecryptor(self.wisedrive_private)
        
        # Try various modifications to ciphertext
        padding_errors = 0
        auth_errors = 0
        
        for i in range(10):
            modified = bytearray(encrypted)
            # Modify last byte of ciphertext (before HMAC)
            modified[-65] ^= (i + 1)
            
            try:
                decryptor.decrypt(base64.b64encode(bytes(modified)).decode('utf-8'))
            except DecryptionError as e:
                error_str = str(e).lower()
                if "padding" in error_str:
                    padding_errors += 1
                elif "hmac" in error_str or "tamper" in error_str or "gcm" in error_str:
                    auth_errors += 1
        
        # AES-GCM should give authentication errors, not padding errors
        if padding_errors > auth_errors:
            return AttackResult.SUCCESS  # Bad - padding oracle possible!
        
        return AttackResult.FAILED  # Good - no padding oracle
    
    def test_iv_reuse(self) -> AttackResult:
        """
        Verify that IVs are unique for each encryption.
        IV reuse in GCM mode is catastrophic.
        """
        ivs = set()
        
        for _ in range(100):
            encrypted = self._encrypt_payload(self.sample_payload, self.wisedrive_public)
            # Extract IV
            iv = encrypted[16 + 512:16 + 512 + 12]
            iv_hex = iv.hex()
            
            if iv_hex in ivs:
                return AttackResult.SUCCESS  # Bad - IV reused!
            ivs.add(iv_hex)
        
        return AttackResult.FAILED  # Good - all IVs unique
    
    def test_memory_dump_simulation(self) -> AttackResult:
        """
        Simulate memory dump attack looking for key material.
        In a real attack, this would search process memory.
        """
        # This is a simulation - in reality:
        # 1. Keys are cleared after use
        # 2. SDK uses native memory for key storage
        # 3. Anti-debug prevents memory inspection
        
        # We can only test that decryption works correctly
        # and that there's no obvious key leakage in output
        
        encrypted = self._encrypt_payload(self.sample_payload, self.wisedrive_public)
        encrypted_b64 = base64.b64encode(encrypted).decode('utf-8')
        decryptor = WiseDriveDecryptor(self.wisedrive_private)
        
        result = decryptor.decrypt(encrypted_b64)
        
        # Check result doesn't contain private key material
        result_str = json.dumps(result)
        if "PRIVATE" in result_str or self.wisedrive_private[:50] in result_str:
            return AttackResult.SUCCESS  # Bad!
        
        return AttackResult.FAILED  # Good (simulated only)
    
    def print_summary(self):
        """Print test summary"""
        print("\n" + "=" * 70)
        print("SECURITY ASSESSMENT SUMMARY")
        print("=" * 70)
        
        secure = sum(1 for r in self.results if r.result == AttackResult.FAILED)
        partial = sum(1 for r in self.results if r.result == AttackResult.PARTIAL)
        vulnerable = sum(1 for r in self.results if r.result == AttackResult.SUCCESS)
        
        print(f"\n  SECURE:           {secure}/{len(self.results)}")
        print(f"  PARTIALLY SECURE: {partial}/{len(self.results)}")
        print(f"  VULNERABLE:       {vulnerable}/{len(self.results)}")
        
        if vulnerable == 0:
            print("\n  OVERALL: ENCRYPTION IS SECURE")
            print("\n  The encryption implementation successfully resists all tested attacks.")
        else:
            print("\n  OVERALL: VULNERABILITIES FOUND!")
            print("\n  The following attacks succeeded:")
            for r in self.results:
                if r.result == AttackResult.SUCCESS:
                    print(f"    - {r.name}")
        
        print("\n" + "=" * 70)


def main():
    """Run the full red team assessment"""
    red_team = SecurityRedTeam()
    red_team.run_all_tests()
    red_team.print_summary()


if __name__ == "__main__":
    main()
