"""
WiseDrive OBD2 - Backend API Server
====================================

This is a complete Flask/FastAPI backend that receives encrypted OBD scan data
from the Android SDK and decrypts it.

Requirements:
    pip install flask cryptography python-dotenv

Environment Variables:
    WISEDRIVE_PRIVATE_KEY_PATH=/path/to/wisedrive_private.pem
    CLIENT_PRIVATE_KEY_PATH=/path/to/client_private.pem  (if you're a client)
    
Run:
    python3 api_server.py
"""

import os
import json
import logging
from datetime import datetime, timedelta
from functools import wraps
from typing import Optional

from flask import Flask, request, jsonify, g
from wisedrive_decryption import WiseDriveDecryptor, DecryptionError, KeyGenerator

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('WiseDriveAPI')

app = Flask(__name__)

# ============================================================================
# KEY MANAGEMENT
# ============================================================================

# In production, load from secure vault (AWS KMS, HashiCorp Vault, etc.)
# For demo, we generate keys on startup
DEMO_MODE = True

if DEMO_MODE:
    logger.info("Running in DEMO mode - generating new keys...")
    CLIENT_PUBLIC, CLIENT_PRIVATE = KeyGenerator.generate_rsa_4096()
    WISEDRIVE_PUBLIC, WISEDRIVE_PRIVATE = KeyGenerator.generate_rsa_4096()
    logger.info("Keys generated successfully")
else:
    # Production: Load from environment/file
    with open(os.environ['WISEDRIVE_PRIVATE_KEY_PATH'], 'r') as f:
        WISEDRIVE_PRIVATE = f.read()
    with open(os.environ.get('CLIENT_PRIVATE_KEY_PATH', '/dev/null'), 'r') as f:
        CLIENT_PRIVATE = f.read()

# Initialize decryptors
wisedrive_decryptor = WiseDriveDecryptor(WISEDRIVE_PRIVATE)
client_decryptor = WiseDriveDecryptor(CLIENT_PRIVATE) if CLIENT_PRIVATE else None

# Track seen payloads for replay protection
seen_payloads = {}  # timestamp -> set of hashes
REPLAY_WINDOW_MINUTES = 5

# ============================================================================
# SECURITY MIDDLEWARE
# ============================================================================

def validate_auth(f):
    """Validate API authentication"""
    @wraps(f)
    def decorated(*args, **kwargs):
        auth = request.headers.get('Authorization', '')
        
        # Basic auth check (in production, use proper auth)
        if auth != 'Basic cHJhc2FkOnByYXNhZEAxMjM=':
            return jsonify({'error': 'Unauthorized'}), 401
        
        return f(*args, **kwargs)
    return decorated


def check_replay(payload_hash: str, timestamp: int) -> bool:
    """Check for replay attacks"""
    current_time = datetime.now()
    
    # Clean old entries
    cutoff = current_time - timedelta(minutes=REPLAY_WINDOW_MINUTES)
    old_keys = [k for k in seen_payloads if k < cutoff.timestamp() * 1000]
    for k in old_keys:
        del seen_payloads[k]
    
    # Check if timestamp is too old
    payload_time = datetime.fromtimestamp(timestamp / 1000)
    if payload_time < cutoff:
        return False  # Too old - possible replay
    
    # Check if we've seen this exact payload
    minute_key = int(timestamp / 60000) * 60000
    if minute_key not in seen_payloads:
        seen_payloads[minute_key] = set()
    
    if payload_hash in seen_payloads[minute_key]:
        return False  # Duplicate - replay attack
    
    seen_payloads[minute_key].add(payload_hash)
    return True


# ============================================================================
# API ENDPOINTS
# ============================================================================

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'service': 'WiseDrive OBD2 Backend',
        'version': '2.0',
        'encryption': 'RSA-4096 + AES-256-GCM',
        'demo_mode': DEMO_MODE
    })


@app.route('/apiv2/webhook/obdreport/wisedrive/encrypted', methods=['POST'])
@validate_auth
def receive_encrypted_obd_report():
    """
    Receive encrypted OBD scan report from Android SDK.
    
    Request Body:
    {
        "version": 2,
        "keyId": 1,
        "timestamp": 1705312800000,
        "encryptedData": "BASE64_ENCRYPTED_PAYLOAD..."
    }
    
    Response:
    {
        "result": "SUCCESS",
        "reportId": "...",
        "received": true
    }
    """
    try:
        body = request.get_json()
        
        if not body:
            return jsonify({'error': 'Empty request body'}), 400
        
        # Validate required fields
        required_fields = ['version', 'keyId', 'timestamp', 'encryptedData']
        for field in required_fields:
            if field not in body:
                return jsonify({'error': f'Missing field: {field}'}), 400
        
        version = body['version']
        key_id = body['keyId']
        timestamp = body['timestamp']
        encrypted_data = body['encryptedData']
        
        logger.info(f"Received encrypted report - version={version}, keyId={key_id}")
        
        # Check encryption version
        if version != 2:
            return jsonify({'error': f'Unsupported encryption version: {version}'}), 400
        
        # Check for replay attack
        payload_hash = hash(encrypted_data)
        if not check_replay(str(payload_hash), timestamp):
            logger.warning(f"Possible replay attack detected! timestamp={timestamp}")
            return jsonify({'error': 'Replay detected or timestamp expired'}), 400
        
        # Decrypt the payload
        try:
            scan_data = wisedrive_decryptor.decrypt(encrypted_data)
        except DecryptionError as e:
            logger.error(f"Decryption failed: {e}")
            return jsonify({'error': 'Decryption failed', 'details': str(e)}), 400
        
        # Log decrypted data (in production, store to database)
        logger.info("=" * 50)
        logger.info("DECRYPTED OBD SCAN DATA:")
        logger.info("=" * 50)
        logger.info(f"License Plate: {scan_data.get('license_plate', 'N/A')}")
        logger.info(f"Tracking ID: {scan_data.get('tracking_id', 'N/A')}")
        logger.info(f"VIN: {scan_data.get('vin', 'N/A')}")
        logger.info(f"Car Company: {scan_data.get('car_company', 'N/A')}")
        logger.info(f"MIL Status: {scan_data.get('mil_status', False)}")
        logger.info(f"Faulty Modules: {scan_data.get('faulty_modules', [])}")
        logger.info(f"DTCs Found: {len(scan_data.get('code_details', []))}")
        logger.info(f"Battery Voltage: {scan_data.get('battery_voltage', 'N/A')}V")
        logger.info("=" * 50)
        
        # Process the data (store to database, trigger alerts, etc.)
        report_id = process_scan_data(scan_data)
        
        return jsonify({
            'result': 'SUCCESS',
            'reportId': report_id,
            'received': True,
            'decrypted': True,
            'dtcCount': len(scan_data.get('code_details', []))
        })
        
    except Exception as e:
        logger.exception(f"Error processing request: {e}")
        return jsonify({'error': 'Internal server error'}), 500


@app.route('/apiv2/webhook/obdreport/wisedrive', methods=['POST'])
@validate_auth
def receive_legacy_obd_report():
    """
    Legacy endpoint for unencrypted data.
    In production, this should be deprecated.
    """
    logger.warning("Legacy unencrypted endpoint called!")
    
    try:
        body = request.get_json()
        
        if not body:
            return jsonify({'error': 'Empty request body'}), 400
        
        # Log warning about unencrypted data
        logger.warning("SECURITY WARNING: Received unencrypted OBD data!")
        logger.warning(f"License Plate: {body.get('license_plate', 'N/A')}")
        
        # Process anyway (for backward compatibility)
        report_id = process_scan_data(body)
        
        return jsonify({
            'result': 'SUCCESS',
            'reportId': report_id,
            'received': True,
            'encrypted': False,
            'warning': 'Please upgrade to encrypted SDK'
        })
        
    except Exception as e:
        logger.exception(f"Error processing legacy request: {e}")
        return jsonify({'error': 'Internal server error'}), 500


@app.route('/api/keys/public', methods=['GET'])
def get_public_keys():
    """
    Endpoint for SDK to fetch public keys.
    In production, this should be behind authentication.
    """
    if not DEMO_MODE:
        return jsonify({'error': 'Not available in production mode'}), 403
    
    return jsonify({
        'clientPublicKey': CLIENT_PUBLIC,
        'wiseDrivePublicKey': WISEDRIVE_PUBLIC,
        'keyId': 1,
        'algorithm': 'RSA-4096',
        'expiresAt': (datetime.now() + timedelta(days=365)).isoformat()
    })


@app.route('/api/decrypt/test', methods=['POST'])
def test_decrypt():
    """
    Test endpoint to verify decryption works.
    Only available in demo mode.
    """
    if not DEMO_MODE:
        return jsonify({'error': 'Not available in production mode'}), 403
    
    try:
        body = request.get_json()
        encrypted_data = body.get('encryptedData', '')
        key_type = body.get('keyType', 'wisedrive')  # 'wisedrive' or 'client'
        
        decryptor = wisedrive_decryptor if key_type == 'wisedrive' else client_decryptor
        
        if not decryptor:
            return jsonify({'error': f'No decryptor for key type: {key_type}'}), 400
        
        # Get header info first
        header = decryptor.get_header_info(encrypted_data)
        
        # Decrypt
        decrypted = decryptor.decrypt(encrypted_data)
        
        return jsonify({
            'success': True,
            'header': {
                'magic': header.magic,
                'version': header.version,
                'keyId': header.key_id,
                'timestamp': header.timestamp,
                'payloadType': header.payload_type.value
            },
            'decryptedData': decrypted
        })
        
    except DecryptionError as e:
        return jsonify({
            'success': False,
            'error': str(e),
            'errorType': 'DecryptionError'
        }), 400
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e),
            'errorType': type(e).__name__
        }), 500


# ============================================================================
# DATA PROCESSING
# ============================================================================

def process_scan_data(scan_data: dict) -> str:
    """
    Process decrypted scan data.
    In production, this would:
    - Store to database
    - Trigger alerts for critical DTCs
    - Update vehicle history
    - Generate reports
    """
    import uuid
    
    report_id = str(uuid.uuid4())
    
    # Example processing
    license_plate = scan_data.get('license_plate', 'UNKNOWN')
    tracking_id = scan_data.get('tracking_id', 'UNKNOWN')
    dtc_count = len(scan_data.get('code_details', []))
    faulty_modules = scan_data.get('faulty_modules', [])
    
    # Log summary
    logger.info(f"Processed report {report_id}:")
    logger.info(f"  - License: {license_plate}")
    logger.info(f"  - Tracking: {tracking_id}")
    logger.info(f"  - DTCs: {dtc_count}")
    logger.info(f"  - Faulty: {faulty_modules}")
    
    # TODO: Store to database
    # db.scan_reports.insert_one({
    #     'report_id': report_id,
    #     'license_plate': license_plate,
    #     'tracking_id': tracking_id,
    #     'scan_data': scan_data,
    #     'received_at': datetime.utcnow(),
    #     'processed': True
    # })
    
    return report_id


# ============================================================================
# MAIN
# ============================================================================

if __name__ == '__main__':
    print("\n" + "=" * 60)
    print("WISEDRIVE OBD2 BACKEND API SERVER")
    print("=" * 60)
    print(f"\nMode: {'DEMO' if DEMO_MODE else 'PRODUCTION'}")
    print(f"Encryption: RSA-4096 + AES-256-GCM + HMAC-SHA512")
    print("\nEndpoints:")
    print("  GET  /health                                   - Health check")
    print("  POST /apiv2/webhook/obdreport/wisedrive/encrypted - Encrypted reports")
    print("  POST /apiv2/webhook/obdreport/wisedrive        - Legacy (deprecated)")
    print("  GET  /api/keys/public                          - Get public keys (demo)")
    print("  POST /api/decrypt/test                         - Test decryption (demo)")
    print("\n" + "=" * 60)
    
    if DEMO_MODE:
        print("\nDEMO PUBLIC KEYS (embed in SDK):")
        print("-" * 40)
        print("\nCLIENT PUBLIC KEY:")
        print(CLIENT_PUBLIC[:300] + "...")
        print("\nWISDRIVE PUBLIC KEY:")
        print(WISEDRIVE_PUBLIC[:300] + "...")
    
    print("\n" + "=" * 60)
    print("Starting server on http://0.0.0.0:8082")
    print("=" * 60 + "\n")
    
    app.run(host='0.0.0.0', port=8082, debug=DEMO_MODE)
