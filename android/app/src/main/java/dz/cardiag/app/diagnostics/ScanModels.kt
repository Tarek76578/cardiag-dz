package dz.cardiag.app.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class ScanDtc(
    val code: String,
    val description: String = "",
    val severity: String = "unknown",
    val symptoms: List<String> = emptyList(),
    val causes: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)

@Serializable
data class LivePid(
    val name: String,
    val value: String,
    val unit: String = ""
)

@Serializable
data class ScanResult(
    val sessionId: String,
    val vehicleId: String? = null,
    val vehicleName: String = "Unknown vehicle",
    val vin: String? = null,
    val ecu: String? = null,
    val timestampEpochMs: Long,
    val dtcs: List<ScanDtc> = emptyList(),
    val liveData: List<LivePid> = emptyList(),
    val source: String = "obd"
) {
    val hasFaults: Boolean get() = dtcs.isNotEmpty()
}

@Serializable
data class DiagnosticReport(
    val id: String,
    val scan: ScanResult,
    val diagnosisSummary: String? = null,
    val recommendations: List<String> = emptyList(),
    val createdAtEpochMs: Long
)

enum class ScanState { CONNECTING, SCANNING, PROCESSING, SUCCESS, ERROR, OFFLINE }
