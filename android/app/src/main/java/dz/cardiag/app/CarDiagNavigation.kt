package dz.cardiag.app

/** Canonical product flow. Keep route names stable so screens do not invent their own navigation vocabulary. */
enum class CarDiagRoute {
    // Primary bottom-bar destinations
    HOME,
    GARAGE,
    DIAGNOSE,
    HISTORY,
    MORE,

    // Detail destinations
    VEHICLE,
    OBD,
    SCAN_RESULTS,
    DTC,
    GUIDED_DIAGNOSIS,
    SYMPTOM,
    LIVE_DATA,
    FREEZE_FRAME,
    READINESS,
    VIN,
    AI,
    REPORT,
}

/** Lightweight navigation contract shared by Compose screens and Activity bridges. */
data class CarDiagNavArgs(
    val vehicleId: String? = null,
    val vehicleName: String? = null,
    val vin: String? = null,
    val dtcCode: String? = null,
    val sessionId: String? = null,
)

object CarDiagNavigation {
    fun diagnosticArgs(vehicleId: String?, vehicleName: String?, vin: String? = null) =
        CarDiagNavArgs(vehicleId = vehicleId, vehicleName = vehicleName, vin = vin)

    fun dtcArgs(vehicleId: String?, vehicleName: String?, dtcCode: String) =
        CarDiagNavArgs(vehicleId, vehicleName, dtcCode = dtcCode)

    fun reportArgs(vehicleId: String?, vehicleName: String?, vin: String?, sessionId: String?) =
        CarDiagNavArgs(vehicleId, vehicleName, vin, sessionId = sessionId)
}
