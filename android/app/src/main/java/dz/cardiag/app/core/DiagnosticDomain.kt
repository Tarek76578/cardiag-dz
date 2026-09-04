package dz.cardiag.app.core

/** Canonical DTC knowledge model consumed by the deterministic diagnostic engine. */
data class DtcKnowledge(
    val code: String,
    val severity: String,
    val symptoms: List<String> = emptyList(),
    val causes: List<String> = emptyList(),
    val tests: List<String> = emptyList(),
    val repairs: List<String> = emptyList()
)

/** One live PID observation with an optional expected operating range. */
data class LivePidValue(
    val pid: String,
    val value: Double,
    val unit: String? = null,
    val min: Double? = null,
    val max: Double? = null
)

/** Public result of deterministic diagnostic evaluation. */
data class DiagnosticFinding(
    val code: String,
    val severity: String,
    val score: Int,
    val confidence: Double,
    val causes: List<String>,
    val tests: List<String>,
    val recommendations: List<String>
)
