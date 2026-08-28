package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEngineTest {
    @Test fun normalizesAndDeduplicatesCodes() {
        val result = DiagnosticEngine.analyze(DiagnosticInput(dtcCodes = listOf("p0301", "P0301", "bad")))
        assertEquals(1, result.size)
        assertEquals("P0301", result.single().code)
    }

    @Test fun communicationCodesRequireNetworkChecks() {
        val finding = DiagnosticEngine.analyze(DiagnosticInput(dtcCodes = listOf("U0100"))).single()
        assertEquals("critical", finding.severity)
        assertTrue(finding.tests.any { it.contains("CAN", ignoreCase = true) })
        assertTrue(finding.confidence in 0..100)
    }

    @Test fun engineNeverInventsRepairCertainty() {
        val finding = DiagnosticEngine.analyze(DiagnosticInput(dtcCodes = listOf("P0301"))).single()
        assertTrue(finding.repairGuidance.any { it.contains("Confirm", ignoreCase = true) })
    }
}
