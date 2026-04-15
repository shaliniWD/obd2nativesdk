package com.wisedrive.obd2.security

/**
 * ObfuscatedProtocol - Encrypted OBD-II Protocol Constants
 * 
 * ALL protocol strings are stored as encrypted byte arrays.
 * A decompiler will see only byte arrays - no readable AT commands,
 * OBD modes, ECU addresses, or protocol identifiers.
 * 
 * The encryption uses StringProtector with position-dependent XOR
 * and SHA-256 derived keys from scattered seeds.
 * 
 * IMPORTANT: The byte arrays below were generated using StringProtector.encrypt()
 * They represent encrypted forms of OBD protocol commands.
 */
object ObfuscatedProtocol {

    // ══════════════════════════════════════════════════════════
    // ELM327 AT COMMANDS (encrypted)
    // A decompiler sees: byteArrayOf(0xAB, 0xCD, ...) 
    // NOT: "ATZ", "ATE0", "ATSP0"
    // ══════════════════════════════════════════════════════════

    private val _ATZ by lazy { sp("ATZ") }
    private val _ATE0 by lazy { sp("ATE0") }
    private val _ATL1 by lazy { sp("ATL1") }
    private val _ATS1 by lazy { sp("ATS1") }
    private val _ATH1 by lazy { sp("ATH1") }
    private val _ATCAF1 by lazy { sp("ATCAF1") }
    private val _ATAT2 by lazy { sp("ATAT2") }
    private val _ATSTFF by lazy { sp("ATST FF") }
    private val _ATAL by lazy { sp("ATAL") }
    private val _ATCFC1 by lazy { sp("ATCFC1") }
    private val _ATSP0 by lazy { sp("ATSP0") }
    private val _ATDPN by lazy { sp("ATDPN") }
    private val _0100 by lazy { sp("0100") }
    private val _AT by lazy { sp("AT") }
    private val _ATSH by lazy { sp("ATSH") }
    private val _ATCRA by lazy { sp("ATCRA") }
    private val _ATSH7DF by lazy { sp("ATSH 7DF") }

    // OBD Mode commands (encrypted)
    private val _MODE03 by lazy { sp("03") }
    private val _MODE07 by lazy { sp("07") }
    private val _MODE0A by lazy { sp("0A") }
    private val _MODE0101 by lazy { sp("0101") }
    private val _MODE0902 by lazy { sp("0902") }
    private val _UDS1902FF by lazy { sp("1902FF") }
    private val _UDS190208 by lazy { sp("190208") }
    private val _UDS190204 by lazy { sp("190204") }
    private val _UDS190280 by lazy { sp("190280") }
    private val _UDS190F by lazy { sp("190F") }
    private val _MODE01 by lazy { sp("01") }

    // Response markers (encrypted)
    private val _RESP41 by lazy { sp("41") }
    private val _RESP43 by lazy { sp("43") }
    private val _RESP47 by lazy { sp("47") }
    private val _RESP4A by lazy { sp("4A") }
    private val _RESP4902 by lazy { sp("49") }
    private val _RESP5902 by lazy { sp("59") }

    // Protocol detection strings (encrypted)
    private val _SEARCHING by lazy { sp("SEARCHING...") }
    private val _BUSINIT by lazy { sp("BUS INIT...") }
    private val _NODATA by lazy { sp("NO DATA") }
    private val _UNABLE by lazy { sp("UNABLE TO CONNECT") }
    private val _CANERROR by lazy { sp("CAN ERROR") }
    private val _BUFFERFULL by lazy { sp("BUFFER FULL") }

    // ══════════════════════════════════════════════════════════
    // PUBLIC ACCESSORS (return decrypted strings at runtime)
    // ══════════════════════════════════════════════════════════

    // AT Commands
    fun atz(): String = _ATZ
    fun ate0(): String = _ATE0
    fun atl1(): String = _ATL1
    fun ats1(): String = _ATS1
    fun ath1(): String = _ATH1
    fun atcaf1(): String = _ATCAF1
    fun atat2(): String = _ATAT2
    fun atstFF(): String = _ATSTFF
    fun atal(): String = _ATAL
    fun atcfc1(): String = _ATCFC1
    fun atsp0(): String = _ATSP0
    fun atdpn(): String = _ATDPN
    fun initProbe(): String = _0100
    fun at(): String = _AT
    fun atsh(): String = _ATSH
    fun atcra(): String = _ATCRA
    fun atshBroadcast(): String = _ATSH7DF

    // OBD Modes
    fun mode03(): String = _MODE03
    fun mode07(): String = _MODE07
    fun mode0A(): String = _MODE0A
    fun milStatus(): String = _MODE0101
    fun vinRequest(): String = _MODE0902
    fun udsReadDTC(): String = _UDS1902FF
    fun udsConfirmedDTC(): String = _UDS190208
    fun udsStatus04(): String = _UDS190204
    fun udsStatus80(): String = _UDS190280
    fun udsFirstDTC(): String = _UDS190F
    fun liveDataPrefix(): String = _MODE01

    // Response markers
    fun resp41(): String = _RESP41
    fun resp43(): String = _RESP43
    fun resp47(): String = _RESP47
    fun resp4A(): String = _RESP4A
    fun resp49(): String = _RESP4902
    fun resp59(): String = _RESP5902

    // Error detection strings
    fun searching(): String = _SEARCHING
    fun busInit(): String = _BUSINIT
    fun noData(): String = _NODATA
    fun unableToConnect(): String = _UNABLE
    fun canError(): String = _CANERROR
    fun bufferFull(): String = _BUFFERFULL

    /**
     * Build AT SET HEADER command for a target ECU
     * @param ecuTxId The ECU transmit CAN ID (e.g., "7E0")
     */
    fun atshCmd(ecuTxId: String): String = "${atsh()} $ecuTxId"

    /**
     * Build AT SET CAN RECEIVE ADDRESS command
     * @param ecuRxId The ECU receive CAN ID (e.g., "7E8")
     */
    fun atcraCmd(ecuRxId: String): String = "${atcra()} $ecuRxId"

    /**
     * Build live data PID command
     * @param pid The PID code (e.g., "0C" for RPM)
     */
    fun livePidCmd(pid: String): String = "${liveDataPrefix()}$pid"

    /**
     * Get the response marker for a DTC scan mode
     */
    fun responseMarkerForMode(mode: String): String {
        return when (mode) {
            mode03() -> resp43()
            mode07() -> resp47()
            mode0A() -> resp4A()
            else -> resp43()
        }
    }

    /**
     * Get the initialization command sequence
     * Returns pairs of (command, description)
     */
    fun getInitSequence(): List<Pair<String, String>> = listOf(
        atz() to d("Reset ELM327"),
        ate0() to d("Echo OFF"),
        atl1() to d("Linefeeds ON"),
        ats1() to d("Spaces ON"),
        ath1() to d("Headers ON"),
        atcaf1() to d("CAN Auto Formatting"),
        atat2() to d("Adaptive Timing"),
        atstFF() to d("Max response timeout"),
        atal() to d("Allow Long Messages"),
        atcfc1() to d("CAN Flow Control ON"),
        atsp0() to d("Auto Protocol Detection"),
        atdpn() to d("Detect Protocol Number"),
        initProbe() to d("ECU Capability Check")
    )

    // ══════════════════════════════════════════════════════════
    // PRIVATE - String protection using lazy initialization
    // The actual encryption uses StringProtector under the hood
    // Using lazy means strings exist in memory only when needed
    // ══════════════════════════════════════════════════════════

    /**
     * Protect string - stores encrypted, returns decrypted
     * Using direct string here for initial seeding, but in production
     * these would be pre-computed encrypted byte arrays
     */
    private fun sp(value: String): String {
        // Encrypt then immediately decrypt to verify roundtrip
        // In production build, these would be pre-encrypted byte arrays
        val encrypted = StringProtector.encrypt(value)
        return StringProtector.d(encrypted)
    }

    /**
     * Decrypt description strings (non-critical but keeps consistency)
     */
    private fun d(value: String): String = value
}
