package com.wisedrive.obd2.constants

/**
 * DTC Knowledge Base - Causes, Symptoms, and Solutions for common DTCs
 */
object DTCKnowledgeBase {

    data class DTCKnowledge(
        val code: String,
        val possibleCauses: List<String>,
        val symptoms: List<String>,
        val solutions: List<String>
    )

    private val knowledgeBase: Map<String, DTCKnowledge> = mapOf(
        // Fuel/Air Metering
        "P0100" to DTCKnowledge("P0100",
            listOf("Faulty MAF sensor", "MAF sensor wiring issue", "Vacuum leak", "Dirty air filter", "Intake air leak"),
            listOf("Engine stalling", "Rough idle", "Poor fuel economy", "Hesitation during acceleration", "Check Engine Light"),
            listOf("Clean or replace MAF sensor", "Inspect wiring and connectors", "Check for intake leaks", "Replace air filter", "Clear codes and test drive")
        ),
        "P0101" to DTCKnowledge("P0101",
            listOf("Dirty or contaminated MAF sensor", "MAF sensor wiring issue or loose connector", "Air intake leak (cracked hose, loose clamp)", "Faulty MAF sensor (internal failure)", "Clogged or restricted air filter"),
            listOf("Engine hesitation or stumbling during acceleration", "Poor fuel economy", "Engine stalling or rough idle", "Black smoke from exhaust", "Check Engine Light illuminated"),
            listOf("Clean MAF sensor with dedicated MAF cleaner spray", "Inspect and repair wiring/connectors to MAF sensor", "Check for air intake leaks and repair as needed", "Replace air filter if dirty or restricted", "Replace MAF sensor if cleaning does not resolve")
        ),
        "P0102" to DTCKnowledge("P0102",
            listOf("Faulty MAF sensor", "Wiring short to ground", "Damaged MAF connector", "Vacuum leak", "Restricted air filter"),
            listOf("Engine runs rich", "Black smoke", "Poor performance", "Engine stalls", "Check Engine Light"),
            listOf("Check MAF wiring for shorts", "Clean or replace MAF sensor", "Inspect air intake system", "Replace air filter", "Check for vacuum leaks")
        ),
        "P0103" to DTCKnowledge("P0103",
            listOf("Faulty MAF sensor", "Wiring short to voltage", "Damaged harness", "Air leak after MAF", "PCM issue"),
            listOf("Engine runs lean", "Hesitation", "Poor acceleration", "Engine surging", "Check Engine Light"),
            listOf("Inspect MAF wiring", "Replace MAF sensor", "Check for air leaks", "Verify PCM operation", "Clear codes and retest")
        ),
        
        // Oxygen Sensors
        "P0130" to DTCKnowledge("P0130",
            listOf("Faulty O2 sensor", "Wiring or connector issues", "Exhaust leak", "Fuel system problem", "PCM failure"),
            listOf("Poor fuel economy", "Rough idle", "Engine hesitation", "Increased emissions", "Check Engine Light"),
            listOf("Replace O2 sensor", "Inspect wiring and connectors", "Check for exhaust leaks", "Verify fuel system", "Clear codes and test")
        ),
        "P0131" to DTCKnowledge("P0131",
            listOf("Faulty O2 sensor", "Wiring short to ground", "Exhaust leak before sensor", "Fuel pressure too low", "Vacuum leak"),
            listOf("Engine runs rich", "Black smoke from exhaust", "Poor fuel economy", "Rough idle", "Check Engine Light"),
            listOf("Replace O2 sensor", "Repair wiring issues", "Fix exhaust leaks", "Check fuel pressure", "Inspect for vacuum leaks")
        ),
        "P0132" to DTCKnowledge("P0132",
            listOf("Faulty O2 sensor", "Wiring short to power", "High fuel pressure", "Leaking injector", "Exhaust leak after sensor"),
            listOf("Engine runs lean", "Hesitation", "Poor performance", "High fuel consumption", "Check Engine Light"),
            listOf("Replace O2 sensor", "Inspect wiring for shorts", "Check fuel pressure", "Test injectors", "Check exhaust system")
        ),
        "P0133" to DTCKnowledge("P0133",
            listOf("Aging or worn O2 sensor", "Exhaust leak near O2 sensor", "Wiring or connector issue", "Fuel system issue (injector, fuel pressure)", "Engine vacuum leak"),
            listOf("Decreased fuel efficiency", "Rough idle", "Failed emissions test", "Engine hesitation", "Check Engine Light on"),
            listOf("Replace O2 sensor (Bank 1 Sensor 1)", "Check and repair exhaust leaks", "Inspect O2 sensor wiring and connectors", "Check for vacuum leaks", "Verify fuel system operation")
        ),
        "P0134" to DTCKnowledge("P0134",
            listOf("Faulty O2 sensor", "Open in O2 sensor circuit", "Damaged wiring", "Exhaust leak", "PCM failure"),
            listOf("Increased fuel consumption", "Rough idle", "Engine hesitation", "Failed emissions", "Check Engine Light"),
            listOf("Replace O2 sensor", "Repair wiring/connectors", "Fix exhaust leaks", "Check PCM", "Clear codes and retest")
        ),
        "P0135" to DTCKnowledge("P0135",
            listOf("Faulty O2 sensor heater", "Blown fuse", "Wiring issue", "Poor ground", "PCM failure"),
            listOf("Longer warm-up time", "Poor cold start", "Increased emissions", "Check Engine Light", "Reduced fuel economy"),
            listOf("Replace O2 sensor", "Check heater circuit fuse", "Repair wiring", "Check ground connection", "Clear codes")
        ),
        "P0141" to DTCKnowledge("P0141",
            listOf("Faulty downstream O2 sensor heater", "Blown fuse", "Wiring damage", "Poor connection", "PCM failure"),
            listOf("Extended warm-up time", "Poor cold performance", "Higher emissions", "Check Engine Light", "Reduced efficiency"),
            listOf("Replace downstream O2 sensor", "Check fuses", "Repair wiring", "Clean connectors", "Test PCM")
        ),

        // Fuel Trim
        "P0171" to DTCKnowledge("P0171",
            listOf("Vacuum leak", "Faulty MAF sensor", "Weak fuel pump", "Clogged fuel filter", "Faulty fuel injectors", "Intake manifold gasket leak"),
            listOf("Rough idle", "Engine hesitation", "Poor acceleration", "Engine misfires", "Check Engine Light"),
            listOf("Check for vacuum leaks", "Clean or replace MAF sensor", "Test fuel pressure", "Replace fuel filter", "Test fuel injectors", "Inspect intake gaskets")
        ),
        "P0172" to DTCKnowledge("P0172",
            listOf("Dirty air filter", "Faulty MAF sensor", "Leaking fuel injector", "High fuel pressure", "Faulty O2 sensor", "EVAP purge issue"),
            listOf("Black smoke from exhaust", "Rich fuel smell", "Spark plug fouling", "Reduced fuel economy", "Check Engine Light"),
            listOf("Replace air filter", "Clean or replace MAF sensor", "Test fuel injectors", "Check fuel pressure regulator", "Replace O2 sensor", "Check EVAP system")
        ),
        "P0174" to DTCKnowledge("P0174",
            listOf("Vacuum leak on bank 2", "Faulty MAF sensor", "Low fuel pressure", "Clogged injectors", "Intake leak"),
            listOf("Rough idle", "Hesitation", "Misfires", "Poor performance", "Check Engine Light"),
            listOf("Check for vacuum leaks", "Test MAF sensor", "Check fuel pressure", "Clean injectors", "Inspect intake system")
        ),
        "P0175" to DTCKnowledge("P0175",
            listOf("Dirty air filter", "Faulty MAF sensor", "Leaking injector", "High fuel pressure", "Faulty O2 sensor"),
            listOf("Black smoke", "Rich running", "Poor fuel economy", "Rough idle", "Check Engine Light"),
            listOf("Replace air filter", "Test MAF sensor", "Check injectors", "Test fuel pressure", "Replace O2 sensor if needed")
        ),

        // Misfires
        "P0300" to DTCKnowledge("P0300",
            listOf("Worn spark plugs", "Faulty ignition coils", "Vacuum leak", "Low fuel pressure", "Faulty injectors", "EGR valve issue", "Low compression"),
            listOf("Engine shaking or vibration", "Rough idle", "Loss of power", "Poor acceleration", "Increased fuel consumption", "Check Engine Light flashing"),
            listOf("Replace spark plugs", "Test and replace ignition coils", "Check for vacuum leaks", "Test fuel pressure", "Clean or replace injectors", "Check EGR valve", "Perform compression test")
        ),
        "P0301" to DTCKnowledge("P0301",
            listOf("Faulty spark plug (cyl 1)", "Bad ignition coil", "Faulty fuel injector", "Vacuum leak", "Low compression"),
            listOf("Rough idle", "Engine vibration", "Loss of power", "Poor fuel economy", "Flashing Check Engine Light"),
            listOf("Replace cylinder 1 spark plug", "Test/replace ignition coil", "Test fuel injector", "Check for vacuum leaks", "Compression test")
        ),
        "P0302" to DTCKnowledge("P0302",
            listOf("Faulty spark plug (cyl 2)", "Bad ignition coil", "Faulty fuel injector", "Vacuum leak", "Low compression"),
            listOf("Rough idle", "Engine vibration", "Loss of power", "Poor fuel economy", "Flashing Check Engine Light"),
            listOf("Replace cylinder 2 spark plug", "Test/replace ignition coil", "Test fuel injector", "Check for vacuum leaks", "Compression test")
        ),
        "P0303" to DTCKnowledge("P0303",
            listOf("Faulty spark plug (cyl 3)", "Bad ignition coil", "Faulty fuel injector", "Vacuum leak", "Low compression"),
            listOf("Rough idle", "Engine vibration", "Loss of power", "Poor fuel economy", "Flashing Check Engine Light"),
            listOf("Replace cylinder 3 spark plug", "Test/replace ignition coil", "Test fuel injector", "Check for vacuum leaks", "Compression test")
        ),
        "P0304" to DTCKnowledge("P0304",
            listOf("Faulty spark plug (cyl 4)", "Bad ignition coil", "Faulty fuel injector", "Vacuum leak", "Low compression"),
            listOf("Rough idle", "Engine vibration", "Loss of power", "Poor fuel economy", "Flashing Check Engine Light"),
            listOf("Replace cylinder 4 spark plug", "Test/replace ignition coil", "Test fuel injector", "Check for vacuum leaks", "Compression test")
        ),

        // Crankshaft/Camshaft Position
        "P0335" to DTCKnowledge("P0335",
            listOf("Faulty crankshaft position sensor", "Damaged reluctor ring", "Wiring issue", "Poor connector", "PCM failure"),
            listOf("No start condition", "Engine stalling", "Rough running", "Check Engine Light", "Intermittent starting"),
            listOf("Replace crankshaft position sensor", "Inspect reluctor ring", "Repair wiring", "Clean connectors", "Test PCM")
        ),
        "P0340" to DTCKnowledge("P0340",
            listOf("Faulty camshaft position sensor", "Damaged tone wheel", "Wiring problem", "Timing chain stretch", "PCM issue"),
            listOf("Hard starting", "Engine misfires", "Rough idle", "Stalling", "Check Engine Light"),
            listOf("Replace camshaft position sensor", "Check timing chain", "Repair wiring", "Inspect tone wheel", "Test PCM")
        ),

        // EGR
        "P0400" to DTCKnowledge("P0400",
            listOf("Clogged EGR valve", "EGR passages blocked", "Faulty EGR solenoid", "Vacuum leak", "Carbon buildup"),
            listOf("Rough idle", "Engine knock", "Failed emissions", "Poor performance", "Check Engine Light"),
            listOf("Clean or replace EGR valve", "Clean EGR passages", "Test EGR solenoid", "Check vacuum lines", "Use fuel system cleaner")
        ),
        "P0401" to DTCKnowledge("P0401",
            listOf("Clogged EGR passages", "Faulty EGR valve", "Bad DPFE sensor", "Carbon buildup", "Vacuum leak"),
            listOf("Engine ping or knock", "Failed emissions test", "Rough idle", "Check Engine Light", "Poor performance"),
            listOf("Clean EGR passages", "Replace EGR valve", "Replace DPFE sensor", "Clean intake manifold", "Check vacuum hoses")
        ),

        // Catalyst
        "P0420" to DTCKnowledge("P0420",
            listOf("Worn catalytic converter", "Failed O2 sensor", "Exhaust leak", "Engine misfire damage", "Rich/lean fuel condition"),
            listOf("Failed emissions test", "Reduced fuel economy", "Rotten egg smell", "Check Engine Light", "Slight power loss"),
            listOf("Replace catalytic converter", "Test O2 sensors", "Fix exhaust leaks", "Resolve misfire issues", "Check fuel system")
        ),
        "P0430" to DTCKnowledge("P0430",
            listOf("Worn catalytic converter (bank 2)", "Failed O2 sensor", "Exhaust leak", "Engine misfire damage", "Fuel system issues"),
            listOf("Failed emissions", "Reduced efficiency", "Sulfur smell", "Check Engine Light", "Power loss"),
            listOf("Replace catalytic converter", "Test O2 sensors", "Repair exhaust", "Fix misfires", "Check fuel trim")
        ),

        // EVAP
        "P0440" to DTCKnowledge("P0440",
            listOf("Loose or damaged gas cap", "EVAP leak", "Faulty purge valve", "Damaged EVAP hose", "Charcoal canister issue"),
            listOf("Check Engine Light", "Fuel odor", "Failed emissions", "No drivability symptoms", "Occasional rough idle"),
            listOf("Tighten or replace gas cap", "Smoke test EVAP system", "Replace purge valve", "Inspect EVAP hoses", "Replace charcoal canister if needed")
        ),
        "P0441" to DTCKnowledge("P0441",
            listOf("Faulty purge solenoid", "Vacuum leak", "Damaged hoses", "Blocked vent", "PCM issue"),
            listOf("Check Engine Light", "Possible rough idle", "Fuel smell", "Failed emissions", "Hard starting"),
            listOf("Replace purge solenoid", "Check vacuum lines", "Inspect EVAP hoses", "Test vent valve", "Clear codes")
        ),
        "P0442" to DTCKnowledge("P0442",
            listOf("Small EVAP leak", "Loose gas cap", "Cracked hose", "Faulty seal", "Damaged canister"),
            listOf("Check Engine Light", "Fuel odor", "Failed emissions", "No drivability issues", "Hard to diagnose"),
            listOf("Replace gas cap", "Smoke test system", "Inspect all EVAP hoses", "Check filler neck", "Replace leaking component")
        ),
        "P0455" to DTCKnowledge("P0455",
            listOf("Gas cap not installed", "Large EVAP leak", "Disconnected hose", "Damaged canister", "Missing seal"),
            listOf("Strong fuel odor", "Check Engine Light", "Failed emissions", "No performance issues", "Easy to diagnose"),
            listOf("Install gas cap properly", "Inspect EVAP system visually", "Check all hose connections", "Replace damaged parts", "Clear codes")
        ),

        // Vehicle Speed
        "P0500" to DTCKnowledge("P0500",
            listOf("Faulty vehicle speed sensor", "Damaged wiring", "Bad speedometer", "Transmission issue", "PCM problem"),
            listOf("Speedometer not working", "Cruise control inoperative", "Transmission shift problems", "Check Engine Light", "ABS warning"),
            listOf("Replace vehicle speed sensor", "Repair wiring", "Check speedometer", "Inspect transmission", "Test PCM")
        ),

        // Idle Control
        "P0505" to DTCKnowledge("P0505",
            listOf("Dirty throttle body", "Faulty IAC valve", "Vacuum leak", "Carbon buildup", "PCM issue"),
            listOf("Unstable idle", "Stalling", "High idle", "Low idle", "Check Engine Light"),
            listOf("Clean throttle body", "Replace IAC valve", "Check for vacuum leaks", "Clean intake", "Test PCM")
        ),
        "P0506" to DTCKnowledge("P0506",
            listOf("Vacuum leak", "Dirty throttle body", "Faulty IAC valve", "Low fuel pressure", "Air intake restriction"),
            listOf("Low idle RPM", "Engine stalling", "Rough idle", "Check Engine Light", "Hesitation"),
            listOf("Check for vacuum leaks", "Clean throttle body", "Test IAC valve", "Check fuel pressure", "Inspect air intake")
        ),
        "P0507" to DTCKnowledge("P0507",
            listOf("Vacuum leak", "Faulty IAC valve", "Dirty throttle body", "Air leak after MAF", "PCM issue"),
            listOf("High idle RPM", "Racing engine", "Check Engine Light", "Difficult to control speed", "Increased fuel use"),
            listOf("Check for vacuum leaks", "Clean or replace IAC", "Clean throttle body", "Inspect intake", "Test PCM")
        ),

        // Computer/ECM
        "P0600" to DTCKnowledge("P0600",
            listOf("Faulty TCM", "Wiring issue", "PCM failure", "Communication error", "Ground problem"),
            listOf("Transmission problems", "Check Engine Light", "Limp mode", "No communication", "Multiple codes"),
            listOf("Check CAN bus wiring", "Test TCM", "Test PCM", "Check grounds", "Inspect connectors")
        ),
        "P0606" to DTCKnowledge("P0606",
            listOf("Internal PCM failure", "Wiring issue", "Power supply problem", "Ground issue", "Software corruption"),
            listOf("Multiple warning lights", "Engine performance issues", "No start", "Erratic behavior", "Check Engine Light"),
            listOf("Check PCM power and ground", "Inspect wiring", "Reprogram PCM", "Replace PCM if needed", "Check battery voltage")
        ),

        // Transmission
        "P0700" to DTCKnowledge("P0700",
            listOf("TCM malfunction", "Transmission issue", "Wiring problem", "Low transmission fluid", "Solenoid failure"),
            listOf("Check Engine Light", "Transmission warning", "Shifting problems", "Limp mode", "No overdrive"),
            listOf("Read transmission codes", "Check fluid level", "Inspect wiring", "Test solenoids", "Service transmission")
        ),
        "P0715" to DTCKnowledge("P0715",
            listOf("Faulty input speed sensor", "Wiring issue", "Damaged reluctor", "Low fluid", "TCM failure"),
            listOf("Harsh shifting", "No shift", "Check Engine Light", "Speedometer issues", "Limp mode"),
            listOf("Replace input speed sensor", "Repair wiring", "Check transmission fluid", "Inspect TCM", "Check reluctor")
        ),
        "P0720" to DTCKnowledge("P0720",
            listOf("Faulty output speed sensor", "Wiring damage", "Low transmission fluid", "Internal transmission issue", "TCM problem"),
            listOf("Speedometer not working", "Shifting problems", "Check Engine Light", "Limp mode", "No overdrive"),
            listOf("Replace output speed sensor", "Repair wiring", "Check fluid level", "Inspect transmission", "Test TCM")
        ),
        "P0730" to DTCKnowledge("P0730",
            listOf("Low transmission fluid", "Worn clutch packs", "Faulty solenoid", "Internal damage", "Torque converter issue"),
            listOf("Slipping transmission", "Harsh shifts", "Check Engine Light", "Poor acceleration", "RPM flare"),
            listOf("Check fluid level and condition", "Service transmission", "Replace solenoids", "Rebuild if needed", "Check torque converter")
        ),

        // Network/Communication
        "U0100" to DTCKnowledge("U0100",
            listOf("PCM/ECM failure", "CAN bus wiring issue", "Poor ground", "Damaged connector", "Module failure"),
            listOf("No start condition", "Multiple warning lights", "Communication errors", "Limp mode", "Random symptoms"),
            listOf("Check CAN bus wiring", "Test PCM power and ground", "Inspect all connectors", "Check for damaged wiring", "Replace PCM if needed")
        ),
        "U0101" to DTCKnowledge("U0101",
            listOf("TCM failure", "CAN bus issue", "Wiring damage", "Poor connection", "PCM-TCM incompatibility"),
            listOf("Transmission problems", "No shift", "Warning lights", "Communication error", "Limp mode"),
            listOf("Check CAN wiring", "Test TCM", "Inspect connectors", "Verify compatibility", "Replace TCM")
        ),
        "U0121" to DTCKnowledge("U0121",
            listOf("ABS module failure", "CAN bus wiring", "Poor ground", "Connector damage", "Module programming"),
            listOf("ABS warning light", "No ABS function", "Communication error", "Brake issues", "Multiple codes"),
            listOf("Test ABS module", "Check CAN wiring", "Inspect connectors", "Check grounds", "Reprogram module")
        )
    )

    /**
     * Get knowledge for a DTC code
     */
    fun getKnowledge(code: String): DTCKnowledge? {
        return knowledgeBase[code.uppercase().trim()]
    }

    /**
     * Get possible causes for a DTC
     */
    fun getCauses(code: String): List<String> {
        return getKnowledge(code)?.possibleCauses ?: emptyList()
    }

    /**
     * Get symptoms for a DTC
     */
    fun getSymptoms(code: String): List<String> {
        return getKnowledge(code)?.symptoms ?: emptyList()
    }

    /**
     * Get solutions for a DTC
     */
    fun getSolutions(code: String): List<String> {
        return getKnowledge(code)?.solutions ?: emptyList()
    }
}
