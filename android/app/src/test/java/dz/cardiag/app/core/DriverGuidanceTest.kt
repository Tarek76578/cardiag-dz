package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverGuidanceTest {
    @Test
    fun `no DTCs and MIL off is YES in French`() {
        val g = DriverGuidanceEngine.evaluate(
            dtcCount = 0, pendingCount = 0, permanentCount = 0,
            milOn = false, readinessReady = true, language = "fr"
        )
        assertEquals(CanDriveVerdict.YES, g.canDrive)
        assertTrue(g.nextSteps.any { it.contains("continuer à rouler", true) })
    }

    @Test
    fun `critical DTC means NO in French`() {
        val g = DriverGuidanceEngine.evaluate(
            dtcCount = 1, pendingCount = 0, permanentCount = 0,
            milOn = true, readinessReady = false,
            severities = listOf("critical"),
            language = "fr"
        )
        assertEquals(CanDriveVerdict.NO, g.canDrive)
        assertTrue(g.nextSteps.any { it.contains("Garez", true) })
    }

    @Test
    fun `MIL on with non critical DTCs is CAUTION`() {
        val g = DriverGuidanceEngine.evaluate(
            dtcCount = 1, pendingCount = 0, permanentCount = 0,
            milOn = true, readinessReady = false,
            severities = listOf("warning"),
            language = "fr"
        )
        assertEquals(CanDriveVerdict.CAUTION, g.canDrive)
    }

    @Test
    fun `Arabic responses do not contain Latin script`() {
        val g = DriverGuidanceEngine.evaluate(
            dtcCount = 0, pendingCount = 0, permanentCount = 0,
            milOn = false, readinessReady = true, language = "ar"
        )
        assertEquals(CanDriveVerdict.YES, g.canDrive)
        // The first next step should be in Arabic.
        val first = g.nextSteps.first()
        assertTrue("Expected Arabic text, got: $first", first.any { it.code in 0x0600..0x06FF })
    }

    @Test
    fun `readiness not ready alone is caution`() {
        val g = DriverGuidanceEngine.evaluate(
            dtcCount = 0, pendingCount = 0, permanentCount = 0,
            milOn = false, readinessReady = false, language = "fr"
        )
        assertEquals(CanDriveVerdict.CAUTION, g.canDrive)
    }

    @Test
    fun `no DTCs but no MIL reading is UNKNOWN`() {
        val g = DriverGuidanceEngine.evaluate(
            dtcCount = 0, pendingCount = 0, permanentCount = 0,
            milOn = null, readinessReady = true, language = "fr"
        )
        // Without an explicit MIL reading we cannot claim a clean bill of health.
        assertEquals(CanDriveVerdict.UNKNOWN, g.canDrive)
    }
}
