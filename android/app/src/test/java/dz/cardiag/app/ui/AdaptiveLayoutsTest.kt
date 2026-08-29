package dz.cardiag.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutsTest {
    @Test fun compactWidthClassification() {
        val s = CarDiagWindowSize(CarDiagWindowSize.WidthClass.Compact, CarDiagWindowSize.HeightClass.Compact)
        assertEquals(true, s.isCompact)
        assertEquals(false, s.isMedium)
        assertEquals(false, s.isExpanded)
    }

    @Test fun mediumWidthClassification() {
        val s = CarDiagWindowSize(CarDiagWindowSize.WidthClass.Medium, CarDiagWindowSize.HeightClass.Medium)
        assertEquals(true, s.isMedium)
    }

    @Test fun expandedWidthClassification() {
        val s = CarDiagWindowSize(CarDiagWindowSize.WidthClass.Expanded, CarDiagWindowSize.HeightClass.Expanded)
        assertEquals(true, s.isExpanded)
    }
}
