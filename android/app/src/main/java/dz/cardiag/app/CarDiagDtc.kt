package dz.cardiag.app

import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server-side DTC record used by the unified DTC detail screen.
 *
 * The remote database column names are preserved through @SerialName to keep
 * the production code independent of the actual schema.
 */
@Serializable
data class DtcRecord(
    val id: String? = null,
    val code: String,
    val system: String? = null,
    val category: String? = null,
    val severity: String? = null,
    @SerialName("title_fr") val titleFr: String? = null,
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("description_fr") val descriptionFr: String? = null,
    @SerialName("description_ar") val descriptionAr: String? = null,
    @SerialName("causes_fr") val causesFr: String? = null,
    @SerialName("causes_ar") val causesAr: String? = null,
    @SerialName("diagnostic_steps_fr") val diagnosticStepsFr: String? = null,
    @SerialName("diagnostic_steps_ar") val diagnosticStepsAr: String? = null,
    @SerialName("repair_summary_fr") val repairSummaryFr: String? = null,
    @SerialName("repair_summary_ar") val repairSummaryAr: String? = null
)

/**
 * Resolves a DTC code to a [DtcRecord] from the remote Supabase store. The
 * function is resilient to network and schema failures; it returns null when
 * no record is found, so the caller can render the truthful "unknown" UI
 * instead of inventing content.
 */
suspend fun lookupDtc(code: String): DtcRecord? {
    val normalized = code.trim().uppercase()
    if (!normalized.matches(Regex("[PBCU][0-3][0-9A-F]{3}"))) return null
    return runCatching {
        SupabaseClient.client
            .from("diagnostic_codes")
            .select(
                Columns.list(
                    "id",
                    "code",
                    "system",
                    "category",
                    "severity",
                    "title_fr",
                    "title_ar",
                    "description_fr",
                    "description_ar",
                    "causes_fr",
                    "causes_ar",
                    "diagnostic_steps_fr",
                    "diagnostic_steps_ar",
                    "repair_summary_fr",
                    "repair_summary_ar"
                )
            ) {
                filter { eq("code", normalized) }
            }
            .decodeList<DtcRecord>()
            .firstOrNull()
    }.getOrNull()
}

/**
 * Breaks a multi-line free-text field into a list of trimmed non-empty lines.
 * This is defensive against NULL or blank fields and avoids splitting on
 * ambiguous punctuation.
 */
internal fun splitMultiline(value: String?): List<String> = value
    ?.split('\n')
    ?.map { it.trim() }
    ?.filter { it.isNotBlank() }
    .orEmpty()
