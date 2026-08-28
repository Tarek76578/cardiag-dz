package dz.cardiag.app.core.diagnostics

object ScanStateMachine {
    fun begin(): ScanResult = ScanResult(
        sessionId = OfflineFirstScanRepository.newSessionId(),
        startedAt = System.currentTimeMillis(),
        phase = ScanPhase.CONNECTING
    )

    fun connected(scan: ScanResult): ScanResult = scan.copy(phase = ScanPhase.SCANNING, error = null)

    fun processing(scan: ScanResult): ScanResult = scan.copy(phase = ScanPhase.PROCESSING, error = null)

    fun success(scan: ScanResult, dtcs: List<ScanDtc>, liveData: List<LivePid> = emptyList(), vin: String? = scan.vin, ecu: String? = scan.ecu): ScanResult =
        scan.copy(phase = ScanPhase.SUCCESS, completedAt = System.currentTimeMillis(), dtcs = dtcs, liveData = liveData, vin = vin, ecu = ecu, error = null)

    fun offline(scan: ScanResult, reason: String): ScanResult = scan.copy(phase = ScanPhase.OFFLINE, error = reason)

    fun failure(scan: ScanResult, reason: String): ScanResult = scan.copy(phase = ScanPhase.ERROR, completedAt = System.currentTimeMillis(), error = reason)
}
