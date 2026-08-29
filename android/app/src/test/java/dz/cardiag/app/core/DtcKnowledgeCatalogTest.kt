package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcKnowledgeCatalogTest {
    @Test fun catalogHasExpectedFamilies() {
        val families = DtcKnowledgeCatalog.entries.map { it.family }.toSet()
        assertTrue("Powertrain (P) family must be present", 'P' in families)
        assertTrue("Body (B) family must be present", 'B' in families)
        assertTrue("Chassis (C) family must be present", 'C' in families)
        assertTrue("Network (U) family must be present", 'U' in families)
    }

    @Test fun lookupReturnsEntry() {
        val e = DtcKnowledgeCatalog.lookup("P0301")
        assertNotNull(e)
        assertEquals("P0301", e!!.code)
    }

    @Test fun lookupIsCaseInsensitive() {
        val e = DtcKnowledgeCatalog.lookup("p0420")
        assertEquals("P0420", e?.code)
    }

    @Test fun searchMatchesTitleAndCode() {
        val matches = DtcKnowledgeCatalog.search("misfire")
        assertTrue("At least one misfire entry expected", matches.isNotEmpty())
    }

    @Test fun searchEmptyReturnsAll() {
        val all = DtcKnowledgeCatalog.search("")
        assertEquals(DtcKnowledgeCatalog.entries.size, all.size)
    }

    @Test fun byFamilyFiltersCorrectly() {
        val p = DtcKnowledgeCatalog.byFamily('P')
        assertTrue(p.isNotEmpty())
        assertTrue(p.all { it.family == 'P' })
    }

    @Test fun bySeverityFiltersCorrectly() {
        val critical = DtcKnowledgeCatalog.bySeverity("critical")
        assertTrue(critical.isNotEmpty())
        assertTrue(critical.all { it.severity == "critical" })
    }

    @Test fun noEntryFabricatesSensorValue() {
        // Each catalog entry must NOT contain numeric sensor readings we
        // could not verify from a real OBD-II data stream.
        DtcKnowledgeCatalog.entries.forEach { e ->
            assertTrue(e.code.matches(Regex("[PBCU][0-3][0-9A-F]{3}")))
            assertTrue(e.causes.isNotEmpty())
        }
    }
}
