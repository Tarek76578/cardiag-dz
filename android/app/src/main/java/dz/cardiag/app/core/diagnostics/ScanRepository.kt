package dz.cardiag.app.core.diagnostics

import android.content.Context
import kotlinx.serialization.json.Json
import java.util.UUID

interface ScanRepository {
    suspend fun saveScan(scan: ScanResult)
    suspend fun getScan(sessionId: String): ScanResult?
    suspend fun latestScans(limit: Int = 50): List<ScanResult>
    suspend fun saveReport(report: DiagnosticReport)
    suspend fun getReport(reportId: String): DiagnosticReport?
    suspend fun latestReports(limit: Int = 50): List<DiagnosticReport>
}

class OfflineFirstScanRepository(context: Context) : ScanRepository {
    private val prefs = context.applicationContext.getSharedPreferences("cardiag_offline_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun saveScan(scan: ScanResult) {
        val ids = mutableListOf<String>().apply { addAll(readIds("scan_ids")) }
        if (!ids.contains(scan.sessionId)) ids.add(0, scan.sessionId)
        while (ids.size > 50) ids.removeLast()
        prefs.edit().putString("scan:${scan.sessionId}", json.encodeToString(ScanResult.serializer(), scan)).putStringSet("scan_ids", ids.toSet()).apply()
    }

    override suspend fun getScan(sessionId: String): ScanResult? = prefs.getString("scan:$sessionId", null)?.let { runCatching { json.decodeFromString(ScanResult.serializer(), it) }.getOrNull() }

    override suspend fun latestScans(limit: Int): List<ScanResult> = readIds("scan_ids").take(limit.coerceAtLeast(0)).mapNotNull { getScan(it) }

    override suspend fun saveReport(report: DiagnosticReport) {
        val ids = mutableListOf<String>().apply { addAll(readIds("report_ids")) }
        if (!ids.contains(report.reportId)) ids.add(0, report.reportId)
        while (ids.size > 50) ids.removeLast()
        prefs.edit().putString("report:${report.reportId}", json.encodeToString(DiagnosticReport.serializer(), report)).putStringSet("report_ids", ids.toSet()).apply()
    }

    override suspend fun getReport(reportId: String): DiagnosticReport? = prefs.getString("report:$reportId", null)?.let { runCatching { json.decodeFromString(DiagnosticReport.serializer(), it) }.getOrNull() }

    override suspend fun latestReports(limit: Int): List<DiagnosticReport> = readIds("report_ids").take(limit.coerceAtLeast(0)).mapNotNull { getReport(it) }

    private fun readIds(key: String): List<String> = prefs.getStringSet(key, emptySet()).orEmpty().sortedByDescending { it }

    companion object {
        fun newSessionId(): String = UUID.randomUUID().toString()
        fun newReportId(): String = UUID.randomUUID().toString()
    }
}
