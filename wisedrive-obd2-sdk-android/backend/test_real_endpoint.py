#!/usr/bin/env python3
"""
WiseDrive OBD2 SDK - Real Endpoint Test
========================================

Tests submission to the actual WiseDrive analytics endpoint.
"""

import os
import sys
import json
import time
import requests
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    CYAN = '\033[96m'
    END = '\033[0m'
    BOLD = '\033[1m'


def test_real_endpoint():
    """Test submission to real WiseDrive endpoint"""
    
    print(f"\n{Colors.BOLD}{'='*60}")
    print(" TESTING REAL WISEDRIVE ENDPOINT")
    print(f"{'='*60}{Colors.END}\n")
    
    # Real endpoint
    endpoint = "http://164.52.213.170:82/apiv2/webhook/obdreport/wisedrive"
    
    # Test payload (matching API format)
    payload = {
        "license_plate": "MH12AB1234",
        "tracking_id": "ORD6894331",  # Valid test tracking ID
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
                "meaning": "Vehicle Speed Sensor A Circuit Intermittent",
                "module": "Engine Control Module (ECM)",
                "status": "Confirmed",
                "descriptions": ["Speed sensor signal erratic"],
                "causes": ["Faulty sensor", "Wiring issue"],
                "symptoms": ["Check engine light", "Erratic speedometer"],
                "solutions": ["Check wiring", "Replace sensor"]
            }
        ],
        "battery_voltage": 14.02
    }
    
    print(f"{Colors.CYAN}Endpoint:{Colors.END} {endpoint}")
    print(f"{Colors.CYAN}Tracking ID:{Colors.END} {payload['tracking_id']}")
    print(f"{Colors.CYAN}License Plate:{Colors.END} {payload['license_plate']}")
    
    print(f"\n{Colors.YELLOW}Sending request...{Colors.END}")
    
    try:
        response = requests.post(
            endpoint,
            json=payload,
            headers={
                "Content-Type": "application/json",
                "Authorization": "Basic cHJhc2FkOnByYXNhZEAxMjM="
            },
            timeout=30
        )
        
        print(f"\n{Colors.BOLD}Response:{Colors.END}")
        print(f"  Status Code: {response.status_code}")
        print(f"  Headers: {dict(response.headers)}")
        print(f"  Body: {response.text}")
        
        if response.status_code == 200:
            try:
                result = response.json()
                if result.get("result") == "SUCCESS":
                    print(f"\n{Colors.GREEN}✓ SUCCESS! Data submitted to WiseDrive{Colors.END}")
                else:
                    print(f"\n{Colors.YELLOW}⚠ Response received but not SUCCESS{Colors.END}")
            except:
                print(f"\n{Colors.YELLOW}⚠ Could not parse JSON response{Colors.END}")
        else:
            print(f"\n{Colors.RED}✗ Failed with status {response.status_code}{Colors.END}")
            
            # Try with invalid tracking ID to show difference
            print(f"\n{Colors.CYAN}Testing with INVALID tracking ID...{Colors.END}")
            payload["tracking_id"] = "INVALID123"
            
            response2 = requests.post(
                endpoint,
                json=payload,
                headers={
                    "Content-Type": "application/json",
                    "Authorization": "Basic cHJhc2FkOnByYXNhZEAxMjM="
                },
                timeout=30
            )
            print(f"  Status Code: {response2.status_code}")
            print(f"  Body: {response2.text}")
            print(f"\n{Colors.YELLOW}Note: Invalid tracking ID returns error (expected){Colors.END}")
            
    except requests.exceptions.Timeout:
        print(f"\n{Colors.RED}✗ Request timed out{Colors.END}")
    except requests.exceptions.ConnectionError as e:
        print(f"\n{Colors.RED}✗ Connection error: {e}{Colors.END}")
    except Exception as e:
        print(f"\n{Colors.RED}✗ Error: {e}{Colors.END}")
    
    print(f"\n{Colors.BOLD}{'='*60}{Colors.END}\n")


if __name__ == "__main__":
    test_real_endpoint()
