package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcBrowseFilterTest {
    @Test fun emptyQueryReturnsAll() {
        val r = DtcBrowseFilter.apply(DtcKnowledgeCatalog.entries, "", null, null)
        assertEquals(DtcKnowledgeCatalog.entries.size, r.size)
    }

    @Test fun familyFilterRestricts() {
        val r = DtcBrowseFilter.apply(DtcKnowledgeCatalog.entries, "", 'P', null)
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.family == 'P' })
    }

    @Test fun severityFilterRestricts() {
        val r = DtcBrowseFilter.apply(DtcKnowledgeCatalog.entries, "", null, "critical")
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.severity == "critical" })
    }

    @Test fun familyAndSeverityCombine() {
        val r = DtcBrowseFilter.apply(DtcKnowledgeCatalog.entries, "", 'P', "critical")
        assertTrue(r.all { it.family == 'P' && it.severity == "critical" })
    }

    @Test fun queryMatchesCode() {
        val r = DtcBrowseFilter.apply(DtcKnowledgeCatalog.entries, "p0301", null, null)
        assertTrue(r.any { it.code == "P0301" })
    }

    @Test fun queryMatchesTitle() {
        val r = DtcBrowseFilter.apply(DtcKnowledgeCatalog.entries, "misfire", null, null)
        assertTrue(r.isNotEmpty())
    }
}
