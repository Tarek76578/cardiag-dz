package dz.cardiag.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanModelsTest {
    @Test fun emptyScanHasNoFaults() {
        val result = ScanResult("session-1", vehicleName = "Fiat Doblo", timestampEpochMs = 1L)
        assertFalse(result.hasFaults)
    }

    @Test fun dtcScanHasFaults() {
        val result = ScanResult("session-2", dtcs = listOf(ScanDtc("P0301")), timestampEpochMs = 1L)
        assertTrue(result.hasFaults)
    }

    @Test fun allScanStatesExist() {
        val expected = setOf(
            ScanState.CONNECTING, ScanState.SCANNING, ScanState.PROCESSING,
            ScanState.SUCCESS, ScanState.ERROR, ScanState.OFFLINE
        )
        assertTrue("All required scan states are defined", expected.all { it in ScanState.entries })
        assertEquals("Enum is complete (no missing states)", expected.size, ScanState.entries.size)
    }
}
