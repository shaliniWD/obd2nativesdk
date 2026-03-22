# WiseDrive OBD2 SDK ProGuard Rules
# Aggressive obfuscation for anti-reverse-engineering

# ─── KEEP PUBLIC API ───────────────────────────────────────
-keep public class com.wisedrive.obd2.WiseDriveOBD2SDK {
    public *;
}

# Keep all public model classes
-keep public class com.wisedrive.obd2.models.** { *; }

# ─── OBFUSCATE INTERNAL PACKAGES ───────────────────────────
# These packages contain sensitive protocol logic
-repackageclasses 'a'
-flattenpackagehierarchy 'a'
-overloadaggressively
-allowaccessmodification

# ─── REMOVE LOGGING ────────────────────────────────────────
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

# ─── OPTIMIZATION ──────────────────────────────────────────
-optimizationpasses 5
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ─── STRING ENCRYPTION HINTS ───────────────────────────────
# For additional security, use DexGuard or similar for string encryption
# These rules prepare the codebase for string obfuscation

# ─── KOTLIN SPECIFIC ───────────────────────────────────────
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ─── GSON SERIALIZATION ────────────────────────────────────
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

# ─── OKHTTP ────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ─── SECURITY CLASSES ──────────────────────────────────────
# Obfuscate but keep functionality
-keepclassmembers class javax.crypto.** { *; }
-keepclassmembers class javax.crypto.spec.** { *; }

# ─── REMOVE DEBUG INFO ─────────────────────────────────────
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
