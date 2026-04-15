# WiseDrive OBD2 SDK ProGuard Rules
# ═══════════════════════════════════════════════════════════════
# MAXIMUM OBFUSCATION for Anti-Reverse-Engineering
# ═══════════════════════════════════════════════════════════════

# ─── KEEP PUBLIC API ONLY ──────────────────────────────────────
# Only the absolute minimum public surface is kept
-keep public class com.wisedrive.obd2.WiseDriveOBD2SDK {
    public *;
}

# Keep public model classes (needed for JSON serialization)
-keep public class com.wisedrive.obd2.models.** { *; }

# Keep SDKConfig for client initialization
-keep public class com.wisedrive.obd2.models.SDKConfig { *; }
-keep public class com.wisedrive.obd2.models.ScanReport { *; }

# ─── AGGRESSIVE OBFUSCATION ──────────────────────────────────
# Flatten all internal packages into a single unreadable package
-repackageclasses 'a'
-flattenpackagehierarchy 'a'

# Allow overloading of method names (different methods can have same name)
-overloadaggressively

# Allow access modification for maximum inlining
-allowaccessmodification

# Merge classes where possible (reduces class count, hides structure)
-mergeinterfacesaggressively

# ─── REMOVE ALL LOGGING COMPLETELY ─────────────────────────────
# No log output = no string clues for reverse engineer
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Remove SDK internal logging
-assumenosideeffects class com.wisedrive.obd2.util.Logger {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
}

# ─── MAXIMUM OPTIMIZATION ──────────────────────────────────────
-optimizationpasses 7
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,code/removal/simple,code/removal/advanced

# ─── REMOVE SOURCE FILE INFO ──────────────────────────────────
# No file names or line numbers in stack traces
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable

# ─── REMOVE METHOD PARAMETER NAMES ────────────────────────────
# Prevents decompiler from showing meaningful parameter names
-keepparameternames

# ─── OBFUSCATE INTERNAL CLASSES AGGRESSIVELY ───────────────────
# Protocol logic - most critical IP
-keep,allowobfuscation class com.wisedrive.obd2.protocol.** { *; }
-keep,allowobfuscation class com.wisedrive.obd2.security.StringProtector { *; }
-keep,allowobfuscation class com.wisedrive.obd2.security.ObfuscatedProtocol { *; }
-keep,allowobfuscation class com.wisedrive.obd2.security.ObfuscatedECUConfig { *; }

# ─── OBFUSCATE DICTIONARY ─────────────────────────────────────
# Use very short, confusing names (a, b, c, aa, ab, etc.)
# R8 does this by default but we reinforce it
-obfuscationdictionary proguard-dictionary.txt
-classobfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

# ─── KOTLIN SPECIFIC ──────────────────────────────────────────
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep Kotlin coroutines (internal, but needed for runtime)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ─── GSON SERIALIZATION ───────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep fields for serialization in model classes
-keepclassmembers class com.wisedrive.obd2.models.** {
    <fields>;
}

# ─── OKHTTP ───────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ─── SECURITY CLASSES ─────────────────────────────────────────
# Keep crypto functionality but obfuscate names
-keepclassmembers class javax.crypto.** { *; }
-keepclassmembers class javax.crypto.spec.** { *; }

# ─── STRIP KOTLIN INTRINSICS MESSAGES ─────────────────────────
# Remove Kotlin null-check messages that reveal parameter names
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
    public static void throwUninitializedPropertyAccessException(...);
}
