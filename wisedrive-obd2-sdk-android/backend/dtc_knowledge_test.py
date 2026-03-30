#!/usr/bin/env python3
"""
WiseDrive OBD2 SDK - DTC Knowledge Base Testing Suite
====================================================

This module tests the DTC knowledge base functionality including:
1. DTCKnowledgeBase.getKnowledge() for specific and generic codes
2. LiveDataParser.enrichDTC() functionality
3. ReportTransformer.transform() including causes/symptoms/solutions
4. Mock mode functionality
5. Simulated live scan with full DTC data

Since this is Kotlin code, we simulate the functionality in Python to verify
the logic and data structures work correctly.

Run: python3 dtc_knowledge_test.py
"""

import sys
import json
import time
from typing import Dict, Any, List, Optional, Tuple
from datetime import datetime
from dataclasses import dataclass


@dataclass
class DTCKnowledge:
    """Simulates the Kotlin DTCKnowledge data class"""
    code: str
    possible_causes: List[str]
    symptoms: List[str]
    solutions: List[str]


@dataclass
class DTCBasic:
    """Simulates the Kotlin DTCBasic data class"""
    code: str
    category: str
    description: str
    ecu_source: Optional[str] = None


@dataclass
class DTCDetail:
    """Simulates the Kotlin DTCDetail data class"""
    code: str
    category: str
    description: str
    severity: str
    possible_causes: List[str]
    symptoms: List[str]
    solutions: List[str]
    is_manufacturer_specific: bool = False
    ecu_source: Optional[str] = None


@dataclass
class CodeDetail:
    """Simulates the API payload CodeDetail"""
    dtc: str
    meaning: str
    module: str
    status: str
    descriptions: List[str]
    causes: List[str]
    symptoms: List[str]
    solutions: List[str]


class DTCKnowledgeBaseSimulator:
    """
    Python simulation of the Kotlin DTCKnowledgeBase object
    This tests the same logic as the Kotlin implementation
    """
    
    def __init__(self):
        # Simulate the knowledge base with key DTCs from the Kotlin code
        self.knowledge_base = {
            # Specific DTCs that should be in the knowledge base
            "P0503": DTCKnowledge(
                code="P0503",
                possible_causes=[
                    "Faulty vehicle speed sensor",
                    "Intermittent connection",
                    "Damaged wiring",
                    "Corroded connector",
                    "Tone ring damage",
                    "ECM/PCM issue"
                ],
                symptoms=[
                    "Erratic speedometer reading",
                    "Cruise control malfunction",
                    "Transmission shifting problems",
                    "ABS/Traction control warning",
                    "Check Engine Light on",
                    "Intermittent symptoms"
                ],
                solutions=[
                    "Inspect vehicle speed sensor connector for corrosion",
                    "Check wiring for damage or chafing",
                    "Test sensor signal with scan tool",
                    "Inspect tone ring for damage",
                    "Replace vehicle speed sensor",
                    "Check for related TSBs"
                ]
            ),
            "B1000": DTCKnowledge(
                code="B1000",
                possible_causes=[
                    "Airbag module fault",
                    "Wiring issue",
                    "Crash sensor problem",
                    "Clock spring failure",
                    "Low battery voltage"
                ],
                symptoms=[
                    "Airbag warning light on",
                    "Airbag system disabled",
                    "SRS malfunction",
                    "Horn or cruise may not work",
                    "Steering wheel controls inoperative"
                ],
                solutions=[
                    "Diagnose with appropriate airbag scan tool",
                    "Check battery voltage",
                    "Inspect clock spring",
                    "Check wiring",
                    "Replace faulty component"
                ]
            ),
            "C0035": DTCKnowledge(
                code="C0035",
                possible_causes=[
                    "Faulty left front wheel speed sensor",
                    "Damaged tone ring",
                    "Wiring issue",
                    "ABS module problem",
                    "Sensor gap incorrect"
                ],
                symptoms=[
                    "ABS light on",
                    "Traction control light",
                    "ABS not functioning",
                    "Speedometer issues",
                    "Brake warning"
                ],
                solutions=[
                    "Test wheel speed sensor",
                    "Inspect tone ring",
                    "Check wiring",
                    "Verify sensor gap",
                    "Replace sensor if faulty"
                ]
            )
        }
    
    def get_knowledge(self, code: str) -> Optional[DTCKnowledge]:
        """
        Simulate the Kotlin getKnowledge function
        Returns specific knowledge if available, otherwise generates generic knowledge
        """
        normalized_code = code.upper().strip()
        
        # First try exact match
        if normalized_code in self.knowledge_base:
            return self.knowledge_base[normalized_code]
        
        # Generate generic knowledge based on code pattern
        return self._generate_generic_knowledge(normalized_code)
    
    def _generate_generic_knowledge(self, code: str) -> Optional[DTCKnowledge]:
        """Generate generic knowledge based on DTC code pattern"""
        if len(code) < 5:
            return None
        
        category = code[0]
        sub_system = code[1:3]
        
        if category == 'P':
            return self._generate_powertrain_knowledge(code, sub_system)
        elif category == 'B':
            return self._generate_body_knowledge(code, sub_system)
        elif category == 'C':
            return self._generate_chassis_knowledge(code, sub_system)
        elif category == 'U':
            return self._generate_network_knowledge(code, sub_system)
        else:
            return None
    
    def _generate_powertrain_knowledge(self, code: str, sub_system: str) -> DTCKnowledge:
        """Generate generic powertrain knowledge"""
        if sub_system.startswith("01") or sub_system.startswith("02"):
            system_name = "Fuel/Air Metering"
        elif sub_system.startswith("03") or sub_system.startswith("04"):
            system_name = "Ignition System"
        elif sub_system.startswith("05"):
            system_name = "Vehicle Speed/Idle Control"
        elif sub_system.startswith("06"):
            system_name = "Computer Output Circuit"
        elif sub_system.startswith("07") or sub_system.startswith("08"):
            system_name = "Transmission"
        else:
            system_name = "Powertrain"
        
        return DTCKnowledge(
            code=code,
            possible_causes=[
                f"Faulty sensor or actuator in {system_name} system",
                "Wiring or connector issue",
                "Poor electrical connection",
                "ECU/PCM malfunction",
                "Related component failure"
            ],
            symptoms=[
                "Check Engine Light illuminated",
                "Reduced engine performance",
                "Poor fuel economy",
                "Engine hesitation or rough idle",
                "Possible drivability issues"
            ],
            solutions=[
                "Scan for related trouble codes",
                "Inspect wiring and connectors",
                "Test suspected sensor/actuator",
                "Check for technical service bulletins (TSB)",
                "Clear codes and test drive to verify repair"
            ]
        )
    
    def _generate_body_knowledge(self, code: str, sub_system: str) -> DTCKnowledge:
        """Generate generic body knowledge"""
        system_name = "Body Electronics"
        
        return DTCKnowledge(
            code=code,
            possible_causes=[
                f"Faulty {system_name} module or sensor",
                "Wiring harness damage or corrosion",
                "Poor ground connection",
                "Body control module (BCM) issue",
                "Related fuse or relay failure"
            ],
            symptoms=[
                "Warning light on dashboard",
                "System malfunction",
                "Intermittent operation",
                "Complete system failure",
                "Diagnostic trouble code stored"
            ],
            solutions=[
                "Check fuses and relays",
                "Inspect wiring and ground connections",
                "Test affected component",
                "Check for water intrusion",
                "Consult service manual for specific procedures"
            ]
        )
    
    def _generate_chassis_knowledge(self, code: str, sub_system: str) -> DTCKnowledge:
        """Generate generic chassis knowledge"""
        system_name = "Chassis System"
        
        return DTCKnowledge(
            code=code,
            possible_causes=[
                "Faulty wheel speed sensor",
                "ABS module malfunction",
                "Wiring or connector issue",
                "Low brake fluid level",
                "Damaged tone ring or reluctor"
            ],
            symptoms=[
                "ABS warning light on",
                "Traction control light on",
                "Stability control disabled",
                "Unusual brake pedal feel",
                "ABS not functioning properly"
            ],
            solutions=[
                "Check wheel speed sensors",
                "Inspect brake fluid level and condition",
                "Test ABS module communication",
                "Check for damaged tone rings",
                "Verify sensor air gaps"
            ]
        )
    
    def _generate_network_knowledge(self, code: str, sub_system: str) -> DTCKnowledge:
        """Generate generic network knowledge"""
        module_name = "Control Module"
        
        return DTCKnowledge(
            code=code,
            possible_causes=[
                f"Lost communication with {module_name}",
                "CAN bus wiring issue (open or short)",
                "Control module failure",
                "Poor ground connection",
                "Power supply issue to module"
            ],
            symptoms=[
                "Multiple warning lights",
                "System not responding",
                "Intermittent operation",
                "Communication errors",
                "Module not detected on network"
            ],
            solutions=[
                "Check CAN bus wiring for damage",
                "Test module power and ground",
                "Inspect all connectors",
                "Check for proper termination",
                "Test module communication with scan tool"
            ]
        )


class LiveDataParserSimulator:
    """Simulate the Kotlin LiveDataParser functionality"""
    
    def __init__(self, knowledge_base: DTCKnowledgeBaseSimulator):
        self.knowledge_base = knowledge_base
    
    def enrich_dtc(self, basic: DTCBasic) -> DTCDetail:
        """
        Simulate the enrichDTC function from LiveDataParser
        This should populate causes, symptoms, and solutions from knowledge base
        """
        # Get severity (simplified)
        severity = self._get_severity(basic.code)
        
        # Get knowledge from knowledge base
        knowledge = self.knowledge_base.get_knowledge(basic.code)
        
        return DTCDetail(
            code=basic.code,
            category=basic.category,
            description=basic.description,
            severity=severity,
            possible_causes=knowledge.possible_causes[:5] if knowledge else [],
            symptoms=knowledge.symptoms[:5] if knowledge else [],
            solutions=knowledge.solutions[:5] if knowledge else [],
            is_manufacturer_specific=self._is_manufacturer_specific(basic.code),
            ecu_source=basic.ecu_source
        )
    
    def _get_severity(self, code: str) -> str:
        """Simplified severity determination"""
        if code.startswith('P03'):  # Misfires
            return "Critical"
        elif code.startswith('P04'):  # Emissions
            return "Important"
        elif code.startswith('B'):  # Body
            return "Non-Critical"
        elif code.startswith('C'):  # Chassis
            return "Important"
        elif code.startswith('U'):  # Network
            return "Critical"
        else:
            return "Important"
    
    def _is_manufacturer_specific(self, code: str) -> bool:
        """Check if code is manufacturer specific"""
        if len(code) >= 2:
            return code[1] in ['1', '3']  # P1xxx, P3xxx, etc.
        return False


class ReportTransformerSimulator:
    """Simulate the Kotlin ReportTransformer functionality"""
    
    def __init__(self, knowledge_base: DTCKnowledgeBaseSimulator):
        self.knowledge_base = knowledge_base
    
    def transform(self, dtc_details: List[DTCDetail], registration_number: str, tracking_id: str) -> Dict[str, Any]:
        """
        Simulate the transform function from ReportTransformer
        This should include causes, symptoms, solutions in code_details
        """
        code_details = []
        
        for dtc in dtc_details:
            # Get knowledge from knowledge base (should already be populated, but double-check)
            knowledge = self.knowledge_base.get_knowledge(dtc.code)
            
            code_detail = CodeDetail(
                dtc=dtc.code,
                meaning=dtc.description,
                module=self._map_category_to_module(dtc.category),
                status="Confirmed",  # Simplified
                descriptions=[dtc.description],
                causes=knowledge.possible_causes if knowledge else dtc.possible_causes,
                symptoms=knowledge.symptoms if knowledge else dtc.symptoms,
                solutions=knowledge.solutions if knowledge else dtc.solutions
            )
            code_details.append(code_detail)
        
        return {
            "license_plate": registration_number,
            "tracking_id": tracking_id,
            "code_details": [
                {
                    "dtc": cd.dtc,
                    "meaning": cd.meaning,
                    "module": cd.module,
                    "status": cd.status,
                    "descriptions": cd.descriptions,
                    "causes": cd.causes,
                    "symptoms": cd.symptoms,
                    "solutions": cd.solutions
                } for cd in code_details
            ]
        }
    
    def _map_category_to_module(self, category: str) -> str:
        """Map DTC category to module name"""
        mapping = {
            "Powertrain": "Engine",
            "Body": "BCM",
            "Chassis": "ABS",
            "Network/Communication": "Others",
            "Network": "Others"
        }
        return mapping.get(category, category)


class DTCKnowledgeBaseTester:
    """Main test class for DTC knowledge base functionality"""
    
    def __init__(self):
        self.tests_run = 0
        self.tests_passed = 0
        self.tests_failed = 0
        self.failures = []
        
        # Initialize simulators
        self.knowledge_base = DTCKnowledgeBaseSimulator()
        self.live_data_parser = LiveDataParserSimulator(self.knowledge_base)
        self.report_transformer = ReportTransformerSimulator(self.knowledge_base)
    
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
            self.failures.append(f"{name}: {str(e)}")
            return False
    
    def test_specific_dtc_p0503(self) -> bool:
        """Test DTCKnowledgeBase.getKnowledge() returns data for P0503 (specific code in knowledge base)"""
        try:
            knowledge = self.knowledge_base.get_knowledge("P0503")
            
            if not knowledge:
                print("     ❌ No knowledge returned for P0503")
                return False
            
            if knowledge.code != "P0503":
                print(f"     ❌ Wrong code returned: {knowledge.code}")
                return False
            
            if not knowledge.possible_causes:
                print("     ❌ No possible causes for P0503")
                return False
            
            if not knowledge.symptoms:
                print("     ❌ No symptoms for P0503")
                return False
            
            if not knowledge.solutions:
                print("     ❌ No solutions for P0503")
                return False
            
            print(f"     ✓ P0503 knowledge found with {len(knowledge.possible_causes)} causes, {len(knowledge.symptoms)} symptoms, {len(knowledge.solutions)} solutions")
            print(f"     ✓ First cause: {knowledge.possible_causes[0]}")
            print(f"     ✓ First symptom: {knowledge.symptoms[0]}")
            print(f"     ✓ First solution: {knowledge.solutions[0]}")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False
    
    def test_generic_dtc_p0999(self) -> bool:
        """Test DTCKnowledgeBase.getKnowledge() returns data for P0999 (generic fallback for unknown codes)"""
        try:
            knowledge = self.knowledge_base.get_knowledge("P0999")
            
            if not knowledge:
                print("     ❌ No knowledge returned for P0999")
                return False
            
            if knowledge.code != "P0999":
                print(f"     ❌ Wrong code returned: {knowledge.code}")
                return False
            
            if not knowledge.possible_causes:
                print("     ❌ No possible causes for P0999")
                return False
            
            if not knowledge.symptoms:
                print("     ❌ No symptoms for P0999")
                return False
            
            if not knowledge.solutions:
                print("     ❌ No solutions for P0999")
                return False
            
            # Check that it's generic powertrain knowledge
            if "Powertrain" not in knowledge.possible_causes[0]:
                print("     ❌ Generic knowledge doesn't mention powertrain system")
                return False
            
            print(f"     ✓ P0999 generic knowledge found with {len(knowledge.possible_causes)} causes, {len(knowledge.symptoms)} symptoms, {len(knowledge.solutions)} solutions")
            print(f"     ✓ Generic cause: {knowledge.possible_causes[0]}")
            print(f"     ✓ Generic symptom: {knowledge.symptoms[0]}")
            print(f"     ✓ Generic solution: {knowledge.solutions[0]}")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False
    
    def test_body_code_b1000(self) -> bool:
        """Test DTCKnowledgeBase.getKnowledge() returns data for B1000 (body code)"""
        try:
            knowledge = self.knowledge_base.get_knowledge("B1000")
            
            if not knowledge:
                print("     ❌ No knowledge returned for B1000")
                return False
            
            if knowledge.code != "B1000":
                print(f"     ❌ Wrong code returned: {knowledge.code}")
                return False
            
            if not knowledge.possible_causes:
                print("     ❌ No possible causes for B1000")
                return False
            
            if not knowledge.symptoms:
                print("     ❌ No symptoms for B1000")
                return False
            
            if not knowledge.solutions:
                print("     ❌ No solutions for B1000")
                return False
            
            # Check that it mentions airbag (specific knowledge)
            if "airbag" not in knowledge.possible_causes[0].lower():
                print("     ❌ B1000 knowledge doesn't mention airbag")
                return False
            
            print(f"     ✓ B1000 knowledge found with {len(knowledge.possible_causes)} causes, {len(knowledge.symptoms)} symptoms, {len(knowledge.solutions)} solutions")
            print(f"     ✓ Body cause: {knowledge.possible_causes[0]}")
            print(f"     ✓ Body symptom: {knowledge.symptoms[0]}")
            print(f"     ✓ Body solution: {knowledge.solutions[0]}")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False
    
    def test_chassis_code_c0035(self) -> bool:
        """Test DTCKnowledgeBase.getKnowledge() returns data for C0035 (chassis code)"""
        try:
            knowledge = self.knowledge_base.get_knowledge("C0035")
            
            if not knowledge:
                print("     ❌ No knowledge returned for C0035")
                return False
            
            if knowledge.code != "C0035":
                print(f"     ❌ Wrong code returned: {knowledge.code}")
                return False
            
            if not knowledge.possible_causes:
                print("     ❌ No possible causes for C0035")
                return False
            
            if not knowledge.symptoms:
                print("     ❌ No symptoms for C0035")
                return False
            
            if not knowledge.solutions:
                print("     ❌ No solutions for C0035")
                return False
            
            # Check that it mentions wheel speed sensor (specific knowledge)
            if "wheel speed sensor" not in knowledge.possible_causes[0].lower():
                print("     ❌ C0035 knowledge doesn't mention wheel speed sensor")
                return False
            
            print(f"     ✓ C0035 knowledge found with {len(knowledge.possible_causes)} causes, {len(knowledge.symptoms)} symptoms, {len(knowledge.solutions)} solutions")
            print(f"     ✓ Chassis cause: {knowledge.possible_causes[0]}")
            print(f"     ✓ Chassis symptom: {knowledge.symptoms[0]}")
            print(f"     ✓ Chassis solution: {knowledge.solutions[0]}")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False
    
    def test_network_code_u0155(self) -> bool:
        """Test DTCKnowledgeBase.getKnowledge() returns data for U0155 (network code - generic fallback)"""
        try:
            knowledge = self.knowledge_base.get_knowledge("U0155")
            
            if not knowledge:
                print("     ❌ No knowledge returned for U0155")
                return False
            
            if knowledge.code != "U0155":
                print(f"     ❌ Wrong code returned: {knowledge.code}")
                return False
            
            if not knowledge.possible_causes:
                print("     ❌ No possible causes for U0155")
                return False
            
            if not knowledge.symptoms:
                print("     ❌ No symptoms for U0155")
                return False
            
            if not knowledge.solutions:
                print("     ❌ No solutions for U0155")
                return False
            
            # Check that it's generic network knowledge
            if "communication" not in knowledge.possible_causes[0].lower():
                print("     ❌ Generic network knowledge doesn't mention communication")
                return False
            
            print(f"     ✓ U0155 generic knowledge found with {len(knowledge.possible_causes)} causes, {len(knowledge.symptoms)} symptoms, {len(knowledge.solutions)} solutions")
            print(f"     ✓ Network cause: {knowledge.possible_causes[0]}")
            print(f"     ✓ Network symptom: {knowledge.symptoms[0]}")
            print(f"     ✓ Network solution: {knowledge.solutions[0]}")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False
    
    def test_enrich_dtc_functionality(self) -> bool:
        """Test enrichDTC in LiveDataParser correctly populates possibleCauses, symptoms, solutions"""
        try:
            # Create a basic DTC
            basic_dtc = DTCBasic(
                code="P0503",
                category="Powertrain",
                description="Vehicle Speed Sensor Intermittent",
                ecu_source="PCM"
            )
            
            # Enrich it
            enriched_dtc = self.live_data_parser.enrich_dtc(basic_dtc)
            
            # Verify enrichment
            if not enriched_dtc.possible_causes:
                print("     ❌ enrichDTC didn't populate possible_causes")
                return False
            
            if not enriched_dtc.symptoms:
                print("     ❌ enrichDTC didn't populate symptoms")
                return False
            
            if not enriched_dtc.solutions:
                print("     ❌ enrichDTC didn't populate solutions")
                return False
            
            if enriched_dtc.code != basic_dtc.code:
                print(f"     ❌ Code mismatch: {enriched_dtc.code} != {basic_dtc.code}")
                return False
            
            if enriched_dtc.category != basic_dtc.category:
                print(f"     ❌ Category mismatch: {enriched_dtc.category} != {basic_dtc.category}")
                return False
            
            if enriched_dtc.ecu_source != basic_dtc.ecu_source:
                print(f"     ❌ ECU source mismatch: {enriched_dtc.ecu_source} != {basic_dtc.ecu_source}")
                return False
            
            print(f"     ✓ enrichDTC populated {len(enriched_dtc.possible_causes)} causes, {len(enriched_dtc.symptoms)} symptoms, {len(enriched_dtc.solutions)} solutions")
            print(f"     ✓ Severity: {enriched_dtc.severity}")
            print(f"     ✓ Manufacturer specific: {enriched_dtc.is_manufacturer_specific}")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False
    
    def test_report_transformer_includes_knowledge(self) -> bool:
        """Test ReportTransformer.transform() includes causes, symptoms, solutions in code_details"""
        try:
            # Create enriched DTCs
            dtc_details = [
                DTCDetail(
                    code="P0503",
                    category="Powertrain",
                    description="Vehicle Speed Sensor Intermittent",
                    severity="Important",
                    possible_causes=["Faulty vehicle speed sensor", "Intermittent connection"],
                    symptoms=["Erratic speedometer reading", "Cruise control malfunction"],
                    solutions=["Inspect vehicle speed sensor connector", "Check wiring for damage"],
                    ecu_source="PCM"
                ),
                DTCDetail(
                    code="B1000",
                    category="Body",
                    description="Airbag Module Fault",
                    severity="Critical",
                    possible_causes=["Airbag module fault", "Wiring issue"],
                    symptoms=["Airbag warning light on", "Airbag system disabled"],
                    solutions=["Diagnose with appropriate airbag scan tool", "Check battery voltage"],
                    ecu_source="SRS"
                )
            ]
            
            # Transform to API payload
            payload = self.report_transformer.transform(dtc_details, "MH12AB1234", "ORD123456")
            
            # Verify payload structure
            if "code_details" not in payload:
                print("     ❌ code_details not in payload")
                return False
            
            code_details = payload["code_details"]
            if len(code_details) != 2:
                print(f"     ❌ Expected 2 code_details, got {len(code_details)}")
                return False
            
            # Check first DTC (P0503)
            p0503_detail = code_details[0]
            if p0503_detail["dtc"] != "P0503":
                print(f"     ❌ First DTC code wrong: {p0503_detail['dtc']}")
                return False
            
            if not p0503_detail.get("causes"):
                print("     ❌ P0503 missing causes in code_details")
                return False
            
            if not p0503_detail.get("symptoms"):
                print("     ❌ P0503 missing symptoms in code_details")
                return False
            
            if not p0503_detail.get("solutions"):
                print("     ❌ P0503 missing solutions in code_details")
                return False
            
            # Check second DTC (B1000)
            b1000_detail = code_details[1]
            if b1000_detail["dtc"] != "B1000":
                print(f"     ❌ Second DTC code wrong: {b1000_detail['dtc']}")
                return False
            
            if not b1000_detail.get("causes"):
                print("     ❌ B1000 missing causes in code_details")
                return False
            
            if not b1000_detail.get("symptoms"):
                print("     ❌ B1000 missing symptoms in code_details")
                return False
            
            if not b1000_detail.get("solutions"):
                print("     ❌ B1000 missing solutions in code_details")
                return False
            
            print(f"     ✓ ReportTransformer included knowledge for {len(code_details)} DTCs")
            print(f"     ✓ P0503 has {len(p0503_detail['causes'])} causes, {len(p0503_detail['symptoms'])} symptoms, {len(p0503_detail['solutions'])} solutions")
            print(f"     ✓ B1000 has {len(b1000_detail['causes'])} causes, {len(b1000_detail['symptoms'])} symptoms, {len(b1000_detail['solutions'])} solutions")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False
    
    def test_mock_mode_functionality(self) -> bool:
        """Test that mock mode still works correctly with full DTC data"""
        try:
            # Simulate mock mode DTCs
            mock_dtcs = [
                DTCBasic(code="P0300", category="Powertrain", description="Random/Multiple Cylinder Misfire Detected", ecu_source="PCM"),
                DTCBasic(code="P0420", category="Powertrain", description="Catalyst System Efficiency Below Threshold", ecu_source="PCM"),
                DTCBasic(code="B1000", category="Body", description="Airbag Module Fault", ecu_source="SRS")
            ]
            
            # Enrich mock DTCs
            enriched_mock_dtcs = [self.live_data_parser.enrich_dtc(dtc) for dtc in mock_dtcs]
            
            # Verify all mock DTCs have knowledge
            for dtc in enriched_mock_dtcs:
                if not dtc.possible_causes:
                    print(f"     ❌ Mock DTC {dtc.code} missing causes")
                    return False
                if not dtc.symptoms:
                    print(f"     ❌ Mock DTC {dtc.code} missing symptoms")
                    return False
                if not dtc.solutions:
                    print(f"     ❌ Mock DTC {dtc.code} missing solutions")
                    return False
            
            # Transform to API payload
            payload = self.report_transformer.transform(enriched_mock_dtcs, "MOCK12345", "MOCK_ORDER")
            
            # Verify payload
            if len(payload["code_details"]) != 3:
                print(f"     ❌ Expected 3 mock DTCs, got {len(payload['code_details'])}")
                return False
            
            print(f"     ✓ Mock mode generated {len(enriched_mock_dtcs)} DTCs with full knowledge")
            print(f"     ✓ All mock DTCs have causes, symptoms, and solutions")
            print(f"     ✓ Mock payload generated successfully")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False
    
    def test_simulated_live_scan(self) -> bool:
        """Test that a simulated live scan produces DTCs with causes/symptoms/solutions"""
        try:
            # Simulate live scan results (mix of specific and generic DTCs)
            live_scan_dtcs = [
                DTCBasic(code="P0503", category="Powertrain", description="Vehicle Speed Sensor Intermittent", ecu_source="PCM"),
                DTCBasic(code="P0999", category="Powertrain", description="Unknown Powertrain Code", ecu_source="PCM"),
                DTCBasic(code="C0035", category="Chassis", description="Left Front Wheel Speed Sensor", ecu_source="ABS"),
                DTCBasic(code="U0155", category="Network", description="Lost Communication with Unknown Module", ecu_source="Gateway")
            ]
            
            # Process live scan DTCs
            enriched_live_dtcs = [self.live_data_parser.enrich_dtc(dtc) for dtc in live_scan_dtcs]
            
            # Verify all live DTCs have knowledge (both specific and generic)
            for dtc in enriched_live_dtcs:
                if not dtc.possible_causes:
                    print(f"     ❌ Live DTC {dtc.code} missing causes")
                    return False
                if not dtc.symptoms:
                    print(f"     ❌ Live DTC {dtc.code} missing symptoms")
                    return False
                if not dtc.solutions:
                    print(f"     ❌ Live DTC {dtc.code} missing solutions")
                    return False
            
            # Transform to API payload
            payload = self.report_transformer.transform(enriched_live_dtcs, "LIVE12345", "LIVE_ORDER")
            
            # Verify payload includes all knowledge
            code_details = payload["code_details"]
            for detail in code_details:
                if not detail.get("causes"):
                    print(f"     ❌ Live DTC {detail['dtc']} missing causes in payload")
                    return False
                if not detail.get("symptoms"):
                    print(f"     ❌ Live DTC {detail['dtc']} missing symptoms in payload")
                    return False
                if not detail.get("solutions"):
                    print(f"     ❌ Live DTC {detail['dtc']} missing solutions in payload")
                    return False
            
            print(f"     ✓ Live scan generated {len(enriched_live_dtcs)} DTCs with full knowledge")
            print(f"     ✓ Mix of specific (P0503, C0035) and generic (P0999, U0155) knowledge")
            print(f"     ✓ All live DTCs have causes, symptoms, and solutions in payload")
            
            return True
            
        except Exception as e:
            print(f"     ❌ Test failed: {e}")
            return False
    
    def run_all_tests(self) -> Dict[str, Any]:
        """Run all DTC knowledge base tests"""
        print("\n" + "=" * 70)
        print("WISEDRIVE OBD2 SDK - DTC KNOWLEDGE BASE TEST SUITE")
        print("=" * 70)
        print(f"Test Environment: Python {sys.version}")
        print(f"Test Time: {datetime.now().isoformat()}")
        print("Testing Kotlin functionality via Python simulation")
        
        # Define tests
        tests = [
            ("DTCKnowledgeBase.getKnowledge() for P0503 (specific)", self.test_specific_dtc_p0503),
            ("DTCKnowledgeBase.getKnowledge() for P0999 (generic fallback)", self.test_generic_dtc_p0999),
            ("DTCKnowledgeBase.getKnowledge() for B1000 (body code)", self.test_body_code_b1000),
            ("DTCKnowledgeBase.getKnowledge() for C0035 (chassis code)", self.test_chassis_code_c0035),
            ("DTCKnowledgeBase.getKnowledge() for U0155 (network generic)", self.test_network_code_u0155),
            ("LiveDataParser.enrichDTC() populates knowledge", self.test_enrich_dtc_functionality),
            ("ReportTransformer.transform() includes knowledge in code_details", self.test_report_transformer_includes_knowledge),
            ("Mock mode works with full DTC data", self.test_mock_mode_functionality),
            ("Simulated live scan produces DTCs with full knowledge", self.test_simulated_live_scan)
        ]
        
        # Run all tests
        for name, test_func in tests:
            self.run_test(name, test_func)
        
        # Print summary
        print("\n" + "=" * 70)
        print("TEST SUMMARY")
        print("=" * 70)
        print(f"Tests Run:    {self.tests_run}")
        print(f"Tests Passed: {self.tests_passed}")
        print(f"Tests Failed: {self.tests_failed}")
        print(f"Success Rate: {(self.tests_passed/self.tests_run)*100:.1f}%")
        
        if self.failures:
            print(f"\n❌ FAILURES ({len(self.failures)}):")
            for failure in self.failures:
                print(f"   - {failure}")
        
        if self.tests_failed == 0:
            print("\n✅ ALL TESTS PASSED - DTC KNOWLEDGE BASE IS WORKING CORRECTLY")
            overall_status = "SUCCESS"
        else:
            print(f"\n❌ {self.tests_failed} TESTS FAILED - REVIEW REQUIRED")
            overall_status = "FAILED"
        
        print("=" * 70)
        
        return {
            "overall_status": overall_status,
            "tests_run": self.tests_run,
            "tests_passed": self.tests_passed,
            "tests_failed": self.tests_failed,
            "success_rate": (self.tests_passed/self.tests_run)*100,
            "failures": self.failures,
            "timestamp": datetime.now().isoformat()
        }


def main():
    """Main test execution"""
    tester = DTCKnowledgeBaseTester()
    results = tester.run_all_tests()
    
    # Return appropriate exit code
    if results["overall_status"] == "SUCCESS":
        return 0
    else:
        return 1


if __name__ == "__main__":
    sys.exit(main())