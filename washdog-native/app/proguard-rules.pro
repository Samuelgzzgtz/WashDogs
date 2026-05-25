-keep class com.washdog.app.data.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Ktor
-keep class io.ktor.** { *; }
-keepnames class io.ktor.** { *; }

# Kotlinx Serialization
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.washdog.app.**$$serializer { *; }
-keepclassmembers class com.washdog.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.washdog.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
