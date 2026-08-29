package dz.cardiag.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

/**
 * Production navigation graph.
 *
 * A single Scaffold + NavigationBar hosts the primary destinations. Detail
 * destinations (DTC detail, OBD, live data, freeze frame, readiness, VIN,
 * guided diagnosis, AI, vehicle profile) are pushed on top of the same
 * Scaffold, so back navigation is predictable and the bottom bar stays
 * consistent.
 */
@Composable
fun CarDiagNavGraph(
    initialRoute: CarDiagRoute,
    initialDtcCode: String?,
    initialVehicleId: String?,
    initialVehicleName: String?,
    dark: Boolean,
    arabic: Boolean,
    setDark: (Boolean) -> Unit,
    setArabic: (Boolean) -> Unit,
    persistVehicle: (String?, String?) -> Unit = { _, _ -> }
) {
    var currentRoute by rememberSaveable { mutableStateOf(initialRoute) }
    var navStack by rememberSaveable(stateSaver = NavStackSaver) {
        mutableStateOf(listOf(initialRoute))
    }
    var pendingDtcCode by rememberSaveable { mutableStateOf(initialDtcCode) }
    var pendingVehicleId by rememberSaveable { mutableStateOf(initialVehicleId) }
    var pendingVehicleName by rememberSaveable { mutableStateOf(initialVehicleName) }

    fun navigateTo(route: CarDiagRoute) {
        currentRoute = route
        navStack = if (navStack.lastOrNull() == route) navStack else navStack + route
    }
    fun pushDetail(route: CarDiagRoute) {
        navStack = navStack + route
        currentRoute = route
    }
    fun pop() {
        if (navStack.size > 1) {
            navStack = navStack.dropLast(1)
            currentRoute = navStack.last()
        }
    }
    fun setVehicle(id: String?, name: String?) {
        pendingVehicleId = id
        pendingVehicleName = name
        persistVehicle(id, name)
    }
    fun setDtc(code: String?) {
        pendingDtcCode = code
    }

    Scaffold(
        bottomBar = {
            // Bottom bar is hidden when drilling into a detail so that the
            // professional detail surfaces own the full viewport.
            if (currentRoute in PRIMARY_ROUTES) {
                NavigationBar {
                    PRIMARY_ROUTES.forEach { route ->
                        val selected = currentRoute == route
                        val (label, icon) = routePresentation(route)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTo(route) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        when (currentRoute) {
            CarDiagRoute.HOME -> HomeScreen(
                padding = padding,
                arabic = arabic,
                activeVehicleId = pendingVehicleId,
                activeVehicleName = pendingVehicleName,
                onVehicle = { id, name -> setVehicle(id, name); navigateTo(CarDiagRoute.GARAGE) },
                onOpenDtc = { code -> setDtc(code); pushDetail(CarDiagRoute.DTC) },
                onOpenObd = { pushDetail(CarDiagRoute.OBD) },
                onOpenSymptom = { pushDetail(CarDiagRoute.SYMPTOM) },
                onOpenGuided = { pushDetail(CarDiagRoute.GUIDED_DIAGNOSIS) },
                onOpenAi = { pushDetail(CarDiagRoute.AI) },
                onOpenHistory = { navigateTo(CarDiagRoute.HISTORY) },
                onOpenDtcSearch = { pushDetail(CarDiagRoute.DTC) }
            )
            CarDiagRoute.GARAGE -> GarageScreen(
                padding = padding,
                arabic = arabic,
                activeVehicleId = pendingVehicleId,
                onVehicle = { id, name ->
                    setVehicle(id, name)
                    pushDetail(CarDiagRoute.VEHICLE)
                }
            )
            CarDiagRoute.DIAGNOSE -> DiagnoseHubScreen(
                padding = padding,
                arabic = arabic,
                hasVehicle = pendingVehicleId != null,
                onOpenObd = { pushDetail(CarDiagRoute.OBD) },
                onOpenSymptom = { pushDetail(CarDiagRoute.SYMPTOM) },
                onOpenDtc = { code -> setDtc(code); pushDetail(CarDiagRoute.DTC) },
                onOpenGuided = { pushDetail(CarDiagRoute.GUIDED_DIAGNOSIS) }
            )
            CarDiagRoute.HISTORY -> HistoryScreen(
                padding = padding,
                arabic = arabic,
                onOpenSession = { sessionId -> pushDetail(CarDiagRoute.REPORT) }
            )
            CarDiagRoute.MORE -> MoreScreen(
                padding = padding,
                arabic = arabic,
                dark = dark,
                setDark = setDark,
                setArabic = setArabic,
                onOpenAdvanced = { pushDetail(CarDiagRoute.OBD) }
            )
            CarDiagRoute.VEHICLE -> VehicleProfileScreen(
                padding = padding,
                arabic = arabic,
                vehicleId = pendingVehicleId,
                vehicleName = pendingVehicleName,
                onBack = ::pop,
                onOpenDtc = { code -> setDtc(code); pushDetail(CarDiagRoute.DTC) },
                onOpenObd = { pushDetail(CarDiagRoute.OBD) }
            )
            CarDiagRoute.OBD -> ObdOnboardingScreen(
                padding = padding,
                arabic = arabic,
                vehicleId = pendingVehicleId,
                vehicleName = pendingVehicleName,
                onBack = ::pop,
                onOpenDtc = { code -> setDtc(code); pushDetail(CarDiagRoute.DTC) },
                onOpenLiveData = { pushDetail(CarDiagRoute.LIVE_DATA) },
                onOpenFreezeFrame = { pushDetail(CarDiagRoute.FREEZE_FRAME) },
                onOpenReadiness = { pushDetail(CarDiagRoute.READINESS) },
                onOpenVin = { pushDetail(CarDiagRoute.VIN) }
            )
            CarDiagRoute.SCAN_RESULTS -> ScanResultsScreen(
                padding = padding,
                arabic = arabic,
                onBack = ::pop,
                onOpenDtc = { code -> setDtc(code); pushDetail(CarDiagRoute.DTC) },
                onOpenGuided = { code -> setDtc(code); pushDetail(CarDiagRoute.GUIDED_DIAGNOSIS) },
                onOpenAi = { code -> setDtc(code); pushDetail(CarDiagRoute.AI) }
            )
            CarDiagRoute.DTC -> DtcDetailScreen(
                padding = padding,
                arabic = arabic,
                initialCode = pendingDtcCode,
                onBack = ::pop,
                onOpenGuided = { code -> setDtc(code); pushDetail(CarDiagRoute.GUIDED_DIAGNOSIS) }
            )
            CarDiagRoute.GUIDED_DIAGNOSIS -> GuidedDiagnosisScreen(
                padding = padding,
                arabic = arabic,
                vehicleId = pendingVehicleId,
                vehicleName = pendingVehicleName,
                initialCode = pendingDtcCode,
                onBack = ::pop,
                onOpenAi = { code -> setDtc(code); pushDetail(CarDiagRoute.AI) },
                onOpenObd = { pushDetail(CarDiagRoute.OBD) }
            )
            CarDiagRoute.SYMPTOM -> SymptomDiagnosisScreen(
                padding = padding,
                arabic = arabic,
                hasVehicle = pendingVehicleId != null,
                onBack = ::pop,
                onOpenAi = { pushDetail(CarDiagRoute.AI) }
            )
            CarDiagRoute.LIVE_DATA -> LiveDataScreen(
                padding = padding,
                arabic = arabic,
                initialDtc = pendingDtcCode,
                onBack = ::pop
            )
            CarDiagRoute.FREEZE_FRAME -> FreezeFrameScreen(
                padding = padding,
                arabic = arabic,
                onBack = ::pop
            )
            CarDiagRoute.READINESS -> ReadinessScreen(
                padding = padding,
                arabic = arabic,
                onBack = ::pop
            )
            CarDiagRoute.VIN -> VinScreen(
                padding = padding,
                arabic = arabic,
                onBack = ::pop,
                onLinkToProfile = {
                    setVehicle(pendingVehicleId, pendingVehicleName)
                    pop()
                }
            )
            CarDiagRoute.AI -> AiDiagnosisScreen(
                padding = padding,
                arabic = arabic,
                vehicleId = pendingVehicleId,
                vehicleName = pendingVehicleName,
                initialCode = pendingDtcCode,
                onBack = ::pop
            )
            CarDiagRoute.REPORT -> DiagnosticReportScreen(
                padding = padding,
                arabic = arabic,
                onBack = ::pop
            )
        }
    }
}

@Composable
private fun routePresentation(route: CarDiagRoute): Pair<String, androidx.compose.ui.graphics.vector.ImageVector> = when (route) {
    CarDiagRoute.HOME -> stringResource(R.string.nav_home) to Icons.Default.Home
    CarDiagRoute.GARAGE -> stringResource(R.string.nav_garage) to Icons.Default.DirectionsCar
    CarDiagRoute.DIAGNOSE -> stringResource(R.string.nav_diagnose) to Icons.Default.Build
    CarDiagRoute.HISTORY -> stringResource(R.string.nav_history) to Icons.Default.History
    CarDiagRoute.MORE -> stringResource(R.string.nav_more) to Icons.Default.MoreHoriz
    else -> "" to Icons.Default.Home
}

internal val PRIMARY_ROUTES = listOf(
    CarDiagRoute.HOME,
    CarDiagRoute.GARAGE,
    CarDiagRoute.DIAGNOSE,
    CarDiagRoute.HISTORY,
    CarDiagRoute.MORE
)

private val NavStackSaver: androidx.compose.runtime.saveable.Saver<List<CarDiagRoute>, Any> =
    androidx.compose.runtime.saveable.listSaver(
        save = { list -> list.map { it.name } },
        restore = { saved -> (saved as List<String>).mapNotNull { runCatching { CarDiagRoute.valueOf(it) }.getOrNull() } }
    )
