package dz.cardiag.app.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStateMachineTest {
    @Test fun transitionsToSuccessWithFaults() {
        val initial = ScanStateMachine.begin()
        assertEquals(ScanPhase.CONNECTING, initial.phase)
        val success = ScanStateMachine.success(
            ScanStateMachine.processing(ScanStateMachine.connected(initial)),
            listOf(ScanDtc("P0301", severity = "high"))
        )
        assertEquals(ScanPhase.SUCCESS, success.phase)
        assertTrue(success.hasFaults)
        assertNotNull(success.completedAt)
    }

    @Test fun offlineAndFailureKeepExplicitState() {
        val initial = ScanStateMachine.begin()
        assertEquals(ScanPhase.OFFLINE, ScanStateMachine.offline(initial, "No internet").phase)
        assertEquals(ScanPhase.ERROR, ScanStateMachine.failure(initial, "Bluetooth unavailable").phase)
    }

    @Test fun noFaultsIsExplicitlyRepresented() {
        val result = ScanStateMachine.success(ScanStateMachine.begin(), emptyList())
        assertFalse(result.hasFaults)
        assertTrue(result.dtcs.isEmpty())
    }

    @Test fun reportCopiesScanContext() {
        val scan = ScanStateMachine.success(
            ScanStateMachine.begin().copy(vehicleId = "v1", vehicleName = "Fiat Doblò", vin = "VIN123", ecu = "ECU"),
            listOf(ScanDtc("P0301")),
            listOf(LivePid("RPM", "800", "rpm"))
        )
        val report = DiagnosticReportFactory.fromScan(scan, engine = "1.6 HDi")
        assertEquals("v1", report.vehicleId)
        assertEquals("VIN123", report.vin)
        assertEquals(1, report.dtcs.size)
        assertEquals(1, report.liveData.size)
    }
}
