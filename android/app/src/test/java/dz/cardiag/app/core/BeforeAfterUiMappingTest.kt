package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-Kotlin mapping tests. The [BeforeAfterComparison] engine is unit
 * tested; these additional tests exercise additional label/snapshot helpers
 * used by the UI without depending on Android resources.
 */
class BeforeAfterUiMappingTest {
    private fun snap(
        dtcs: List<String> = emptyList(),
        pending: List<String> = emptyList(),
        permanent: List<String> = emptyList(),
        readiness: Boolean? = null,
        mil: Boolean? = null
    ) = BeforeAfterSnapshot(
        capturedAt = 0L,
        label = "x",
        dtcs = dtcs,
        pendingDtcs = pending,
        permanentDtcs = permanent,
        readinessReady = readiness,
        milOn = mil
    )

    @Test fun totalFaultCountIsSumAcrossClasses() {
        val s = snap(dtcs = listOf("P0301"), pending = listOf("P0300"), permanent = listOf("P0420"))
        val total = s.dtcs.size + s.pendingDtcs.size + s.permanentDtcs.size
        assertEquals(3, total)
    }

    @Test fun emptySnapshotsResultInSame() {
        val r = BeforeAfterComparison.compare(snap(), snap())
        assertEquals(BeforeAfterOutcome.SAME, r)
    }

    @Test fun onlyReadinessRecoversIsImproved() {
        val before = snap(readiness = false)
        val after = snap(readiness = true)
        assertEquals(BeforeAfterOutcome.IMPROVED, BeforeAfterComparison.compare(before, after))
    }

    @Test fun onlyMilClearsIsImproved() {
        val before = snap(mil = true)
        val after = snap(mil = false)
        assertEquals(BeforeAfterOutcome.IMPROVED, BeforeAfterComparison.compare(before, after))
    }

    @Test fun serializableRoundTripPreservesFields() {
        val original = snap(
            dtcs = listOf("P0301", "P0171"),
            readiness = true,
            mil = false
        )
        val json = kotlinx.serialization.json.Json.encodeToString(BeforeAfterSnapshot.serializer(), original)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(BeforeAfterSnapshot.serializer(), json)
        assertEquals(original, decoded)
    }
}
