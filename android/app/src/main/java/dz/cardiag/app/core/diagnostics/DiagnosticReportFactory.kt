package dz.cardiag.app.core.diagnostics

object DiagnosticReportFactory {
    fun fromScan(
        scan: ScanResult,
        engine: String? = null,
        diagnosis: String? = null,
        recommendations: List<String> = emptyList()
    ): DiagnosticReport = DiagnosticReport(
        reportId = OfflineFirstScanRepository.newReportId(),
        sessionId = scan.sessionId,
        vehicleId = scan.vehicleId,
        vehicleName = scan.vehicleName,
        engine = engine,
        ecu = scan.ecu,
        vin = scan.vin,
        createdAt = System.currentTimeMillis(),
        dtcs = scan.dtcs,
        liveData = scan.liveData,
        diagnosis = diagnosis,
        recommendations = recommendations
    )
}
