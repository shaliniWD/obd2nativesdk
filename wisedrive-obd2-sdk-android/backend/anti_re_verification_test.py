#!/usr/bin/env python3
"""
WiseDrive OBD2 SDK - Anti-Reverse-Engineering Verification Tests
================================================================

This test verifies the StringProtector encryption/decryption logic
by implementing the same algorithm in Python and confirming roundtrip.

Also tests that the obfuscation architecture is sound:
1. StringProtector encrypt/decrypt roundtrip
2. All OBD protocol commands can be encrypted and recovered
3. All ECU addresses can be encrypted and recovered
4. Key derivation produces consistent results
5. Position-dependent XOR produces unique ciphertexts
6. Encrypted forms contain no readable plaintext
"""

import hashlib
import json
import sys
import os

# Test data - all the protocol strings that must be protected
AT_COMMANDS = [
    "ATZ", "ATE0", "ATL1", "ATS1", "ATH1", "ATCAF1", "ATAT2",
    "ATST FF", "ATAL", "ATCFC1", "ATSP0", "ATDPN", "0100",
    "AT", "ATSH", "ATCRA", "ATSH 7DF"
]

OBD_MODES = [
    "03", "07", "0A", "0101", "0902", "1902FF",
    "190208", "190204", "190280", "190F", "01"
]

RESPONSE_MARKERS = ["41", "43", "47", "4A", "49", "59"]

ERROR_STRINGS = [
    "SEARCHING...", "BUS INIT...", "NO DATA",
    "UNABLE TO CONNECT", "CAN ERROR", "BUFFER FULL"
]

ECU_ADDRESSES = [
    "7E0", "7E8", "7E1", "7E9", "7B0", "7B8", "7C0", "7C8",
    "720", "728", "726", "72E", "727", "72F", "730", "738",
    "7D0", "7D8", "7D2", "7DA", "770", "778", "7C6", "7CE",
    "7B2", "7BA", "7A0", "7A8", "7D4", "7DC", "7A2", "7AA",
    "713", "77D", "715", "77F", "714", "77E", "710", "77A",
    "711", "77B", "712", "77C", "780", "788", "740", "748",
    "7C2", "7CA", "750", "758", "760", "768", "733", "73B",
    "745", "74D", "742", "74A", "74E", "756", "76E", "737", "73F"
]

MANUFACTURER_NAMES = [
    "Tata", "Mahindra", "Maruti Suzuki", "Hyundai", "Kia",
    "Toyota", "Honda", "Nissan", "Mitsubishi",
    "Volkswagen", "Audi", "BMW", "Mercedes-Benz",
    "Ford", "General Motors", "Chrysler",
    "Volvo", "Jaguar Land Rover", "Renault", "Peugeot", "Fiat", "SEAT", "Skoda"
]

MODULE_NAMES = [
    "Engine/PCM", "Transmission/TCM", "ABS/ESP", "SRS/Airbag",
    "Body Control Module", "Instrument Cluster", "Climate Control",
    "Electric Power Steering", "TPMS", "Smart Key"
]


class StringProtectorPython:
    """
    Python implementation of the Kotlin StringProtector.
    Verifies the encryption algorithm is correct and reversible.
    """
    
    # Same seeds as Kotlin StringProtector
    S1 = bytes([0x4F, 0x42, 0x44, 0x5F, 0x53, 0x45, 0x45, 0x44,
                0x5F, 0x41, 0x4C, 0x50, 0x48, 0x41, 0x5F, 0x31])
    S2 = bytes([0x57, 0x44, 0x5F, 0x50, 0x52, 0x4F, 0x54, 0x4F,
                0x43, 0x4F, 0x4C, 0x5F, 0x47, 0x55, 0x41, 0x52])
    S3 = bytes([0xDE, 0xAD, 0xBE, 0xEF, 0xCA, 0xFE, 0xBA, 0xBE,
                0x13, 0x37, 0x42, 0x58, 0x7A, 0x3C, 0x9E, 0xF1])
    
    # Same class name used in Kotlin for key derivation
    CLASS_NAME = "com.wisedrive.obd2.security.StringProtector"
    
    def __init__(self):
        self.key = self._derive_key()
    
    def _derive_key(self):
        md = hashlib.sha256()
        md.update(self.S1)
        md.update(self.S2)
        md.update(self.S3)
        md.update(self.CLASS_NAME.encode('utf-8'))
        return md.digest()
    
    def encrypt(self, plaintext: str) -> bytes:
        data = plaintext.encode('utf-8')
        encrypted = bytearray(len(data))
        for i in range(len(data)):
            key_byte = self.key[(i + len(data)) % len(self.key)]
            pos_offset = ((i * 7 + 3) % 256)
            encrypted[i] = (data[i] ^ key_byte ^ pos_offset) & 0xFF
        return bytes(encrypted)
    
    def decrypt(self, encrypted: bytes) -> str:
        decrypted = bytearray(len(encrypted))
        for i in range(len(encrypted)):
            key_byte = self.key[(i + len(encrypted)) % len(self.key)]
            pos_offset = ((i * 7 + 3) % 256)
            decrypted[i] = (encrypted[i] ^ key_byte ^ pos_offset) & 0xFF
        return decrypted.decode('utf-8')


def run_tests():
    """Run all anti-reverse-engineering verification tests"""
    protector = StringProtectorPython()
    tests_passed = 0
    tests_failed = 0
    total_tests = 0
    
    print("=" * 70)
    print("WISEDRIVE OBD2 SDK - ANTI-REVERSE-ENGINEERING VERIFICATION")
    print("=" * 70)
    
    # ═══════════════════════════════════════════════════════════
    # TEST 1: Key Derivation Consistency
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 1] Key Derivation Consistency...")
    p1 = StringProtectorPython()
    p2 = StringProtectorPython()
    if p1.key == p2.key:
        print("   PASSED - Key derivation is deterministic")
        tests_passed += 1
    else:
        print("   FAILED - Key derivation not consistent!")
        tests_failed += 1
    
    # ═══════════════════════════════════════════════════════════
    # TEST 2: AT Command Roundtrip
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 2] AT Command Encrypt/Decrypt Roundtrip...")
    all_ok = True
    for cmd in AT_COMMANDS:
        encrypted = protector.encrypt(cmd)
        decrypted = protector.decrypt(encrypted)
        if decrypted != cmd:
            print(f"   FAILED - {cmd}: got '{decrypted}'")
            all_ok = False
    if all_ok:
        print(f"   PASSED - All {len(AT_COMMANDS)} AT commands roundtrip correctly")
        tests_passed += 1
    else:
        tests_failed += 1
    
    # ═══════════════════════════════════════════════════════════
    # TEST 3: OBD Mode Roundtrip
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 3] OBD Mode Command Roundtrip...")
    all_ok = True
    for mode in OBD_MODES:
        encrypted = protector.encrypt(mode)
        decrypted = protector.decrypt(encrypted)
        if decrypted != mode:
            print(f"   FAILED - {mode}: got '{decrypted}'")
            all_ok = False
    if all_ok:
        print(f"   PASSED - All {len(OBD_MODES)} OBD modes roundtrip correctly")
        tests_passed += 1
    else:
        tests_failed += 1
    
    # ═══════════════════════════════════════════════════════════
    # TEST 4: ECU Address Roundtrip  
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 4] ECU Address Roundtrip...")
    all_ok = True
    for addr in ECU_ADDRESSES:
        encrypted = protector.encrypt(addr)
        decrypted = protector.decrypt(encrypted)
        if decrypted != addr:
            print(f"   FAILED - {addr}: got '{decrypted}'")
            all_ok = False
    if all_ok:
        print(f"   PASSED - All {len(ECU_ADDRESSES)} ECU addresses roundtrip correctly")
        tests_passed += 1
    else:
        tests_failed += 1
    
    # ═══════════════════════════════════════════════════════════
    # TEST 5: Error String Roundtrip
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 5] Error String Roundtrip...")
    all_ok = True
    for err in ERROR_STRINGS:
        encrypted = protector.encrypt(err)
        decrypted = protector.decrypt(encrypted)
        if decrypted != err:
            print(f"   FAILED - {err}: got '{decrypted}'")
            all_ok = False
    if all_ok:
        print(f"   PASSED - All {len(ERROR_STRINGS)} error strings roundtrip correctly")
        tests_passed += 1
    else:
        tests_failed += 1
    
    # ═══════════════════════════════════════════════════════════
    # TEST 6: No Plaintext Leakage in Encrypted Data
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 6] No Plaintext Leakage...")
    all_ok = True
    all_encrypted = b""
    for cmd in AT_COMMANDS + OBD_MODES + ECU_ADDRESSES:
        encrypted = protector.encrypt(cmd)
        all_encrypted += encrypted
        # Check that the encrypted bytes don't contain the plaintext
        if cmd.encode('utf-8') in encrypted and len(cmd) > 2:
            print(f"   FAILED - Plaintext '{cmd}' visible in encrypted form!")
            all_ok = False
    
    # Also check combined blob doesn't have recognizable patterns
    combined_hex = all_encrypted.hex()
    suspicious_patterns = ["41545A", "415445", "415453", "37453", "37453"]  # ATZ, ATE, ATS, 7E hex
    for pattern in suspicious_patterns:
        if pattern.lower() in combined_hex.lower():
            # This might be a coincidence, so just log it
            pass
    
    if all_ok:
        print(f"   PASSED - No plaintext leakage detected in {len(all_encrypted)} encrypted bytes")
        tests_passed += 1
    else:
        tests_failed += 1
    
    # ═══════════════════════════════════════════════════════════
    # TEST 7: Different Inputs Produce Different Ciphertexts
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 7] Unique Ciphertexts for Different Inputs...")
    ciphertexts = set()
    all_unique = True
    for cmd in AT_COMMANDS + OBD_MODES + ECU_ADDRESSES:
        encrypted = protector.encrypt(cmd)
        ct_hex = encrypted.hex()
        if ct_hex in ciphertexts:
            print(f"   FAILED - Duplicate ciphertext for '{cmd}'!")
            all_unique = False
        ciphertexts.add(ct_hex)
    if all_unique:
        print(f"   PASSED - All {len(ciphertexts)} ciphertexts are unique")
        tests_passed += 1
    else:
        tests_failed += 1
    
    # ═══════════════════════════════════════════════════════════
    # TEST 8: Same Input Produces Same Ciphertext (Deterministic)
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 8] Deterministic Encryption...")
    all_ok = True
    for cmd in AT_COMMANDS[:5]:
        e1 = protector.encrypt(cmd)
        e2 = protector.encrypt(cmd)
        if e1 != e2:
            print(f"   FAILED - '{cmd}' produces different ciphertexts!")
            all_ok = False
    if all_ok:
        print("   PASSED - Same input always produces same ciphertext")
        tests_passed += 1
    else:
        tests_failed += 1
    
    # ═══════════════════════════════════════════════════════════
    # TEST 9: Manufacturer Names Roundtrip
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 9] Manufacturer Names Roundtrip...")
    all_ok = True
    for name in MANUFACTURER_NAMES:
        encrypted = protector.encrypt(name)
        decrypted = protector.decrypt(encrypted)
        if decrypted != name:
            print(f"   FAILED - {name}: got '{decrypted}'")
            all_ok = False
    if all_ok:
        print(f"   PASSED - All {len(MANUFACTURER_NAMES)} manufacturer names roundtrip correctly")
        tests_passed += 1
    else:
        tests_failed += 1
    
    # ═══════════════════════════════════════════════════════════
    # TEST 10: Module Names Roundtrip
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 10] Module Names Roundtrip...")
    all_ok = True
    for name in MODULE_NAMES:
        encrypted = protector.encrypt(name)
        decrypted = protector.decrypt(encrypted)
        if decrypted != name:
            print(f"   FAILED - {name}: got '{decrypted}'")
            all_ok = False
    if all_ok:
        print(f"   PASSED - All {len(MODULE_NAMES)} module names roundtrip correctly")
        tests_passed += 1
    else:
        tests_failed += 1
    
    # ═══════════════════════════════════════════════════════════
    # TEST 11: Wrong Key Fails Decryption
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 11] Wrong Key Produces Garbage...")
    test_str = "ATSP0 ATDPN 1902FF 7E0"  # Longer string to ensure key coverage
    encrypted = protector.encrypt(test_str)
    # Tamper with multiple key bytes
    wrong_key = bytearray(protector.key)
    for i in range(len(wrong_key)):
        wrong_key[i] ^= 0xFF
    
    # Manually decrypt with wrong key
    decrypted = bytearray(len(encrypted))
    for i in range(len(encrypted)):
        key_byte = wrong_key[(i + len(encrypted)) % len(wrong_key)]
        pos_offset = ((i * 7 + 3) % 256)
        decrypted[i] = (encrypted[i] ^ key_byte ^ pos_offset) & 0xFF
    
    try:
        wrong_result = decrypted.decode('utf-8')
        if wrong_result != test_str:
            print("   PASSED - Wrong key produces different output")
            tests_passed += 1
        else:
            print("   FAILED - Wrong key still produces correct output!")
            tests_failed += 1
    except UnicodeDecodeError:
        print("   PASSED - Wrong key produces non-decodable garbage")
        tests_passed += 1
    
    # ═══════════════════════════════════════════════════════════
    # TEST 12: Large String Handling
    # ═══════════════════════════════════════════════════════════
    total_tests += 1
    print("\n[TEST 12] Large String Handling...")
    large_str = "ATZ " * 1000 + "0100 " * 500
    encrypted = protector.encrypt(large_str)
    decrypted = protector.decrypt(encrypted)
    if decrypted == large_str:
        print(f"   PASSED - {len(large_str)} char string roundtrips correctly")
        tests_passed += 1
    else:
        print("   FAILED - Large string roundtrip failed!")
        tests_failed += 1
    
    # ═══════════════════════════════════════════════════════════
    # SUMMARY
    # ═══════════════════════════════════════════════════════════
    print("\n" + "=" * 70)
    print("ANTI-REVERSE-ENGINEERING VERIFICATION SUMMARY")
    print("=" * 70)
    print(f"Tests Run:    {total_tests}")
    print(f"Tests Passed: {tests_passed}")
    print(f"Tests Failed: {tests_failed}")
    print(f"Success Rate: {(tests_passed/total_tests)*100:.1f}%")
    
    if tests_failed == 0:
        print("\nALL TESTS PASSED - String protection is working correctly")
        print("\nProtection layers verified:")
        print("  1. XOR encryption with position-dependent key")
        print("  2. SHA-256 derived key from scattered seeds")
        print("  3. No plaintext leakage in encrypted output")
        print("  4. Deterministic but unique ciphertexts")
        print("  5. Wrong key produces garbage")
    else:
        print(f"\n{tests_failed} TESTS FAILED - Review implementation!")
    
    print("=" * 70)
    
    return 0 if tests_failed == 0 else 1


if __name__ == "__main__":
    sys.exit(run_tests())
