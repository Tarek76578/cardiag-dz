package dz.cardiag.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymptomCatalogTest {
    @Test fun catalogCoversAllCategories() {
        val grouped = SymptomCatalog.byCategory()
        SymptomCategoryId.values().forEach { c ->
            assertTrue("Category $c must have at least one symptom", grouped[c].orEmpty().isNotEmpty())
        }
    }

    @Test fun entriesAreUnique() {
        val ids = SymptomCatalog.entries.map { it.id }
        assertEquals("Symptom IDs must be unique", ids.distinct().size, ids.size)
    }

    @Test fun engineCategoryIncludesMisfire() {
        val entries = SymptomCatalog.byCategory()[SymptomCategoryId.ENGINE].orEmpty()
        assertTrue(entries.any { it.id == "engine_misfire" })
    }

    @Test fun questionsGrowWithSelectedSymptoms() {
        val empty = SymptomQuestions.forSymptoms(emptySet())
        val misfire = SymptomQuestions.forSymptoms(setOf("engine_misfire"))
        val smoke = SymptomQuestions.forSymptoms(setOf("engine_smoke"))
        assertTrue("Engine misfire should trigger at least one question", misfire.size >= 1)
        assertTrue("Smoke symptom should produce at least one question", smoke.isNotEmpty())
        assertTrue(empty.isEmpty())
    }
}
