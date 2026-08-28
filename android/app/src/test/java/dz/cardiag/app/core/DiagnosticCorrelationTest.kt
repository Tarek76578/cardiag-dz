package dz.cardiag.app.core

import org.junit.Assert.*
import org.junit.Test

class DiagnosticCorrelationTest {

    @Test fun correlatesMultipleCodes() {
        val r = DiagnosticCorrelation.correlateAll(
            listOf("P0301", "P0171"),
            listOf(CorrelationObservation("10", 1.0, "g/s"))
        )
        assertTrue(r.findings.any { it.title.contains("Misfire") })
        assertFalse(r.nextBestTests.isEmpty())
    }

    @Test fun ranksNextBestTest() {
        val r = DiagnosticCorrelation.correlateAll(listOf("P0301"), emptyList())
        assertTrue(r.nextBestTests.first().priority >= 90)
    }

    @Test fun catalystCodeOnColdEngineEmitsWarmupFinding() {
        val r = DiagnosticCorrelation.correlateAll(
            listOf("P0420"),
            listOf(CorrelationObservation("05", 45.0, "°C"))
        )
        assertTrue(r.findings.any { it.title.contains("warm-up", ignoreCase = true) })
    }

    @Test fun lowMafSupportsUnmeteredAirOnLeanCode() {
        val r = DiagnosticCorrelation.correlateAll(
            listOf("P0171"),
            listOf(CorrelationObservation("10", 1.5, "g/s"))
        )
        assertTrue(r.findings.any { it.title.contains("unmetered", ignoreCase = true) })
    }

    @Test fun highMapOnLeanCodeEmitsMapFinding() {
        val r = DiagnosticCorrelation.correlateAll(
            listOf("P0174"),
            listOf(CorrelationObservation("0B", 95.0, "kPa"))
        )
        assertTrue(r.findings.any { it.title.contains("MAP", ignoreCase = true) })
    }

    @Test fun lowRpmMisfireFiringEmitsIdleFinding() {
        val r = DiagnosticCorrelation.correlateAll(
            listOf("P0301"),
            listOf(CorrelationObservation("0C", 450.0, "rpm"))
        )
        assertTrue(r.findings.any { it.title.contains("idle", ignoreCase = true) })
    }

    @Test fun coldEngineDuringMisfireEmitsWarmupHint() {
        val r = DiagnosticCorrelation.correlateAll(
            listOf("P0302"),
            listOf(CorrelationObservation("05", 40.0, "°C"))
        )
        assertTrue(r.findings.any { it.title.contains("Cold", ignoreCase = true) })
    }

    @Test fun multipleDtcCodesProduceCrossCorrelationTest() {
        val r = DiagnosticCorrelation.correlateAll(listOf("P0301", "P0302"), emptyList())
        assertTrue(r.nextBestTests.any { it.test.contains("correlation", ignoreCase = true) })
    }

    @Test fun emptyInputsReturnEmptyResults() {
        val r = DiagnosticCorrelation.correlateAll(emptyList(), emptyList())
        assertTrue(r.findings.isEmpty())
        assertTrue(r.nextBestTests.isEmpty())
    }

    @Test fun unknownDtcProducesNoFinding() {
        val r = DiagnosticCorrelation.correlateAll(listOf("P9999"), emptyList())
        assertTrue(r.findings.isEmpty())
    }

    @Test fun findingsAreDeduplicatedByTitle() {
        val r = DiagnosticCorrelation.correlateAll(
            listOf("P0300"),
            listOf(CorrelationObservation("05", 35.0, "°C"))
        )
        val titles = r.findings.map { it.title }
        assertEquals(titles.distinct().size, titles.size)
    }

    @Test fun findingsAreSortedByConfidenceDescending() {
        val r = DiagnosticCorrelation.correlateAll(
            listOf("P0301", "P0171"),
            listOf(CorrelationObservation("10", 1.0, "g/s"), CorrelationObservation("0B", 95.0, "kPa"))
        )
        val confidences = r.findings.map { it.confidence }
        assertEquals(confidences.sortedDescending(), confidences)
    }
}
