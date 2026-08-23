# CarDiag release rules.
# Supabase/Ktor/Compose are reflection-light and use Kotlin serialization.
# Keep serializable model metadata for release builds.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keep class dz.cardiag.app.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**
