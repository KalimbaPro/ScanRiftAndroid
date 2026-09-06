# ScanRift ProGuard / R8 rules
#
# Note on serialization: this app uses kotlinx.serialization, not Gson. Serializers
# are generated at compile time and the plugin ships its own consumer rules, so the
# DTOs need no `-keep` — which is deliberate. The previous Gson setup relied on
# reflection over field names and had no keeps at all, which would have produced
# all-null DTOs in release builds only.

# Retrofit service interfaces are reflected over at runtime.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# R8 full mode strips generic signatures from non-kept types; Retrofit needs them.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ML Kit loads its OCR pipeline reflectively.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# OkHttp 5 / Okio optional platform integrations.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
