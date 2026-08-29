package dz.cardiag.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
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
import dz.cardiag.app.core.AppMode

/**
 * Production navigation graph.
 *
 * A single Scaffold + NavigationBar hosts the primary destinations. Detail
 * destinations (DTC detail, OBD, live data, freeze frame, readiness, VIN,
 * guided diagnosis, AI, vehicle profile) are pushed on top of the same
 * Scaffold, so back navigation is predictable and the bottom bar stays
 * consistent.
 *
 * The `mode` parameter influences the visible destinations: in [AppMode.DRIVER]
 * the bottom bar hides the diagnostic hub and surfaces a simpler "My vehicle"
 * primary destination.
 */
@Composable
fun CarDiagNavGraph(
    initialRoute: CarDiagRoute,
    initialDtcCode: String?,
    initialVehicleId: String?,
    initialVehicleName: String?,
    dark: Boolean,
    arabic: Boolean,
    mode: AppMode,
    setDark: (Boolean) -> Unit,
    setArabic: (Boolean) -> Unit,
    setMode: (AppMode) -> Unit,
    persistVehicle: (String?, String?) -> Unit = { _, _ -> },
    onReopenOnboarding: () -> Unit = {}
) {
    var currentRoute by rememberSaveable { mutableStateOf(initialRoute) }
    var navStack by rememberSaveable(stateSaver = NavStackSaver) {
        mutableStateOf(listOf(initialRoute))
    }
    var pendingDtcCode by rememberSaveable { mutableStateOf(initialDtcCode) }
    var pendingSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    // Before/After snapshots are ONLY seeded from the user recording a real
    // scan. We no longer fabricate demo snapshots.
    var pendingBefore by remember { mutableStateOf<dz.cardiag.app.core.BeforeAfterSnapshot?>(null) }
    var pendingAfter by remember { mutableStateOf<dz.cardiag.app.core.BeforeAfterSnapshot?>(null) }
    var pendingVehicleId by rememberSaveable { mutableStateOf(initialVehicleId) }
    var pendingVehicleName by rememberSaveable { mutableStateOf(initialVehicleName) }

    val primaryRoutes = remember(mode) {
        if (mode == AppMode.DRIVER) {
            listOf(
                CarDiagRoute.HOME,
                CarDiagRoute.GARAGE,
                CarDiagRoute.SYMPTOM,
                CarDiagRoute.HISTORY,
                CarDiagRoute.MORE
            )
        } else {
            listOf(
                CarDiagRoute.HOME,
                CarDiagRoute.GARAGE,
                CarDiagRoute.DIAGNOSE,
                CarDiagRoute.HISTORY,
                CarDiagRoute.MORE
            )
        }
    }

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
            if (currentRoute in primaryRoutes) {
                NavigationBar {
                    primaryRoutes.forEach { route ->
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
                mode = mode,
                activeVehicleId = pendingVehicleId,
                activeVehicleName = pendingVehicleName,
                onVehicle = { id, name -> setVehicle(id, name); navigateTo(CarDiagRoute.GARAGE) },
                onOpenDtc = { code -> setDtc(code); pushDetail(CarDiagRoute.DTC) },
                onOpenObd = { pushDetail(CarDiagRoute.OBD) },
                onOpenSymptom = { pushDetail(CarDiagRoute.SYMPTOM) },
                onOpenGuided = { pushDetail(CarDiagRoute.GUIDED_DIAGNOSIS) },
                onOpenAi = { pushDetail(CarDiagRoute.AI) },
                onOpenHistory = { navigateTo(CarDiagRoute.HISTORY) },
                onOpenDtcSearch = { pushDetail(CarDiagRoute.DTC) },
                onOpenRoadAssistant = { pushDetail(CarDiagRoute.ROAD_ASSISTANT) }
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
            CarDiagRoute.SYMPTOM -> SymptomDiagnosisScreen(
                padding = padding,
                arabic = arabic,
                hasVehicle = pendingVehicleId != null,
                onBack = ::pop,
                onOpenAi = { pushDetail(CarDiagRoute.AI) }
            )
            CarDiagRoute.HISTORY -> HistoryScreen(
                padding = padding,
                arabic = arabic,
                onOpenSession = { sessionId ->
                    pendingSessionId = sessionId
                    // No demo before/after is fabricated here. If the user
                    // taps an existing session, the report screen shows only
                    // the data that has been actually recorded for it.
                    pendingBefore = null
                    pendingAfter = null
                    pushDetail(CarDiagRoute.REPORT)
                }
            )
            CarDiagRoute.MORE -> MoreScreen(
                padding = padding,
                arabic = arabic,
                dark = dark,
                mode = mode,
                setDark = setDark,
                setArabic = setArabic,
                setMode = setMode,
                onOpenAdvanced = { pushDetail(CarDiagRoute.OBD) },
                onOpenRoadAssistant = { pushDetail(CarDiagRoute.ROAD_ASSISTANT) },
                onOpenAuth = { pushDetail(CarDiagRoute.AUTH) },
                onReopenOnboarding = onReopenOnboarding
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
            CarDiagRoute.ROAD_ASSISTANT -> RoadAssistantScreen(
                padding = padding,
                arabic = arabic,
                onBack = ::pop
            )
            CarDiagRoute.AUTH -> AuthScreen(
                padding = padding,
                arabic = arabic,
                onAuthenticated = ::pop,
                onBack = ::pop
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
                onOpenGuided = { code -> setDtc(code); pushDetail(CarDiagRoute.GUIDED_DIAGNOSIS) },
                onOpenBrowse = { pushDetail(CarDiagRoute.DTC_BROWSE) }
            )
            CarDiagRoute.DTC_BROWSE -> DtcBrowseScreen(
                padding = padding,
                arabic = arabic,
                onBack = ::pop,
                onSelectCode = { code -> setDtc(code); pushDetail(CarDiagRoute.DTC) }
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
                sessionId = pendingSessionId,
                before = pendingBefore,
                after = pendingAfter,
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
    CarDiagRoute.SYMPTOM -> stringResource(R.string.nav_symptom) to Icons.Default.Search
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
