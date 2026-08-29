package dz.cardiag.app.core

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the symptom question option labels and the before/after outcome
 * labels are deterministic and stable across the user-facing surfaces.
 */
class LocalizationConsistencyTest {
    @Test
    fun `driver guidance verdict names are stable`() {
        // The enum must always carry the same set of values, otherwise the
        // serialized history records would change meaning.
        assertEquals(4, CanDriveVerdict.values().size)
        assertTrue(CanDriveVerdict.values().contains(CanDriveVerdict.YES))
        assertTrue(CanDriveVerdict.values().contains(CanDriveVerdict.CAUTION))
        assertTrue(CanDriveVerdict.values().contains(CanDriveVerdict.NO))
        assertTrue(CanDriveVerdict.values().contains(CanDriveVerdict.UNKNOWN))
    }

    @Test
    fun `app mode values are stable`() {
        assertEquals(2, AppMode.values().size)
        assertTrue(AppMode.values().contains(AppMode.DRIVER))
        assertTrue(AppMode.values().contains(AppMode.MECHANIC))
    }

    @Test
    fun `before after outcomes are stable`() {
        // The before/after report relies on this set; the diagnostic report
        // factory would otherwise render an unknown label.
        assertEquals(4, BeforeAfterOutcome.values().size)
        assertTrue(BeforeAfterOutcome.values().contains(BeforeAfterOutcome.IMPROVED))
        assertTrue(BeforeAfterOutcome.values().contains(BeforeAfterOutcome.SAME))
        assertTrue(BeforeAfterOutcome.values().contains(BeforeAfterOutcome.REGRESSED))
        assertTrue(BeforeAfterOutcome.values().contains(BeforeAfterOutcome.INSUFFICIENT))
    }
}
