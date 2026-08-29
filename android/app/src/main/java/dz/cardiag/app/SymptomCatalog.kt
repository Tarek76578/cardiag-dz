package dz.cardiag.app

/**
 * Canonical symptom catalog. The category enum maps to a localized display
 * label and to a curated set of vehicle-specific symptoms. The catalog is
 * intentionally hard-coded and offline-safe; the AI assistant remains the
 * primary source for richer interpretation.
 */
enum class SymptomCategoryId(val labelRes: Int) {
    ENGINE(R.string.symptom_category_engine),
    ELECTRICAL(R.string.symptom_category_electrical),
    BRAKES(R.string.symptom_category_brakes),
    AIRBAG(R.string.symptom_category_airbag),
    TRANSMISSION(R.string.symptom_category_transmission),
    COOLING(R.string.symptom_category_cooling),
    EMISSIONS(R.string.symptom_category_emissions),
    OTHER(R.string.symptom_category_other)
}

data class SymptomEntry(
    val id: String,
    val labelRes: Int,
    val category: SymptomCategoryId
)

object SymptomCatalog {
    val entries: List<SymptomEntry> = listOf(
        // Engine
        SymptomEntry("engine_no_start", R.string.symptom_specific_engine, SymptomCategoryId.ENGINE),
        SymptomEntry("engine_hard_start", R.string.symptom_specific_hard_start, SymptomCategoryId.ENGINE),
        SymptomEntry("engine_loss_power", R.string.symptom_specific_loss_power, SymptomCategoryId.ENGINE),
        SymptomEntry("engine_hesitation", R.string.symptom_specific_hesitation, SymptomCategoryId.ENGINE),
        SymptomEntry("engine_rough_idle", R.string.symptom_specific_rough_idle, SymptomCategoryId.ENGINE),
        SymptomEntry("engine_misfire", R.string.symptom_specific_misfire, SymptomCategoryId.ENGINE),
        SymptomEntry("engine_high_fuel", R.string.symptom_specific_high_fuel, SymptomCategoryId.ENGINE),
        SymptomEntry("engine_smoke", R.string.symptom_specific_smoke, SymptomCategoryId.ENGINE),
        SymptomEntry("engine_overheat", R.string.symptom_specific_overheat, SymptomCategoryId.ENGINE),
        SymptomEntry("engine_check_engine", R.string.symptom_specific_check_engine, SymptomCategoryId.ENGINE),
        // Electrical
        SymptomEntry("elec_battery", R.string.symptom_specific_battery, SymptomCategoryId.ELECTRICAL),
        SymptomEntry("elec_starter", R.string.symptom_specific_starter, SymptomCategoryId.ELECTRICAL),
        SymptomEntry("elec_electrical", R.string.symptom_specific_electrical, SymptomCategoryId.ELECTRICAL),
        SymptomEntry("elec_lights", R.string.symptom_specific_lights, SymptomCategoryId.ELECTRICAL),
        // Brakes
        SymptomEntry("brake_abs", R.string.symptom_specific_abs, SymptomCategoryId.BRAKES),
        SymptomEntry("brake_pedal", R.string.symptom_specific_brake_pedal, SymptomCategoryId.BRAKES),
        SymptomEntry("brake_vibration", R.string.symptom_specific_brake_vibration, SymptomCategoryId.BRAKES),
        // Airbag
        SymptomEntry("airbag_srs", R.string.symptom_specific_srs, SymptomCategoryId.AIRBAG),
        // Transmission
        SymptomEntry("tr_shifting", R.string.symptom_specific_transmission, SymptomCategoryId.TRANSMISSION),
        SymptomEntry("tr_at_warning", R.string.symptom_specific_at_warning, SymptomCategoryId.TRANSMISSION),
        // Cooling
        SymptomEntry("cool_loss", R.string.symptom_specific_coolant_loss, SymptomCategoryId.COOLING),
        SymptomEntry("cool_fan", R.string.symptom_specific_fan, SymptomCategoryId.COOLING),
        SymptomEntry("cool_overheat", R.string.symptom_specific_overheat, SymptomCategoryId.COOLING),
        // Emissions
        SymptomEntry("em_dpf", R.string.symptom_specific_dpf, SymptomCategoryId.EMISSIONS),
        SymptomEntry("em_regen", R.string.symptom_specific_regen, SymptomCategoryId.EMISSIONS),
        SymptomEntry("em_egr", R.string.symptom_specific_egr, SymptomCategoryId.EMISSIONS),
        SymptomEntry("em_excessive", R.string.symptom_specific_emissions, SymptomCategoryId.EMISSIONS),
        SymptomEntry("other_free_text", R.string.symptom_free_text, SymptomCategoryId.OTHER)
    )

    fun byCategory(): Map<SymptomCategoryId, List<SymptomEntry>> =
        entries.groupBy { it.category }
}

/**
 * Lightweight contextual question that the symptom screen can ask the user
 * once the primary symptoms are selected. The questions are deterministic
 * and never ask for personal or identifying data.
 */
data class SymptomQuestion(
    val idRes: Int,
    val options: List<Pair<Int, String>>
)

object SymptomQuestions {
    fun forSymptoms(symptomIds: Set<String>): List<SymptomQuestion> {
        val out = mutableListOf<SymptomQuestion>()
        if (symptomIds.isNotEmpty()) {
            out += SymptomQuestion(
                idRes = R.string.symptom_question_constant,
                options = listOf(
                    R.string.symptom_opt_constant_mild to "constant_mild",
                    R.string.symptom_opt_constant_moderate to "constant_moderate",
                    R.string.symptom_opt_intermittent to "intermittent"
                )
            )
        }
        if (symptomIds.any { it.startsWith("engine_") || it.startsWith("tr_") }) {
            out += SymptomQuestion(
                idRes = R.string.symptom_question_when,
                options = listOf(
                    R.string.symptom_opt_cold_start to "cold_start",
                    R.string.symptom_opt_warm to "warm",
                    R.string.symptom_opt_acceleration to "acceleration",
                    R.string.symptom_opt_idle to "idle"
                )
            )
        }
        if (symptomIds.contains("engine_smoke")) {
            out += SymptomQuestion(
                idRes = R.string.symptom_question_smoke,
                options = listOf(
                    R.string.symptom_opt_smoke_white to "white",
                    R.string.symptom_opt_smoke_blue to "blue",
                    R.string.symptom_opt_smoke_black to "black"
                )
            )
        }
        if (symptomIds.any { it.contains("check") || it.contains("abs") || it.contains("at_warning") || it.contains("dpf") || it.contains("srs") }) {
            out += SymptomQuestion(
                idRes = R.string.symptom_question_warning,
                options = listOf(
                    R.string.symptom_opt_warning_yes to "yes",
                    R.string.symptom_opt_warning_no to "no"
                )
            )
        }
        if (symptomIds.any { it.startsWith("engine_") }) {
            out += SymptomQuestion(
                idRes = R.string.symptom_question_limp,
                options = listOf(
                    R.string.symptom_opt_yes to "yes",
                    R.string.symptom_opt_no to "no"
                )
            )
        }
        return out
    }
}
