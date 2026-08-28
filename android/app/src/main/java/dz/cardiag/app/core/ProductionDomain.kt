package dz.cardiag.app.core

import kotlinx.serialization.Serializable

/** Canonical product flow: Vehicle -> OBD -> Scan -> DTC/Live Data -> Diagnosis -> Report -> History. */
@Serializable
data class VehicleRecord(
    val id: String,
    val make: String,
    val model: String,
    val year: Int? = null,
    val engine: String? = null,
    val ecu: String? = null,
    val vin: String? = null,
    val isActive: Boolean = false,
    val lastScanAt: Long? = null,
    val healthScore: Int? = null
)

@Serializable
data class ObdConnectionState(
    val connected: Boolean = false,
    val adapterName: String? = null,
    val protocol: String? = null,
    val vin: String? = null,
    val ecu: String? = null,
    val error: String? = null
)

@Serializable
data class ReadinessStatus(
    val milOn: Boolean? = null,
    val monitorsReady: Boolean? = null,
    val raw: String = ""
)

@Serializable
data class DiagnosticFinding(
    val code: String,
    val severity: String,
    val score: Int,
    val confidence: Double,
    val causes: List<String> = emptyList(),
    val tests: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)

@Serializable
data class LivePidValue(
    val pid: String,
    val label: String,
    val value: Double,
    val unit: String,
    val min: Double? = null,
    val max: Double? = null,
    val timestamp: Long
)

@Serializable
data class FreezeFrame(
    val dtc: String,
    val values: List<LivePidValue> = emptyList()
)

@Serializable
data class DtcKnowledge(
    val code: String,
    val title: String,
    val severity: String = "unknown",
    val symptoms: List<String> = emptyList(),
    val causes: List<String> = emptyList(),
    val tests: List<String> = emptyList(),
    val repairs: List<String> = emptyList(),
    val relatedCodes: List<String> = emptyList()
)

@Serializable
data class ScanSession(
    val id: String,
    val vehicleId: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val ecu: String? = null,
    val dtcs: List<String> = emptyList(),
    val liveData: List<LivePidValue> = emptyList(),
    val freezeFrames: List<FreezeFrame> = emptyList(),
    val readiness: ReadinessStatus? = null,
    val findings: List<DiagnosticFinding> = emptyList(),
    val offline: Boolean = false
)

@Serializable
data class DiagnosticReport(
    val id: String,
    val sessionId: String,
    val vehicle: VehicleRecord,
    val createdAt: Long,
    val dtcs: List<String>,
    val liveData: List<LivePidValue>,
    val findings: List<DiagnosticFinding>,
    val recommendations: List<String>,
    val offline: Boolean = false
)

enum class ScanStage { CONNECTING, SCANNING, PROCESSING, SUCCESS, NO_FAULTS, ERROR, OFFLINE }

enum class SyncState { LOCAL_ONLY, PENDING, SYNCED, CONFLICT, FAILED }

enum class AppLanguage { ARABIC, FRENCH }
enum class MeasurementUnit { METRIC, IMPERIAL }

interface ScanRepositoryContract {
    suspend fun save(session: ScanSession): SyncState
    suspend fun get(id: String): ScanSession?
    suspend fun list(vehicleId: String? = null): List<ScanSession>
}

interface VehicleRepositoryContract {
    suspend fun list(): List<VehicleRecord>
    suspend fun active(): VehicleRecord?
    suspend fun save(vehicle: VehicleRecord)
    suspend fun delete(id: String)
    suspend fun setActive(id: String)
}

/** Unified search contract; implementations can combine local and remote indexes. */
interface GlobalSearchContract {
    suspend fun search(query: String): SearchResults
}

@Serializable
data class SearchResults(
    val vehicles: List<VehicleRecord> = emptyList(),
    val dtcs: List<DtcKnowledge> = emptyList(),
    val parts: List<String> = emptyList(),
    val services: List<String> = emptyList()
)

@Serializable
data class UserPreferences(
    val language: AppLanguage = AppLanguage.FRENCH,
    val darkTheme: Boolean = true,
    val units: MeasurementUnit = MeasurementUnit.METRIC,
    val onboardingComplete: Boolean = false,
    val notificationsEnabled: Boolean = true
)
