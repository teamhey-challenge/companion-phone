# R8 rules for the HEY phone app. Libraries ship their own consumer rules (kotlinx.serialization, OkHttp, CameraX, zxing);
# the rules below are the belt-and-braces ones for what this app resolves by name at runtime.

# kotlinx.serialization: generated $$serializer / Companion.serializer() lookups on the wire models (data/ApiClient.kt).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class ai.hey.phone.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ai.hey.phone.data.**$$serializer { *; }
-keepclasseswithmembers class ai.hey.phone.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# OkHttp 5 / Okio: optional TLS providers are looked up reflectively and may be absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# androidx.security-crypto -> Tink: compile-time-only annotations (R8 missing_rules.txt).
-dontwarn com.google.errorprone.annotations.**
