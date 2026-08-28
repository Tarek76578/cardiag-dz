package dz.cardiag.app.diagnostics

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface ScanRepository {
    suspend fun save(result: ScanResult)
    suspend fun latest(): ScanResult?
    suspend fun list(): List<ScanResult>
    suspend fun clear()
}

/** Local source of truth for scan sessions. Remote synchronization can be layered above this repository. */
class LocalScanRepository(context: Context) : ScanRepository {
    private val file = File(context.filesDir, "cardiag_scan_cache.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun save(result: ScanResult) {
        val current = list().filterNot { it.sessionId == result.sessionId }
        file.writeText(json.encodeToString((current + result).takeLast(50)))
    }

    override suspend fun latest(): ScanResult? = list().maxByOrNull { it.timestampEpochMs }

    override suspend fun list(): List<ScanResult> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString<List<ScanResult>>(file.readText())
    }.getOrDefault(emptyList())

    override suspend fun clear() { if (file.exists()) file.delete() }
}
