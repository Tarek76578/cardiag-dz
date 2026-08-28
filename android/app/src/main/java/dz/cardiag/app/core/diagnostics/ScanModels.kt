package dz.cardiag.app.core.diagnostics

import kotlinx.serialization.Serializable

@Serializable
enum class ScanPhase { CONNECTING, SCANNING, PROCESSING, SUCCESS, OFFLINE, ERROR }

@Serializable
data class ScanDtc(
    val code: String,
    val severity: String = "unknown",
    val description: String? = null,
    val symptoms: List<String> = emptyList(),
    val causes: List<String> = emptyList(),
    val diagnosticSteps: List<String> = emptyList(),
    val repair: List<String> = emptyList()
)

@Serializable
data class LivePid(val name: String, val value: String, val unit: String? = null)

@Serializable
data class ScanResult(
    val sessionId: String,
    val vehicleId: String? = null,
    val vehicleName: String? = null,
    val vin: String? = null,
    val ecu: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null,
    val phase: ScanPhase = ScanPhase.CONNECTING,
    val dtcs: List<ScanDtc> = emptyList(),
    val liveData: List<LivePid> = emptyList(),
    val error: String? = null
) {
    val hasFaults: Boolean get() = dtcs.isNotEmpty()
}

@Serializable
data class DiagnosticReport(
    val reportId: String,
    val sessionId: String,
    val vehicleId: String? = null,
    val vehicleName: String? = null,
    val engine: String? = null,
    val ecu: String? = null,
    val vin: String? = null,
    val createdAt: Long,
    val dtcs: List<ScanDtc> = emptyList(),
    val liveData: List<LivePid> = emptyList(),
    val diagnosis: String? = null,
    val recommendations: List<String> = emptyList()
)
