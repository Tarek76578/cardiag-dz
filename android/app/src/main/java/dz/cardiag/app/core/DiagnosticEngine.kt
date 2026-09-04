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

object DiagnosticEngine {
    fun analyze(input: DiagnosticInput): List<DiagnosticFinding> = input.dtcCodes
        .map { it.trim().uppercase() }
        .filter { it.matches(Regex("[PBCU][0-3][0-9A-F]{3}")) }
        .distinct()
        .map { code ->
            val (severity, causes, tests, recommendations, score) = when {
                code.startsWith("P03") -> DiagnosticResult(
                    "warning",
                    listOf("Ignition/fuel delivery", "Air intake or vacuum leak", "Engine mechanical condition"),
                    listOf("Inspect spark/coil", "Check injector and fuel pressure", "Check intake leaks and compression if required"),
                    listOf("Confirm the failed cylinder and measured cause before replacing parts"),
                    70
                )
                code.startsWith("P01") || code.startsWith("P02") -> DiagnosticResult(
                    "warning",
                    listOf("Sensor circuit", "Wiring/connector", "Fuel or air metering"),
                    listOf("Inspect connector and wiring", "Compare live PID against expected operating range"),
                    listOf("Repair the verified circuit or component; clear and rescan after repair"),
                    65
                )
                code.startsWith("U") -> DiagnosticResult(
                    "critical",
                    listOf("CAN communication", "Power/ground supply", "Network module fault"),
                    listOf("Check battery voltage and grounds", "Inspect CAN wiring and connector integrity", "Identify offline ECU"),
                    listOf("Do not replace an ECU until power, ground and network integrity are verified"),
                    60
                )
                code.startsWith("B") || code.startsWith("C") -> DiagnosticResult(
                    "warning",
                    listOf("Body/chassis module circuit", "Sensor or actuator", "Wiring/connector"),
                    listOf("Inspect related connector and wiring", "Check module power/ground and relevant live data"),
                    listOf("Repair only after confirming the circuit or component fault"),
                    60
                )
                else -> DiagnosticResult(
                    "info",
                    listOf("Refer to vehicle-specific DTC definition"),
                    listOf("Verify DTC applicability for the selected vehicle and ECU"),
                    listOf("Use vehicle-specific service information before replacing parts"),
                    50
                )
            }
            DiagnosticFinding(
                code = code,
                severity = severity,
                score = score,
                confidence = score / 100.0,
                causes = causes,
                tests = tests,
                recommendations = recommendations
            )
        }

    fun evaluate(
        knowledge: DtcKnowledge,
        observedSymptoms: Set<String> = emptySet(),
        liveData: List<LivePidValue> = emptyList(),
        testResults: Map<String, Boolean> = emptyMap()
    ): DiagnosticFinding {
        var score = 40
        score += observedSymptoms.count { symptom -> knowledge.symptoms.any { it.equals(symptom, true) } } * 10
        score += testResults.values.count { it } * 8

        // Normal live data is not positive evidence for a fault. Only an
        // out-of-range, finite value can increase confidence in a DTC-related
        // hypothesis. PIDs without a configured range contribute nothing.
        score += liveData.count { value ->
            val measured = value.value
            measured.isFinite() &&
                value.min != null && value.max != null &&
                (measured < value.min!! || measured > value.max!!)
        } * 2

        val bounded = score.coerceIn(0, 100)
        return DiagnosticFinding(
            code = knowledge.code,
            severity = knowledge.severity,
            score = bounded,
            confidence = bounded / 100.0,
            causes = knowledge.causes,
            tests = knowledge.tests,
            recommendations = knowledge.repairs
        )
    }

    private data class DiagnosticResult(
        val severity: String,
        val causes: List<String>,
        val tests: List<String>,
        val recommendations: List<String>,
        val score: Int
    )
}
