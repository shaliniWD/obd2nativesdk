package com.wisedrive.obd2.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.wisedrive.obd2.util.Logger
import java.io.File
import java.net.Socket

/**
 * Integrity Checker - Anti-tampering and anti-reverse-engineering
 * 
 * Checks for: debuggable apps, emulators, root, Frida, Xposed, repackaged APKs
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
        
        // 2. Check for emulator
        if (isEmulator()) {
            failedChecks.add("Emulator detected")
        }
        
        // 3. Check for root
        if (isRooted()) {
            failedChecks.add("Root access detected")
        }
        
        // 4. Check for Frida
        if (isFridaRunning()) {
            failedChecks.add("Frida detected")
        }
        
        // 5. Check for Xposed
        if (isXposedInstalled(context)) {
            failedChecks.add("Xposed framework detected")
        }
        
        // 6. Verify APK signature (optional - requires expected signature hash)
        // This check is typically done against a known good signature
        
        val isSecure = failedChecks.isEmpty()
        
        if (!isSecure) {
            Logger.w(TAG, "Integrity check failed: ${failedChecks.joinToString(", ")}")
        } else {
            Logger.d(TAG, "Integrity check passed")
        }
        
        return IntegrityResult(isSecure, failedChecks)
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
     * Check if Frida server is running
     */
    private fun isFridaRunning(): Boolean {
        // Default Frida server port
        val fridaPort = 27042
        
        return try {
            val socket = Socket("127.0.0.1", fridaPort)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
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

    /**
     * Verify APK signature matches expected
     * @param context Application context
     * @param expectedSignatureHash SHA-256 hash of expected signing certificate
     */
    @Suppress("DEPRECATION")
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
}
