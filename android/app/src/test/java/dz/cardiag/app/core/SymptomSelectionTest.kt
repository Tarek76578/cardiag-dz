package dz.cardiag.app.core

import dz.cardiag.app.SymptomCatalog
import dz.cardiag.app.SymptomCategoryId
import dz.cardiag.app.SymptomQuestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymptomSelectionTest {
    @Test fun categoryCatalogExposesMisfire() {
        val misfire = SymptomCatalog.entries.firstOrNull { it.id == "engine_misfire" }
        assertNotNull(misfire)
        assertEquals(SymptomCategoryId.ENGINE, misfire!!.category)
    }

    @Test fun brakeSymptomsAreInBrakeCategory() {
        val brakeSymptoms = SymptomCatalog.byCategory()[SymptomCategoryId.BRAKES].orEmpty()
        assertTrue(brakeSymptoms.isNotEmpty())
        assertTrue(brakeSymptoms.all { it.category == SymptomCategoryId.BRAKES })
    }

    @Test fun emissionsSymptomsIncludeDpf() {
        val dpf = SymptomCatalog.byCategory()[SymptomCategoryId.EMISSIONS].orEmpty()
            .firstOrNull { it.id == "em_dpf" }
        assertNotNull(dpf)
    }

    @Test fun smokeSymptomTriggersSmokeQuestion() {
        val questions = SymptomQuestions.forSymptoms(setOf("engine_smoke"))
        val ids = questions.map { it.idRes }
        // smoke-specific question expected
        assertTrue("Smoke symptom must produce the smoke-color question",
            ids.any { it == dz.cardiag.app.R.string.symptom_question_smoke })
    }

    @Test fun warningLightSymptomsTriggerWarningQuestion() {
        val questions = SymptomQuestions.forSymptoms(setOf("engine_check_engine", "brake_abs"))
        val ids = questions.map { it.idRes }
        assertTrue("Warning-light symptoms must produce the warning-lights question",
            ids.any { it == dz.cardiag.app.R.string.symptom_question_warning })
    }

    @Test fun emptySelectionProducesNoQuestions() {
        assertTrue(SymptomQuestions.forSymptoms(emptySet()).isEmpty())
    }

    @Test fun transmissionSymptomsTriggerLimpQuestion() {
        val questions = SymptomQuestions.forSymptoms(setOf("tr_shifting"))
        val ids = questions.map { it.idRes }
        // tr_shifting must trigger a when question (engine/transmission group)
        assertTrue("Transmission symptoms must produce the when question",
            ids.any { it == dz.cardiag.app.R.string.symptom_question_when })
    }
}
