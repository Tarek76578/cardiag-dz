package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BeforeAfterComparisonTest {
    private fun snap(dtcs: List<String>, readiness: Boolean? = null, mil: Boolean? = null) =
        BeforeAfterSnapshot(capturedAt = 0L, label = "x", dtcs = dtcs, readinessReady = readiness, milOn = mil)

    @Test fun improvementWhenFaultsDecrease() {
        val r = BeforeAfterComparison.compare(snap(listOf("P0301", "P0171")), snap(listOf("P0301")))
        assertEquals(BeforeAfterOutcome.IMPROVED, r)
    }

    @Test fun improvementWhenReadinessRecovers() {
        val r = BeforeAfterComparison.compare(snap(emptyList(), readiness = false), snap(emptyList(), readiness = true))
        assertEquals(BeforeAfterOutcome.IMPROVED, r)
    }

    @Test fun regressionWhenFaultsIncrease() {
        val r = BeforeAfterComparison.compare(snap(listOf("P0301")), snap(listOf("P0301", "P0420")))
        assertEquals(BeforeAfterOutcome.REGRESSED, r)
    }

    @Test fun sameWhenIdentical() {
        val r = BeforeAfterComparison.compare(snap(listOf("P0301")), snap(listOf("P0301")))
        assertEquals(BeforeAfterOutcome.SAME, r)
    }
}
