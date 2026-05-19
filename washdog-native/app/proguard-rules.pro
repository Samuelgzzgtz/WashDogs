-keep class com.washdog.app.data.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# security-crypto / Tink references errorprone annotations at compile time only
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
