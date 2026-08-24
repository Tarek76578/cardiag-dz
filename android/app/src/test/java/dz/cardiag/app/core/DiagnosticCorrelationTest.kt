package dz.cardiag.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticCorrelationTest {
 @Test fun correlatesMultipleCodes(){val r=DiagnosticCorrelation.correlateAll(listOf("P0301","P0171"),listOf(CorrelationObservation("10",1.0,"g/s")));assertTrue(r.findings.any{it.title.contains("Misfire")});assertFalse(r.nextBestTests.isEmpty())}
 @Test fun ranksNextBestTest(){val r=DiagnosticCorrelation.correlateAll(listOf("P0301"),emptyList());assertTrue(r.nextBestTests.first().priority>=90)}
}
