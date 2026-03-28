# WiseDrive OBD2 SDK - Advanced Encryption Architecture

## Overview

This document describes the military-grade encryption system for OBD scan data.
The system uses **Hybrid RSA + AES-256-GCM** encryption with dual key pairs.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SDK ENCRYPTION FLOW                                │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────┐
                              │   OBD Scan      │
                              │   Raw JSON      │
                              └────────┬────────┘
                                       │
                                       ▼
                    ┌──────────────────────────────────┐
                    │     ENCRYPTION LAYER (SDK)       │
                    │                                  │
                    │  1. Generate Random AES-256 Key  │
                    │  2. Encrypt JSON with AES-GCM    │
                    │  3. Encrypt AES Key with RSA     │
                    │  4. Sign with HMAC-SHA512        │
                    └──────────────────────────────────┘
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                      ▼
    ┌───────────────────────────────┐    ┌───────────────────────────────┐
    │   CLIENT APP ENCRYPTED BLOB   │    │   WISEDRIVE ENCRYPTED BLOB    │
    │                               │    │                               │
    │  ┌─────────────────────────┐  │    │  ┌─────────────────────────┐  │
    │  │ Header (16 bytes)       │  │    │  │ Header (16 bytes)       │  │
    │  │ - Magic: "WDSC"         │  │    │  │ - Magic: "WDSW"         │  │
    │  │ - Version: 2            │  │    │  │ - Version: 2            │  │
    │  │ - Key ID: 4 bytes       │  │    │  │ - Key ID: 4 bytes       │  │
    │  │ - Timestamp: 8 bytes    │  │    │  │ - Timestamp: 8 bytes    │  │
    │  ├─────────────────────────┤  │    │  ├─────────────────────────┤  │
    │  │ RSA Encrypted AES Key   │  │    │  │ RSA Encrypted AES Key   │  │
    │  │ (256/512 bytes)         │  │    │  │ (256/512 bytes)         │  │
    │  ├─────────────────────────┤  │    │  ├─────────────────────────┤  │
    │  │ IV (12 bytes)           │  │    │  │ IV (12 bytes)           │  │
    │  ├─────────────────────────┤  │    │  ├─────────────────────────┤  │
    │  │ AES-GCM Ciphertext      │  │    │  │ AES-GCM Ciphertext      │  │
    │  │ (Variable length)       │  │    │  │ (Variable length)       │  │
    │  ├─────────────────────────┤  │    │  ├─────────────────────────┤  │
    │  │ Auth Tag (16 bytes)     │  │    │  │ Auth Tag (16 bytes)     │  │
    │  ├─────────────────────────┤  │    │  ├─────────────────────────┤  │
    │  │ HMAC-SHA512 (64 bytes)  │  │    │  │ HMAC-SHA512 (64 bytes)  │  │
    │  └─────────────────────────┘  │    │  └─────────────────────────┘  │
    │                               │    │                               │
    │  Decryptable by:              │    │  Decryptable by:              │
    │  CLIENT_PRIVATE_KEY           │    │  WISEDRIVE_PRIVATE_KEY        │
    └───────────────────────────────┘    └───────────────────────────────┘
                    │                                      │
                    ▼                                      ▼
    ┌───────────────────────────────┐    ┌───────────────────────────────┐
    │      CLIENT BACKEND           │    │      WISEDRIVE BACKEND        │
    │                               │    │                               │
    │  Has: CLIENT_PRIVATE_KEY      │    │  Has: WISEDRIVE_PRIVATE_KEY   │
    │                               │    │                               │
    │  Can: Decrypt client blob     │    │  Can: Decrypt analytics blob  │
    │  Cannot: Decrypt WD blob      │    │  Cannot: Decrypt client blob  │
    └───────────────────────────────┘    └───────────────────────────────┘
```

## Encryption Layers

### Layer 1: AES-256-GCM (Data Encryption)
- **Algorithm**: AES-256-GCM (Galois/Counter Mode)
- **Key Size**: 256 bits (32 bytes)
- **IV Size**: 96 bits (12 bytes) - Randomly generated per encryption
- **Auth Tag**: 128 bits (16 bytes)
- **Purpose**: Fast symmetric encryption of large JSON payloads

### Layer 2: RSA-4096 (Key Encryption)
- **Algorithm**: RSA-OAEP with SHA-256
- **Key Size**: 4096 bits (512 bytes modulus)
- **Purpose**: Securely encrypt the random AES key
- **Note**: Each recipient (Client/WiseDrive) has their own RSA key pair

### Layer 3: HMAC-SHA512 (Integrity)
- **Algorithm**: HMAC-SHA512
- **Output**: 512 bits (64 bytes)
- **Purpose**: Detect tampering of encrypted payload
- **Key**: Derived from AES key using HKDF

### Layer 4: Anti-Tampering (Runtime Protection)
- Root detection
- Emulator detection
- Debugger detection
- Frida/Xposed detection
- Memory encryption for keys

## Key Management

### Public Keys (Embedded in SDK - Obfuscated)
```
CLIENT_PUBLIC_KEY    → Used to encrypt data for client backend
WISEDRIVE_PUBLIC_KEY → Used to encrypt data for WiseDrive analytics
```

### Private Keys (Server-Side Only)
```
CLIENT_PRIVATE_KEY    → Stored securely in client's backend
WISEDRIVE_PRIVATE_KEY → Stored securely in WiseDrive servers
```

### Key Rotation
- Keys can be rotated by updating SDK version
- Key ID in header identifies which key pair to use
- Old keys can be kept for backward compatibility

## Decryption Process (Backend)

### Step 1: Parse Header
```python
magic = data[0:4]      # "WDSC" or "WDSW"
version = data[4:6]    # Version number
key_id = data[6:10]    # Key identifier
timestamp = data[10:18] # Encryption timestamp
```

### Step 2: Extract Components
```python
rsa_key_size = 512  # 4096-bit RSA = 512 bytes
encrypted_aes_key = data[16:16+rsa_key_size]
iv = data[16+rsa_key_size:16+rsa_key_size+12]
ciphertext_with_tag = data[16+rsa_key_size+12:-64]
hmac_signature = data[-64:]
```

### Step 3: Verify HMAC
```python
# Compute HMAC over header + encrypted_key + iv + ciphertext
expected_hmac = hmac_sha512(hmac_key, data[:-64])
if expected_hmac != hmac_signature:
    raise SecurityError("Payload tampered!")
```

### Step 4: Decrypt AES Key
```python
aes_key = rsa_decrypt(encrypted_aes_key, private_key, OAEP_SHA256)
```

### Step 5: Decrypt Data
```python
plaintext = aes_gcm_decrypt(ciphertext_with_tag, aes_key, iv)
json_data = json.loads(plaintext)
```

## Security Features

### 1. Perfect Forward Secrecy
- New random AES key for every encryption
- Compromise of one message doesn't affect others

### 2. Authenticated Encryption
- AES-GCM provides both confidentiality and integrity
- Any bit flip in ciphertext causes decryption failure

### 3. Key Separation
- Different keys for different recipients
- Client cannot decrypt WiseDrive data and vice versa

### 4. Replay Protection
- Timestamp in header prevents replay attacks
- Backend can reject old payloads

### 5. Obfuscation
- Public keys split across multiple classes
- XOR encoding with runtime assembly
- ProGuard/R8 string encryption

## Attack Resistance

| Attack Vector | Protection |
|---------------|------------|
| Man-in-the-middle | RSA encryption, only private key holder can decrypt |
| Key extraction | Keys obfuscated, memory-only, no persistence |
| Reverse engineering | ProGuard, native code, integrity checks |
| Replay attack | Timestamp + nonce in header |
| Tampering | HMAC-SHA512 + AES-GCM auth tag |
| Brute force | AES-256 = 2^256 combinations, RSA-4096 = infeasible |
| Memory dump | Keys cleared after use, anti-debug |
| Frida/hooking | Frida detection, checksum verification |

## Backend Decryption Example (Python)

```python
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import hmac
import hashlib
import base64
import json

def decrypt_wisedrive_payload(encrypted_base64: str, private_key_pem: str) -> dict:
    """
    Decrypt WiseDrive OBD scan data
    
    Args:
        encrypted_base64: Base64-encoded encrypted payload
        private_key_pem: PEM-encoded RSA private key
    
    Returns:
        Decrypted JSON data as dictionary
    """
    # Decode base64
    data = base64.b64decode(encrypted_base64)
    
    # Parse header
    magic = data[0:4].decode('utf-8')
    if magic not in ('WDSC', 'WDSW'):
        raise ValueError(f"Invalid magic: {magic}")
    
    version = int.from_bytes(data[4:6], 'big')
    key_id = int.from_bytes(data[6:10], 'big')
    timestamp = int.from_bytes(data[10:18], 'big')
    
    # Extract components
    RSA_KEY_SIZE = 512  # 4096-bit
    offset = 16
    encrypted_aes_key = data[offset:offset+RSA_KEY_SIZE]
    offset += RSA_KEY_SIZE
    iv = data[offset:offset+12]
    offset += 12
    ciphertext_with_tag = data[offset:-64]
    hmac_signature = data[-64:]
    
    # Load private key
    private_key = serialization.load_pem_private_key(
        private_key_pem.encode(), password=None
    )
    
    # Decrypt AES key
    aes_key = private_key.decrypt(
        encrypted_aes_key,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None
        )
    )
    
    # Derive HMAC key from AES key
    hmac_key = hashlib.sha256(aes_key + b"HMAC_KEY").digest()
    
    # Verify HMAC
    expected_hmac = hmac.new(hmac_key, data[:-64], hashlib.sha512).digest()
    if not hmac.compare_digest(expected_hmac, hmac_signature):
        raise SecurityError("HMAC verification failed - payload tampered!")
    
    # Decrypt with AES-GCM
    aesgcm = AESGCM(aes_key)
    plaintext = aesgcm.decrypt(iv, ciphertext_with_tag, None)
    
    return json.loads(plaintext.decode('utf-8'))
```

## Key Generation (One-Time Setup)

```python
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization

# Generate WiseDrive key pair
wisedrive_private_key = rsa.generate_private_key(
    public_exponent=65537,
    key_size=4096
)
wisedrive_public_key = wisedrive_private_key.public_key()

# Generate Client key pair
client_private_key = rsa.generate_private_key(
    public_exponent=65537,
    key_size=4096
)
client_public_key = client_private_key.public_key()

# Export keys
def export_private_key(key):
    return key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption()
    ).decode()

def export_public_key(key):
    return key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    ).decode()

print("=== WISEDRIVE KEYS ===")
print(export_private_key(wisedrive_private_key))  # KEEP SECRET
print(export_public_key(wisedrive_public_key))    # Embed in SDK

print("=== CLIENT KEYS ===")
print(export_private_key(client_private_key))     # Give to client
print(export_public_key(client_public_key))       # Embed in SDK
```

## Version History

| Version | Changes |
|---------|---------|
| 1.0 | AES-256-GCM only |
| 2.0 | Hybrid RSA-4096 + AES-256-GCM, dual keys, HMAC-SHA512 |
