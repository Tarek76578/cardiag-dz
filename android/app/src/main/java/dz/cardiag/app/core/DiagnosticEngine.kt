package dz.cardiag.app.core

/**
 * Deterministic, offline-safe diagnostic layer. AI may enrich the explanation,
 * but this engine keeps the core recommendation reproducible and testable.
 */
data class DiagnosticInput(
    val dtcCodes: List<String> = emptyList(),
    val symptoms: List<String> = emptyList(),
    val liveData: Map<String, Double> = emptyMap()
)

data class DiagnosticFinding(
    val code: String,
    val severity: String,
    val causes: List<String>,
    val tests: List<String>,
    val repairGuidance: List<String>,
    val confidence: Int
)

object DiagnosticEngine {
    fun analyze(input: DiagnosticInput): List<DiagnosticFinding> = input.dtcCodes
        .map { it.trim().uppercase() }
        .filter { it.matches(Regex("[PBCU][0-3][0-9A-F]{3}")) }
        .distinct()
        .map { code ->
            when {
                code.startsWith("P03") -> DiagnosticFinding(
                    code, "warning",
                    listOf("Ignition/fuel delivery", "Air intake or vacuum leak", "Engine mechanical condition"),
                    listOf("Inspect spark/coil", "Check injector and fuel pressure", "Check intake leaks and compression if required"),
                    listOf("Confirm the failed cylinder and measured cause before replacing parts"),
                    70
                )
                code.startsWith("P01") || code.startsWith("P02") -> DiagnosticFinding(
                    code, "warning",
                    listOf("Sensor circuit", "Wiring/connector", "Fuel or air metering"),
                    listOf("Inspect connector and wiring", "Compare live PID against expected operating range"),
                    listOf("Repair the verified circuit or component; clear and rescan after repair"),
                    65
                )
                code.startsWith("U") -> DiagnosticFinding(
                    code, "critical",
                    listOf("CAN communication", "Power/ground supply", "Network module fault"),
                    listOf("Check battery voltage and grounds", "Inspect CAN wiring and connector integrity", "Identify offline ECU"),
                    listOf("Do not replace an ECU until power, ground and network integrity are verified"),
                    60
                )
                code.startsWith("B") || code.startsWith("C") -> DiagnosticFinding(
                    code, "warning",
                    listOf("Body/chassis module circuit", "Sensor or actuator", "Wiring/connector"),
                    listOf("Inspect related connector and wiring", "Check module power/ground and relevant live data"),
                    listOf("Repair only after confirming the circuit or component fault"),
                    60
                )
                else -> DiagnosticFinding(
                    code, "info", listOf("Refer to vehicle-specific DTC definition"),
                    listOf("Verify DTC applicability for the selected vehicle and ECU"),
                    listOf("Use vehicle-specific service information before replacing parts"), 50
                )
            }
        }
}
