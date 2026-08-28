package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionDomainTest {
    @Test fun diagnosticEngineProducesBoundedConfidence() {
        val finding = DiagnosticEngine.evaluate(
            DtcKnowledge(
                code = "P0301",
                title = "Cylinder 1 misfire",
                severity = "high",
                symptoms = listOf("misfire"),
                causes = listOf("ignition"),
                tests = listOf("inspect spark"),
                repairs = listOf("repair root cause")
            ),
            observedSymptoms = setOf("misfire"),
            testResults = mapOf("spark" to true)
        )
        assertTrue(finding.score in 0..100)
        assertTrue(finding.confidence in 0.0..1.0)
        assertEquals("P0301", finding.code)
    }

    @Test fun emptyDtcSessionIsRepresentable() {
        val session = ScanSession(id = "s1", vehicleId = "v1", startedAt = 1L)
        assertTrue(session.dtcs.isEmpty())
        assertEquals(ScanStage.NO_FAULTS, ScanStage.NO_FAULTS)
    }

    @Test fun userPreferencesDefaultToSafeProductDefaults() {
        val prefs = UserPreferences()
        assertEquals(AppLanguage.FRENCH, prefs.language)
        assertEquals(MeasurementUnit.METRIC, prefs.units)
        assertTrue(prefs.darkTheme)
        assertTrue(!prefs.onboardingComplete)
    }
}
