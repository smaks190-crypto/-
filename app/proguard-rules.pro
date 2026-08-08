# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# R8 Optimization & Code Shrinking
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Preserve Line Numbers & Attributes for Crash Logs and Reflection
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations

# Moshi JSON Serialization (Refined to keep generated adapters & annotated fields without over-retaining Moshi internals)
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}
-keep class * extends com.squareup.moshi.JsonAdapter
-keep class * implements com.squareup.moshi.JsonAdapter$Factory
-keepclassmembers class * {
    public static com.squareup.moshi.JsonAdapter$Factory FACTORY;
}
-keep class **JsonAdapter {
    public <init>(...);
}
-dontwarn com.squareup.moshi.**

# Retrofit 2
-keepclassmembers enum * { *; }
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# OkHttp 3 & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepclassmembers class okhttp3.internal.publicsuffix.PublicSuffixDatabase {
    private byte[] publicSuffixListBytes;
    private byte[] publicSuffixExceptionListBytes;
}

# Room Database Architecture
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Dao
-keep class **_Impl { *; }
-dontwarn androidx.room.paging.**

# Kotlin Coroutines & Flow
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepclassmembers class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-dontwarn kotlinx.coroutines.**

# Biometric & Security Crypto
-dontwarn androidx.biometric.**
-dontwarn androidx.security.crypto.**

# Compose Markdown (jeziellago)
-keep class dev.jeziellago.compose.markdown.** { *; }
-dontwarn dev.jeziellago.compose.markdown.**

# Application Data Models & Room Entities
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** {
    <fields>;
    <methods>;
}

# Jetpack Compose State & Node Retention
-keepclassmembers class * extends androidx.compose.ui.node.Owner { *; }
-dontwarn androidx.compose.**

# External / Internal Tooling Warnings Suppressions
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# Release Isolation & GlobalConsoleLogger Stripping
-assumenosideeffects class com.example.utils.GlobalConsoleLogger {
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** clear(...);
    public static *** setupUncaughtExceptionHandler(...);
}


