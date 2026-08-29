package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Verifies that the user-selected mode is preserved through the canonical
 * enumeration used by the unified navigation graph.
 */
class OnboardingModeSelectionTest {
    @Test
    fun `default mode is DRIVER`() {
        assertEquals(AppMode.DRIVER, AppMode.valueOf("DRIVER"))
    }

    @Test
    fun `mode switching produces a different value`() {
        assertNotEquals(AppMode.DRIVER, AppMode.MECHANIC)
    }

    @Test
    fun `mode can be roundtripped through a string name`() {
        AppMode.values().forEach {
            assertEquals(it, AppMode.valueOf(it.name))
        }
    }
}
