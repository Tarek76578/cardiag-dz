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
        assertTrue(finding.confidence in 0.0..1.0)
    }

    @Test fun engineNeverInventsRepairCertainty() {
        val finding = DiagnosticEngine.analyze(DiagnosticInput(dtcCodes = listOf("P0301"))).single()
        assertTrue(finding.recommendations.any { it.contains("Confirm", ignoreCase = true) })
    }

    @Test fun recommendationsAndBoundsHoldAcrossMajorCodeFamilies() {
        val codes = listOf("P0301", "P0171", "U0100", "B0100", "C0030")
        codes.forEach { code ->
            val finding = DiagnosticEngine.analyze(DiagnosticInput(dtcCodes = listOf(code))).singleOrNull()
                ?: throw AssertionError("No result for $code")
            assertTrue("[$code] recommendations must not be empty", finding.recommendations.isNotEmpty())
            assertTrue("[$code] confidence must be bounded", finding.confidence in 0.0..1.0)
            assertTrue("[$code] score must be bounded", finding.score in 0..100)
            assertTrue("[$code] must have tests", finding.tests.isNotEmpty())
        }
    }

    @Test fun unknownDtcCodeIsFilteredOut() {
        val result = DiagnosticEngine.analyze(DiagnosticInput(dtcCodes = listOf("BAD", "P0301", "UNKNOWN")))
        assertEquals("Invalid codes are filtered", 1, result.size)
        assertEquals("P0301", result.single().code)
    }

    @Test fun normalLiveDataDoesNotIncreaseDiagnosticScore() {
        val knowledge = DiagnosticKnowledge(
            title = "Test",
            reason = "Test",
            severity = "medium",
            confidence = 0.5,
            tests = emptyList(),
            recommendations = emptyList(),
            liveDataRanges = mapOf("RPM" to 600.0..800.0)
        )

        val withoutLiveData = DiagnosticEngine.evaluate(knowledge)
        val withNormalLiveData = DiagnosticEngine.evaluate(
            knowledge,
            liveData = mapOf("RPM" to 700.0)
        )

        assertEquals(withoutLiveData.score, withNormalLiveData.score)
        assertEquals(withoutLiveData.confidence, withNormalLiveData.confidence, 0.0001)
    }

    @Test fun outOfRangeLiveDataAddsDiagnosticEvidence() {
        val knowledge = DiagnosticKnowledge(
            title = "Test",
            reason = "Test",
            severity = "medium",
            confidence = 0.5,
            tests = emptyList(),
            recommendations = emptyList(),
            liveDataRanges = mapOf("RPM" to 600.0..800.0)
        )

        val baseline = DiagnosticEngine.evaluate(knowledge)
        val abnormal = DiagnosticEngine.evaluate(
            knowledge,
            liveData = mapOf("RPM" to 1200.0)
        )

        assertTrue(abnormal.score > baseline.score)
        assertTrue(abnormal.confidence > baseline.confidence)
    }

    @Test fun liveDataWithoutConfiguredRangeDoesNotAddEvidence() {
        val knowledge = DiagnosticKnowledge(
            title = "Test",
            reason = "Test",
            severity = "medium",
            confidence = 0.5,
            tests = emptyList(),
            recommendations = emptyList(),
            liveDataRanges = emptyMap()
        )

        val baseline = DiagnosticEngine.evaluate(knowledge)
        val withUnboundedLiveData = DiagnosticEngine.evaluate(
            knowledge,
            liveData = mapOf("RPM" to 700.0)
        )

        assertEquals(baseline.score, withUnboundedLiveData.score)
        assertEquals(baseline.confidence, withUnboundedLiveData.confidence, 0.0001)
    }
}
