package dz.cardiag.app

import android.content.Intent

/**
 * Maps the existing per-feature Activity entries (kept in the manifest for
 * backwards compatibility / external deep links) to the single unified
 * Compose navigation graph.
 */
internal object CarDiagActivityRoute {
    const val EXTRA_INITIAL_ROUTE = "cardiag_initial_route"
    const val EXTRA_VEHICLE_ID = "cardiag_vehicle_id"
    const val EXTRA_VEHICLE_NAME = "cardiag_vehicle_name"
    const val EXTRA_DTC_CODE = "cardiag_dtc_code"

    fun deepLinkTo(route: CarDiagRoute, vehicleId: String? = null, vehicleName: String? = null, dtcCode: String? = null): Intent {
        val intent = Intent("dz.cardiag.app.OPEN")
        intent.putExtra(EXTRA_INITIAL_ROUTE, route.name)
        intent.putExtra(EXTRA_VEHICLE_ID, vehicleId)
        intent.putExtra(EXTRA_VEHICLE_NAME, vehicleName)
        intent.putExtra(EXTRA_DTC_CODE, dtcCode)
        return intent
    }
}
