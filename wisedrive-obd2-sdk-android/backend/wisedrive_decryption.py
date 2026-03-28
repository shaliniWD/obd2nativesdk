"""
WiseDrive OBD2 SDK - Backend Decryption Module
==============================================

This module provides decryption functionality for encrypted OBD scan data
from the WiseDrive OBD2 Android SDK.

IMPORTANT: This file should be stored securely on your backend server.
The private key should NEVER be exposed or committed to version control.

Requirements:
    pip install cryptography

Usage:
    from wisedrive_decryption import WiseDriveDecryptor
    
    decryptor = WiseDriveDecryptor(private_key_pem)
    scan_data = decryptor.decrypt(encrypted_payload_base64)
"""

import base64
import hashlib
import hmac
import json
import struct
from typing import Dict, Any, Tuple, Optional
from dataclasses import dataclass
from enum import Enum

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding, rsa
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.backends import default_backend
except ImportError:
    raise ImportError("cryptography library required. Install with: pip install cryptography")


class DecryptionError(Exception):
    """Base exception for decryption errors"""
    pass


class PayloadType(Enum):
    CLIENT = "WDSC"      # Client app encrypted data
    WISEDRIVE = "WDSW"   # WiseDrive analytics encrypted data


@dataclass
class EncryptedPayloadHeader:
    """Parsed header from encrypted payload"""
    magic: str           # WDSC or WDSW
    version: int         # Encryption version
    key_id: int          # Key identifier for rotation
    timestamp: int       # Encryption timestamp
    payload_type: PayloadType


class WiseDriveDecryptor:
    """
    Decrypts OBD scan data encrypted by WiseDrive OBD2 Android SDK.
    
    The SDK uses Hybrid RSA-4096 + AES-256-GCM encryption:
    1. Random AES-256 key generated per encryption
    2. Data encrypted with AES-256-GCM (authenticated encryption)
    3. AES key encrypted with RSA-4096-OAEP
    4. HMAC-SHA512 for integrity verification
    
    Security Features:
    - Perfect Forward Secrecy (new key per encryption)
    - Authenticated Encryption (tampering detected)
    - Strong key encryption (RSA-4096)
    """
    
    # Sizes in bytes
    HEADER_SIZE = 16
    RSA_KEY_SIZE_4096 = 512  # 4096 bits = 512 bytes
    RSA_KEY_SIZE_2048 = 256  # 2048 bits = 256 bytes
    GCM_IV_SIZE = 12
    GCM_TAG_SIZE = 16
    HMAC_SIZE = 64  # SHA-512 = 64 bytes
    
    def __init__(self, private_key_pem: str, key_id: Optional[int] = None):
        """
        Initialize decryptor with RSA private key.
        
        Args:
            private_key_pem: PEM-encoded RSA private key
            key_id: Optional key ID for multi-key support
        """
        self.private_key = serialization.load_pem_private_key(
            private_key_pem.encode('utf-8'),
            password=None,
            backend=default_backend()
        )
        self.key_id = key_id
        
        # Determine RSA key size
        key_size = self.private_key.key_size
        if key_size == 4096:
            self.rsa_encrypted_size = self.RSA_KEY_SIZE_4096
        elif key_size == 2048:
            self.rsa_encrypted_size = self.RSA_KEY_SIZE_2048
        else:
            raise ValueError(f"Unsupported RSA key size: {key_size}. Use 2048 or 4096.")
    
    def decrypt(self, encrypted_base64: str) -> Dict[str, Any]:
        """
        Decrypt encrypted OBD scan data.
        
        Args:
            encrypted_base64: Base64-encoded encrypted payload from SDK
            
        Returns:
            Decrypted JSON data as dictionary
            
        Raises:
            DecryptionError: If decryption fails (tampered, wrong key, etc.)
        """
        try:
            # 1. Decode base64
            encrypted_data = base64.b64decode(encrypted_base64)
            
            # 2. Verify minimum size
            min_size = self.HEADER_SIZE + self.rsa_encrypted_size + self.GCM_IV_SIZE + self.GCM_TAG_SIZE + self.HMAC_SIZE
            if len(encrypted_data) < min_size:
                raise DecryptionError(f"Payload too small: {len(encrypted_data)} bytes (minimum: {min_size})")
            
            # 3. Parse header
            header = self._parse_header(encrypted_data[:self.HEADER_SIZE])
            
            # 4. Verify key ID if specified
            if self.key_id is not None and header.key_id != self.key_id:
                raise DecryptionError(f"Key ID mismatch: expected {self.key_id}, got {header.key_id}")
            
            # 5. Extract components
            offset = self.HEADER_SIZE
            encrypted_aes_key = encrypted_data[offset:offset + self.rsa_encrypted_size]
            offset += self.rsa_encrypted_size
            
            iv = encrypted_data[offset:offset + self.GCM_IV_SIZE]
            offset += self.GCM_IV_SIZE
            
            ciphertext_with_tag = encrypted_data[offset:-self.HMAC_SIZE]
            hmac_signature = encrypted_data[-self.HMAC_SIZE:]
            
            # 6. Decrypt AES key with RSA
            aes_key = self._decrypt_aes_key(encrypted_aes_key)
            
            # 7. Derive HMAC key and verify
            hmac_key = self._derive_hmac_key(aes_key)
            data_to_verify = encrypted_data[:-self.HMAC_SIZE]
            self._verify_hmac(data_to_verify, hmac_signature, hmac_key)
            
            # 8. Decrypt data with AES-GCM
            plaintext = self._decrypt_aes_gcm(ciphertext_with_tag, aes_key, iv)
            
            # 9. Parse JSON
            return json.loads(plaintext.decode('utf-8'))
            
        except DecryptionError:
            raise
        except Exception as e:
            raise DecryptionError(f"Decryption failed: {str(e)}")
    
    def _parse_header(self, header_bytes: bytes) -> EncryptedPayloadHeader:
        """Parse the 16-byte header"""
        magic = header_bytes[0:4].decode('utf-8')
        
        if magic not in ('WDSC', 'WDSW'):
            raise DecryptionError(f"Invalid magic bytes: {magic}")
        
        version = struct.unpack('>H', header_bytes[4:6])[0]
        key_id = struct.unpack('>I', header_bytes[6:10])[0]
        
        # Timestamp is 6 bytes in the header
        timestamp_bytes = b'\x00\x00' + header_bytes[10:16]
        timestamp = struct.unpack('>Q', timestamp_bytes)[0]
        
        payload_type = PayloadType.CLIENT if magic == 'WDSC' else PayloadType.WISEDRIVE
        
        return EncryptedPayloadHeader(
            magic=magic,
            version=version,
            key_id=key_id,
            timestamp=timestamp,
            payload_type=payload_type
        )
    
    def _decrypt_aes_key(self, encrypted_key: bytes) -> bytes:
        """Decrypt the AES key using RSA-OAEP"""
        try:
            return self.private_key.decrypt(
                encrypted_key,
                padding.OAEP(
                    mgf=padding.MGF1(algorithm=hashes.SHA256()),
                    algorithm=hashes.SHA256(),
                    label=None
                )
            )
        except Exception as e:
            raise DecryptionError(f"RSA decryption failed (wrong key?): {str(e)}")
    
    def _derive_hmac_key(self, aes_key: bytes) -> bytes:
        """Derive HMAC key from AES key using SHA-256"""
        h = hashlib.sha256()
        h.update(aes_key)
        h.update(b"HMAC_KEY_DERIVATION")
        return h.digest()
    
    def _verify_hmac(self, data: bytes, signature: bytes, key: bytes) -> None:
        """Verify HMAC-SHA512 signature"""
        expected = hmac.new(key, data, hashlib.sha512).digest()
        if not hmac.compare_digest(expected, signature):
            raise DecryptionError("HMAC verification failed - payload has been tampered!")
    
    def _decrypt_aes_gcm(self, ciphertext_with_tag: bytes, key: bytes, iv: bytes) -> bytes:
        """Decrypt data using AES-256-GCM"""
        try:
            aesgcm = AESGCM(key)
            return aesgcm.decrypt(iv, ciphertext_with_tag, None)
        except Exception as e:
            raise DecryptionError(f"AES-GCM decryption failed: {str(e)}")
    
    def get_header_info(self, encrypted_base64: str) -> EncryptedPayloadHeader:
        """
        Get header information without decrypting.
        Useful for routing or logging.
        """
        encrypted_data = base64.b64decode(encrypted_base64)
        return self._parse_header(encrypted_data[:self.HEADER_SIZE])


class KeyGenerator:
    """
    Utility class for generating RSA key pairs.
    Use this for initial key generation only.
    """
    
    @staticmethod
    def generate_rsa_4096() -> Tuple[str, str]:
        """
        Generate a new RSA-4096 key pair.
        
        Returns:
            Tuple of (public_key_pem, private_key_pem)
        """
        private_key = rsa.generate_private_key(
            public_exponent=65537,
            key_size=4096,
            backend=default_backend()
        )
        
        public_key = private_key.public_key()
        
        private_pem = private_key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption()
        ).decode('utf-8')
        
        public_pem = public_key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        ).decode('utf-8')
        
        return public_pem, private_pem
    
    @staticmethod
    def generate_rsa_2048() -> Tuple[str, str]:
        """Generate RSA-2048 key pair (faster but less secure)"""
        private_key = rsa.generate_private_key(
            public_exponent=65537,
            key_size=2048,
            backend=default_backend()
        )
        
        public_key = private_key.public_key()
        
        private_pem = private_key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption()
        ).decode('utf-8')
        
        public_pem = public_key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        ).decode('utf-8')
        
        return public_pem, private_pem


# ============================================================================
# EXAMPLE USAGE
# ============================================================================

def example_usage():
    """
    Complete example of key generation and decryption.
    """
    print("=" * 60)
    print("WiseDrive OBD2 Decryption Example")
    print("=" * 60)
    
    # Step 1: Generate keys (do this ONCE and store securely)
    print("\n1. Generating RSA-4096 key pair...")
    public_key, private_key = KeyGenerator.generate_rsa_4096()
    
    print("\nPUBLIC KEY (embed in SDK):")
    print(public_key[:200] + "...")
    
    print("\nPRIVATE KEY (keep secure on server):")
    print(private_key[:200] + "...")
    
    # Step 2: Initialize decryptor with private key
    print("\n2. Initializing decryptor...")
    decryptor = WiseDriveDecryptor(private_key)
    
    # Step 3: Decrypt received payload
    # This would come from your API endpoint
    print("\n3. To decrypt received payload:")
    print("""
    # In your Flask/FastAPI endpoint:
    
    @app.post("/api/webhook/obdreport")
    async def receive_obd_report(request: Request):
        body = await request.json()
        encrypted_payload = body["encryptedData"]
        
        decryptor = WiseDriveDecryptor(PRIVATE_KEY)
        
        try:
            scan_data = decryptor.decrypt(encrypted_payload)
            
            # Process decrypted data
            print(f"VIN: {scan_data['vin']}")
            print(f"License Plate: {scan_data['license_plate']}")
            print(f"DTCs: {len(scan_data['code_details'])}")
            
            return {"result": "SUCCESS"}
            
        except DecryptionError as e:
            return {"error": str(e)}, 400
    """)
    
    print("\n" + "=" * 60)
    print("Key Management Best Practices:")
    print("=" * 60)
    print("""
    1. NEVER commit private keys to version control
    2. Store private keys in secure vault (AWS KMS, HashiCorp Vault, etc.)
    3. Use environment variables for key loading
    4. Rotate keys periodically (SDK supports key ID versioning)
    5. Keep old private keys for decrypting historical data
    6. Monitor for decryption failures (potential tampering)
    """)


if __name__ == "__main__":
    example_usage()
