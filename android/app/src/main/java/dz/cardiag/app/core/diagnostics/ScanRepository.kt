package dz.cardiag.app.core.diagnostics

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
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
        saveItem("scan", "scan_ids", scan.sessionId, json.encodeToString(ScanResult.serializer(), scan))
    }

    override suspend fun getScan(sessionId: String): ScanResult? = prefs.getString("scan:$sessionId", null)?.let { runCatching { json.decodeFromString(ScanResult.serializer(), it) }.getOrNull() }

    override suspend fun latestScans(limit: Int): List<ScanResult> = readIds("scan_ids").take(limit.coerceAtLeast(0)).mapNotNull { getScan(it) }

    override suspend fun saveReport(report: DiagnosticReport) {
        saveItem("report", "report_ids", report.reportId, json.encodeToString(DiagnosticReport.serializer(), report))
    }

    override suspend fun getReport(reportId: String): DiagnosticReport? = prefs.getString("report:$reportId", null)?.let { runCatching { json.decodeFromString(DiagnosticReport.serializer(), it) }.getOrNull() }

    override suspend fun latestReports(limit: Int): List<DiagnosticReport> = readIds("report_ids").take(limit.coerceAtLeast(0)).mapNotNull { getReport(it) }

    private fun saveItem(prefix: String, indexKey: String, id: String, payload: String) {
        val ids = readIds(indexKey).filterNot { it == id }.toMutableList()
        ids.add(0, id)
        val removed = ids.drop(50)
        ids.subList(50.coerceAtMost(ids.size), ids.size).clear()
        val editor = prefs.edit().putString("$prefix:$id", payload).putString(indexKey, json.encodeToString(ListSerializer(String.serializer()), ids))
        removed.forEach { editor.remove("$prefix:$it") }
        editor.apply()
    }

    private fun readIds(key: String): List<String> = prefs.getString(key, null)?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() }.orEmpty()

    companion object {
        fun newSessionId(): String = UUID.randomUUID().toString()
        fun newReportId(): String = UUID.randomUUID().toString()
    }
}
