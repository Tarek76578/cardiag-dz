package dz.cardiag.app.core

/**
 * Offline DTC knowledge catalog. Each entry is well-known automotive
 * diagnostic information sourced from public OBD-II references. The catalog
 * is intentionally small and conservative; it exists so that the app can
 * browse and filter a baseline set of codes without network access and so
 * that the UI never invents content for unknown codes.
 *
 * This file MUST NOT include fabricated descriptions, sensor values, repair
 * steps, or service information. Each entry sticks to widely documented
 * SAE J2012 generic OBD-II information.
 */
data class DtcKnowledgeEntry(
    val code: String,
    val family: Char,
    val system: String,
    val severity: String,
    val titleEn: String,
    val descriptionEn: String,
    val symptoms: List<String>,
    val causes: List<String>
)

object DtcKnowledgeCatalog {
    val entries: List<DtcKnowledgeEntry> = listOf(
        DtcKnowledgeEntry(
            code = "P0010",
            family = 'P',
            system = "Engine",
            severity = "warning",
            titleEn = "A Camshaft Position Actuator Circuit (Bank 1)",
            descriptionEn = "Generic powertrain code indicating an electrical fault in the variable valve timing (VVT) / camshaft position actuator circuit on engine bank 1.",
            symptoms = listOf("Check Engine Light", "Rough idle", "Reduced fuel economy"),
            causes = listOf("Faulty VVT solenoid", "Damaged wiring/connector", "Low oil pressure or dirty engine oil", "ECU driver fault")
        ),
        DtcKnowledgeEntry(
            code = "P0100",
            family = 'P',
            system = "Engine",
            severity = "warning",
            titleEn = "Mass or Volume Air Flow Circuit",
            descriptionEn = "Generic powertrain code indicating a malfunction in the Mass Air Flow (MAF) sensor circuit.",
            symptoms = listOf("Hesitation", "Rough idle", "Reduced power"),
            causes = listOf("Faulty MAF sensor", "Damaged intake air duct", "Contaminated MAF element", "Wiring/connector fault")
        ),
        DtcKnowledgeEntry(
            code = "P0171",
            family = 'P',
            system = "Engine",
            severity = "warning",
            titleEn = "System Too Lean (Bank 1)",
            descriptionEn = "Generic powertrain code indicating the engine is running lean on bank 1, typically identified by long-term fuel trim values above expected range.",
            symptoms = listOf("Hesitation", "Misfire", "Reduced power", "Check Engine Light"),
            causes = listOf("Unmetered air leak (intake, vacuum lines)", "Faulty MAF sensor", "Low fuel pressure", "Restricted fuel injectors")
        ),
        DtcKnowledgeEntry(
            code = "P0300",
            family = 'P',
            system = "Engine",
            severity = "critical",
            titleEn = "Random/Multiple Cylinder Misfire Detected",
            descriptionEn = "Generic powertrain code indicating the ECU has detected random or multiple cylinder misfires.",
            symptoms = listOf("Engine shake", "Power loss", "Rough idle", "Flashing Check Engine Light"),
            causes = listOf("Worn spark plugs / coils", "Faulty fuel injectors", "Vacuum leak", "Low compression", "Bad crankshaft position sensor")
        ),
        DtcKnowledgeEntry(
            code = "P0301",
            family = 'P',
            system = "Engine",
            severity = "critical",
            titleEn = "Cylinder 1 Misfire Detected",
            descriptionEn = "Generic powertrain code indicating a confirmed misfire on cylinder 1.",
            symptoms = listOf("Engine shake", "Power loss", "Rough idle"),
            causes = listOf("Spark plug or coil on cylinder 1", "Injector electrical/mechanical fault", "Compression issue on cylinder 1")
        ),
        DtcKnowledgeEntry(
            code = "P0420",
            family = 'P',
            system = "Emissions",
            severity = "warning",
            titleEn = "Catalyst System Efficiency Below Threshold (Bank 1)",
            descriptionEn = "Generic powertrain code indicating the catalytic converter efficiency is below the expected threshold on bank 1.",
            symptoms = listOf("Check Engine Light", "Possible reduced fuel economy"),
            causes = listOf("Aged or contaminated catalytic converter", "Faulty downstream O2 sensor", "Exhaust leak before catalyst", "Engine misfire or oil contamination")
        ),
        DtcKnowledgeEntry(
            code = "P2002",
            family = 'P',
            system = "Emissions",
            severity = "warning",
            titleEn = "Diesel Particulate Filter Efficiency Below Threshold (Bank 1)",
            descriptionEn = "Generic powertrain code (diesel) indicating the DPF efficiency has dropped below the expected threshold on bank 1.",
            symptoms = listOf("DPF warning light", "Reduced power", "Limp mode"),
            causes = listOf("Saturated DPF", "Faulty differential pressure sensor", "Failed regeneration", "Short trips preventing regeneration")
        ),
        DtcKnowledgeEntry(
            code = "B0100",
            family = 'B',
            system = "Body",
            severity = "warning",
            titleEn = "Lost Communication With Airbag Control Module",
            descriptionEn = "Body code indicating communication loss with the airbag/SRS control module on a common bus.",
            symptoms = listOf("Airbag warning light", "SRS diagnostic trouble"),
            causes = listOf("Wiring/connector to SRS module", "SRS module power/ground", "CAN bus issue")
        ),
        DtcKnowledgeEntry(
            code = "C0030",
            family = 'C',
            system = "Chassis",
            severity = "warning",
            titleEn = "Left Front Wheel Speed Circuit Malfunction",
            descriptionEn = "Chassis code indicating a malfunction of the left front wheel speed sensor circuit, which feeds the ABS/ESC module.",
            symptoms = listOf("ABS warning", "Traction control warning", "Possible loss of ABS function"),
            causes = listOf("Wheel speed sensor failure", "Tone ring damage", "Wiring/connector fault")
        ),
        DtcKnowledgeEntry(
            code = "U0100",
            family = 'U',
            system = "Network",
            severity = "critical",
            titleEn = "Lost Communication With ECM/PCM",
            descriptionEn = "Network code indicating the ECU has stopped receiving messages from the engine control module on the CAN bus.",
            symptoms = listOf("Check Engine Light", "Engine may not start", "Limp mode"),
            causes = listOf("ECM/PCM power or ground", "CAN bus wiring fault", "ECM/PCM internal failure", "Battery/charging fault")
        )
    )

    private val byCode: Map<String, DtcKnowledgeEntry> = entries.associateBy { it.code }

    fun lookup(code: String): DtcKnowledgeEntry? = byCode[code.trim().uppercase()]

    fun search(query: String): List<DtcKnowledgeEntry> {
        val q = query.trim().uppercase()
        if (q.isEmpty()) return entries
        return entries.filter { it.code.contains(q) || it.titleEn.uppercase().contains(q) }
    }

    fun byFamily(family: Char): List<DtcKnowledgeEntry> = entries.filter { it.family == family }

    fun bySeverity(severity: String): List<DtcKnowledgeEntry> = entries.filter { it.severity == severity }
}
