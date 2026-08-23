# CarDiag release R8 rules.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# Kotlin serialization models are referenced by generated serializers.
-keep class dz.cardiag.app.VehicleModel { *; }
-keep class dz.cardiag.app.UserVehicle { *; }
-keep class dz.cardiag.app.DiagnosticHistory { *; }
-keep class dz.cardiag.app.core.DiagnosticSession { *; }
-keep class dz.cardiag.app.core.DiagnosticSessionInsert { *; }

-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**
