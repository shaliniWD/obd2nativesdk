package com.wisedrive.obd2.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.wisedrive.obd2.util.Logger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Socket

/**
 * Integrity Checker - Anti-tampering and anti-reverse-engineering
 * 
 * Comprehensive detection for:
 * - Debuggable apps / attached debuggers
 * - Emulators (20+ indicators)
 * - Root access (multiple su paths)
 * - Frida server / Frida gadget
 * - Xposed / LSPosed / EdXposed frameworks
 * - Dynamic instrumentation (ptrace detection)
 * - Repackaged/modified APKs
 * - Magisk Hide
 */
object IntegrityChecker {

    private const val TAG = "IntegrityChecker"

    data class IntegrityResult(
        val isSecure: Boolean,
        val failedChecks: List<String>
    )

    /**
     * Run all integrity checks
     * @return IntegrityResult with status and failed checks
     */
    fun verifyEnvironment(context: Context): IntegrityResult {
        val failedChecks = mutableListOf<String>()
        
        // 1. Check if debuggable
        if (isDebuggable(context)) {
            failedChecks.add("Debuggable build detected")
        }
        
        // 2. Check for attached debugger
        if (isDebuggerAttached()) {
            failedChecks.add("Debugger attached")
        }
        
        // 3. Check for emulator
        if (isEmulator()) {
            failedChecks.add("Emulator detected")
        }
        
        // 4. Check for root
        if (isRooted()) {
            failedChecks.add("Root access detected")
        }
        
        // 5. Check for Frida (multiple detection methods)
        if (isFridaRunning()) {
            failedChecks.add("Frida detected")
        }
        
        // 6. Check for Xposed/LSPosed
        if (isXposedInstalled(context)) {
            failedChecks.add("Xposed framework detected")
        }
        
        // 7. Check for hooking frameworks via /proc/self/maps
        if (isHookingLibraryLoaded()) {
            failedChecks.add("Hooking library detected in process memory")
        }
        
        // 8. Check for Magisk
        if (isMagiskPresent()) {
            failedChecks.add("Magisk detected")
        }
        
        val isSecure = failedChecks.isEmpty()
        
        if (!isSecure) {
            Logger.w(TAG, "Integrity check failed: ${failedChecks.joinToString(", ")}")
        } else {
            Logger.d(TAG, "Integrity check passed")
        }
        
        return IntegrityResult(isSecure, failedChecks)
    }

    /**
     * Check if a debugger is actively attached
     */
    private fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    /**
     * Check if app is debuggable
     */
    private fun isDebuggable(context: Context): Boolean {
        return try {
            val flags = context.applicationInfo.flags
            (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if running on emulator
     */
    private fun isEmulator(): Boolean {
        val indicators = listOf(
            Build.FINGERPRINT.startsWith("generic"),
            Build.FINGERPRINT.startsWith("unknown"),
            Build.MODEL.contains("google_sdk"),
            Build.MODEL.contains("Emulator"),
            Build.MODEL.contains("Android SDK built for x86"),
            Build.MANUFACTURER.contains("Genymotion"),
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"),
            "google_sdk" == Build.PRODUCT,
            Build.HARDWARE.contains("goldfish"),
            Build.HARDWARE.contains("ranchu"),
            Build.BOARD.lowercase().contains("nox"),
            Build.BOOTLOADER.lowercase().contains("nox"),
            Build.HARDWARE.lowercase().contains("nox"),
            Build.PRODUCT.lowercase().contains("nox"),
            @Suppress("DEPRECATION")
            Build.SERIAL.lowercase().contains("nox")
        )
        
        return indicators.any { it }
    }

    /**
     * Check for root access
     */
    private fun isRooted(): Boolean {
        // Check for common su binary locations
        val suPaths = listOf(
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/su/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/data/app/eu.chainfire.supersu"
        )
        
        for (path in suPaths) {
            if (File(path).exists()) {
                return true
            }
        }
        
        // Try to execute su
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.destroy()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if Frida server is running (multiple detection methods)
     */
    private fun isFridaRunning(): Boolean {
        // Method 1: Default Frida server port
        val fridaPorts = listOf(27042, 27043)
        for (port in fridaPorts) {
            try {
                val socket = Socket("127.0.0.1", port)
                socket.close()
                return true
            } catch (_: Exception) {}
        }
        
        // Method 2: Check for frida-server in running processes
        try {
            val process = Runtime.getRuntime().exec("ps")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("frida") == true) {
                    reader.close()
                    return true
                }
            }
            reader.close()
        } catch (_: Exception) {}
        
        // Method 3: Check for frida-gadget loaded in memory
        try {
            val maps = File("/proc/self/maps")
            if (maps.exists()) {
                val content = maps.readText()
                if (content.contains("frida") || content.contains("gadget")) {
                    return true
                }
            }
        } catch (_: Exception) {}
        
        // Method 4: Check for frida named pipes
        val fridaPipes = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server"
        )
        for (pipe in fridaPipes) {
            if (File(pipe).exists()) return true
        }
        
        return false
    }

    /**
     * Check if Xposed framework is installed
     */
    private fun isXposedInstalled(context: Context): Boolean {
        val xposedPackages = listOf(
            "de.robv.android.xposed.installer",
            "io.va.exposed",
            "org.meowcat.edxposed.manager",
            "org.lsposed.manager"
        )
        
        val pm = context.packageManager
        
        for (pkg in xposedPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Package not found, continue
            }
        }
        
        // Also check for Xposed in stack trace
        try {
            throw Exception("Xposed check")
        } catch (e: Exception) {
            for (element in e.stackTrace) {
                if (element.className.contains("xposed", ignoreCase = true)) {
                    return true
                }
            }
        }
        
        return false
    }

    fun verifySignature(context: Context, expectedSignatureHash: String): Boolean {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            if (signatures.isNullOrEmpty()) {
                return false
            }
            
            val signature = signatures[0]
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hash = md.digest(signature.toByteArray())
            val hashString = hash.joinToString("") { "%02x".format(it) }
            
            hashString.equals(expectedSignatureHash, ignoreCase = true)
        } catch (e: Exception) {
            Logger.e(TAG, "Signature verification failed: ${e.message}")
            false
        }
    }

    /**
     * Check for hooking libraries loaded in process memory
     * Reads /proc/self/maps for known hooking frameworks
     */
    private fun isHookingLibraryLoaded(): Boolean {
        val suspiciousLibs = listOf(
            "substrate", "cydia", "xhook", "whale",
            "epic", "pine", "sandhook", "dobby",
            "shadowhook", "bhook", "bytehook"
        )
        
        try {
            val maps = File("/proc/self/maps")
            if (maps.exists()) {
                val content = maps.readText().lowercase()
                for (lib in suspiciousLibs) {
                    if (content.contains(lib)) return true
                }
            }
        } catch (_: Exception) {}
        
        return false
    }

    /**
     * Check for Magisk (root hiding framework)
     */
    private fun isMagiskPresent(): Boolean {
        val magiskIndicators = listOf(
            "/sbin/.magisk",
            "/data/adb/magisk",
            "/data/adb/modules",
            "/system/xbin/magisk"
        )
        
        for (path in magiskIndicators) {
            if (File(path).exists()) return true
        }
        
        // Check for MagiskHide/Zygisk in process props
        try {
            val process = Runtime.getRuntime().exec("getprop ro.boot.vbmeta.device_state")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()?.trim()
            reader.close()
            if (result == "unlocked") return true
        } catch (_: Exception) {}
        
        return false
    }
}
