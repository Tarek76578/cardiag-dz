package dz.cardiag.app

/** A single live measurement pulled from a supported OBD PID. */
data class LiveMeasurement(
    val name: String,
    val pid: String,
    val unit: String,
    val value: Double?,
    val min: Double,
    val max: Double
) {
    companion object {
        fun defaultCatalog(): List<LiveMeasurement> = listOf(
            LiveMeasurement("RPM", "010C", "rpm", null, 700.0, 8000.0),
            LiveMeasurement("Coolant", "0105", "°C", null, 70.0, 110.0),
            LiveMeasurement("Speed", "010D", "km/h", null, 0.0, 250.0),
            LiveMeasurement("MAF", "0110", "g/s", null, 0.0, 200.0),
            LiveMeasurement("MAP", "010B", "kPa", null, 20.0, 200.0),
            LiveMeasurement("Throttle", "0111", "%", null, 0.0, 100.0),
            LiveMeasurement("Battery", "0142", "V", null, 12.0, 15.0),
            LiveMeasurement("Engine load", "0104", "%", null, 0.0, 100.0),
            LiveMeasurement("Timing", "010E", "°", null, -10.0, 50.0)
        )
    }
}

/** Finding produced by correlating a DTC with live measurements. */
data class CorrelationFinding(
    val title: String,
    val reason: String,
    val severity: String,
    val confidence: Int
)

/** Result of a single guided test. */
enum class TestResult { PENDING, PASSED, FAILED, INCONCLUSIVE }

/** One step in a guided diagnosis decision tree. */
class GuidedStep(val prompt: String) {
    var result: TestResult = TestResult.PENDING
    val id: String = prompt
}

/**
 * Decision tree library. Each rule is deterministic and offline-safe, so that
 * the guided diagnosis screen remains useful even when the AI backend is
 * unavailable.
 */
object GuidedDecisionTree {
    fun forCode(code: String, record: DtcRecord?): List<GuidedStep> {
        val family = code.firstOrNull()?.uppercaseChar()
        return when {
            code.startsWith("P03") -> listOf(
                GuidedStep("Inspect spark plug / coil condition on the affected cylinder."),
                GuidedStep("Swap the suspected coil with an adjacent cylinder and rescan misfire counter."),
                GuidedStep("Measure fuel injector resistance and harness continuity."),
                GuidedStep("Perform compression test on the affected cylinder.")
            )
            code.startsWith("P01") || code.startsWith("P02") -> listOf(
                GuidedStep("Inspect the sensor connector and wiring for the circuit."),
                GuidedStep("Compare the live PID against the expected operating range."),
                GuidedStep("Verify the sensor is in spec with a multimeter or scope."),
                GuidedStep("Clear the code and rescan after repair.")
            )
            code.startsWith("U") -> listOf(
                GuidedStep("Check battery voltage and main grounds."),
                GuidedStep("Inspect CAN bus wiring and connector integrity."),
                GuidedStep("Identify which ECU is offline via the network scan."),
                GuidedStep("Do not replace an ECU until power, ground and network are verified.")
            )
            family == 'B' || family == 'C' -> listOf(
                GuidedStep("Inspect the related body/chassis connector and wiring."),
                GuidedStep("Check the module power, ground and CAN/network connections."),
                GuidedStep("Verify the live data for the affected sensor or actuator."),
                GuidedStep("Clear the code and rescan after repair.")
            )
            else -> record?.let {
                listOf(
                    GuidedStep("Verify DTC applicability for the selected vehicle and ECU."),
                    GuidedStep("Review the causes listed for this code."),
                    GuidedStep("Perform the recommended diagnostic steps in order."),
                    GuidedStep("Document the test result before replacing parts.")
                )
            }.orEmpty()
        }
    }

    /**
     * Produces a textual recommendation based on the recorded step results. It
     * never claims certainty without recorded evidence and never recommends
     * blind parts replacement.
     */
    fun recommendation(steps: List<GuidedStep>, currentIndex: Int): String? {
        if (steps.isEmpty() || currentIndex >= steps.size) return null
        val current = steps[currentIndex]
        return when (current.result) {
            TestResult.PASSED -> "Result recorded as passed. Continue with the next step to confirm the root cause."
            TestResult.FAILED -> "Result recorded as failed. Inspect the suspect component and its circuit before replacing parts."
            TestResult.INCONCLUSIVE -> "Result is inconclusive. Re-run the test with controlled conditions, then continue."
            TestResult.PENDING -> null
        }
    }
}

/**
 * Offline correlation between a stored DTC and live PID measurements. The
 * scoring is conservative; it never claims a confirmed fault without
 * persistent out-of-range evidence.
 */
object LiveDataCorrelation {
    fun findings(code: String, measurements: List<LiveMeasurement>): List<CorrelationFinding> {
        val findings = mutableListOf<CorrelationFinding>()
        if (code.startsWith("P030")) {
            val rpm = measurements.firstOrNull { it.pid == "010C" }
            if (rpm != null && rpm.value != null && (rpm.value < rpm.min || rpm.value > rpm.max)) {
                findings.add(CorrelationFinding("RPM out of expected range", "Recorded RPM ${rpm.value} is outside ${rpm.min}..${rpm.max}.", "high", 75))
            }
        }
        if (code.startsWith("P01") || code.startsWith("P02")) {
            measurements.filter { it.value != null && (it.value < it.min || it.value > it.max) }
                .forEach { m ->
                    findings.add(CorrelationFinding("${m.name} out of range", "${m.name} = ${m.value} ${m.unit}, expected ${m.min}..${m.max}.", "medium", 60))
                }
        }
        if (code.startsWith("U")) {
            val batt = measurements.firstOrNull { it.pid == "0142" }
            if (batt != null && batt.value != null && batt.value < batt.min) {
                findings.add(CorrelationFinding("Battery voltage low", "Voltage ${batt.value} V is below expected ${batt.min} V; check the network power supply.", "high", 70))
            }
        }
        return findings
    }
}
