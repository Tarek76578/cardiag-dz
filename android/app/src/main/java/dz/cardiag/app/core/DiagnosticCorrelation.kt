package dz.cardiag.app.core

import kotlinx.serialization.Serializable

@Serializable
data class CorrelationObservation(
    val pid: String,
    val value: Double,
    val unit: String? = null,
    val min: Double? = null,
    val max: Double? = null
)

@Serializable
data class CorrelationFinding(
    val title: String,
    val reason: String,
    val severity: String,
    val confidence: Int,
    val supportingPids: List<String>
)

/** Deterministic first-pass correlation. AI/edge-function diagnosis remains authoritative. */
object DiagnosticCorrelation {
    fun correlate(dtc: String, observations: List<CorrelationObservation>): List<CorrelationFinding> {
        val v = observations.associateBy { it.pid.uppercase() }
        fun value(pid: String) = v[pid]?.value
        val findings = mutableListOf<CorrelationFinding>()
        when (dtc.uppercase()) {
            "P0300", "P0301", "P0302", "P0303", "P0304" -> {
                val rpm = value("0C")
                val maf = value("10")
                val coolant = value("05")
                if (maf != null && maf < 2.0) findings += CorrelationFinding("Low MAF signal", "MAF is unusually low at the sampled point; inspect intake leaks, MAF contamination and wiring.", "medium", 72, listOf("10"))
                if (coolant != null && coolant < 60.0) findings += CorrelationFinding("Cold-engine condition", "Coolant temperature is below normal operating temperature; warm the engine before judging misfire behavior.", "low", 65, listOf("05"))
                if (rpm != null && rpm < 500.0) findings += CorrelationFinding("Unstable/low idle speed", "RPM is below the expected idle region; inspect air/fuel delivery and idle control.", "medium", 68, listOf("0C"))
            }
            "P0171", "P0174" -> {
                val maf = value("10")
                val map = value("0B")
                if (maf != null && maf < 2.0) findings += CorrelationFinding("Possible unmetered-air condition", "Low MAF can support an intake leak or restricted/contaminated airflow measurement.", "medium", 70, listOf("10"))
                if (map != null && map > 90.0) findings += CorrelationFinding("High MAP at sampled point", "MAP is high for a lightly loaded engine; compare against throttle/load and check for vacuum/boost abnormalities.", "medium", 62, listOf("0B"))
            }
            "P0420", "P0430" -> {
                val coolant = value("05")
                if (coolant != null && coolant < 70.0) findings += CorrelationFinding("Engine not fully warm", "Catalyst efficiency should be assessed at operating temperature; repeat after warm-up.", "low", 80, listOf("05"))
            }
        }
        return findings.sortedByDescending { it.confidence }
    }
}
