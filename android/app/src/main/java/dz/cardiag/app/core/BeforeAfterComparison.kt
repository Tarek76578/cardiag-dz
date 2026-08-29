package dz.cardiag.app.core

import kotlinx.serialization.Serializable

/**
 * Canonical record for comparing vehicle state before and after a repair.
 * The comparison is computed locally from the two recorded snapshots so it
 * works offline; the cloud layer is only used to persist the snapshots.
 */
@Serializable
data class BeforeAfterSnapshot(
    val capturedAt: Long,
    val label: String,
    val dtcs: List<String> = emptyList(),
    val pendingDtcs: List<String> = emptyList(),
    val permanentDtcs: List<String> = emptyList(),
    val readinessReady: Boolean? = null,
    val milOn: Boolean? = null,
    val measurements: Map<String, Double> = emptyMap()
)

enum class BeforeAfterOutcome { IMPROVED, SAME, REGRESSED, INSUFFICIENT }

object BeforeAfterComparison {
    fun compare(before: BeforeAfterSnapshot, after: BeforeAfterSnapshot): BeforeAfterOutcome {
        val beforeFaults = before.dtcs.size + before.pendingDtcs.size + before.permanentDtcs.size
        val afterFaults = after.dtcs.size + after.pendingDtcs.size + after.permanentDtcs.size
        val readinessImproved = (before.readinessReady == false) && (after.readinessReady == true)
        val milImproved = (before.milOn == true) && (after.milOn == false)
        return when {
            afterFaults < beforeFaults || readinessImproved || milImproved -> BeforeAfterOutcome.IMPROVED
            afterFaults > beforeFaults -> BeforeAfterOutcome.REGRESSED
            afterFaults == beforeFaults -> BeforeAfterOutcome.SAME
            else -> BeforeAfterOutcome.INSUFFICIENT
        }
    }
}
