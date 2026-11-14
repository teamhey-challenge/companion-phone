# R8 rules for the HEY car app. Libraries ship their own consumer rules (kotlinx.serialization, OkHttp, Pleos SDK
# model/AIDL packages); the rules below are the belt-and-braces ones for what this app resolves by name at runtime.

# kotlinx.serialization: generated $$serializer / Companion.serializer() lookups on the wire models (core:model).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class ai.hey.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ai.hey.model.**$$serializer { *; }
-keepclasseswithmembers class ai.hey.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# OkHttp 5 / Okio: optional TLS providers are looked up reflectively and may be absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Pleos SDK: AIDL stubs/parcelables, binder proxies and listeners are matched by name across the process boundary,
# and the SDK resolves its implementation classes and permission annotations by reflection.
-keep class ai.pleos.playground.** { *; }
-keep interface ai.pleos.playground.** { *; }
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keep class * extends android.os.IInterface { *; }
-keep class * extends android.os.Binder { *; }
-dontwarn ai.pleos.**

# android.car is an optional system library that is not on the compile classpath of release shrinking.
-dontwarn android.car.**
