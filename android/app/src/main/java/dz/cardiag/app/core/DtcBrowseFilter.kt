package dz.cardiag.app.core

/**
 * Pure filter helper for [DtcKnowledgeCatalog] entries. The browse screen
 * uses it for both family and severity filters; keeping the logic in pure
 * Kotlin makes it straightforward to unit test.
 */
object DtcBrowseFilter {
    fun apply(
        entries: List<DtcKnowledgeEntry>,
        query: String,
        family: Char?,
        severity: String?
    ): List<DtcKnowledgeEntry> {
        val q = query.trim().uppercase()
        val base = if (q.isEmpty()) entries
            else entries.filter { it.code.contains(q) || it.titleEn.uppercase().contains(q) }
        val byFamily = if (family == null) base else base.filter { it.family == family }
        val bySeverity = if (severity == null) byFamily else byFamily.filter { it.severity == severity }
        return bySeverity
    }
}
