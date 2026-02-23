# ScanRift ProGuard Rules

# Keep Retrofit interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Room entities
-keep class com.scanrift.android.data.local.entity.** { *; }

# Keep Gson serialized classes
-keep class com.scanrift.android.data.remote.dto.** { *; }
-keepattributes *Annotation*
-keepattributes Signature

# Keep ML Kit
-keep class com.google.mlkit.** { *; }
