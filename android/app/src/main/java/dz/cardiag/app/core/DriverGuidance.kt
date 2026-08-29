package dz.cardiag.app.core

/**
 * Lightweight, evidence-aware driver guidance. The output is intentionally
 * conservative: it never claims certainty about safety without recorded
 * evidence and never recommends "keep driving" when a critical fault is
 * present.
 */
data class DriverGuidance(
    val canDrive: CanDriveVerdict,
    val nextSteps: List<String>,
    val uncertainty: String
)

enum class CanDriveVerdict { YES, CAUTION, NO, UNKNOWN }

object DriverGuidanceEngine {
    fun evaluate(
        dtcCount: Int,
        pendingCount: Int,
        permanentCount: Int,
        milOn: Boolean?,
        readinessReady: Boolean?,
        severities: List<String> = emptyList(),
        language: String = "fr"
    ): DriverGuidance {
        val isAr = language == "ar"
        val critical = severities.any { it.equals("critical", true) }
        val totalFaults = dtcCount + pendingCount + permanentCount
        val verdict = when {
            // Critical fault with confirmed DTCs: stop.
            critical && dtcCount > 0 -> CanDriveVerdict.NO
            // MIL on with active faults: caution.
            milOn == true && totalFaults > 0 -> CanDriveVerdict.CAUTION
            // Readiness not ready is normal after a clear: caution.
            readinessReady == false && milOn != true -> CanDriveVerdict.CAUTION
            // MIL off, no faults, readiness ok: yes, keep an eye.
            dtcCount == 0 && pendingCount == 0 && permanentCount == 0 && milOn == false -> CanDriveVerdict.YES
            else -> CanDriveVerdict.UNKNOWN
        }
        val next = mutableListOf<String>()
        if (isAr) {
            when (verdict) {
                CanDriveVerdict.NO -> {
                    next += "أوقف السيارة في مكان آمن."
                    next += "لا تواصل القيادة مع وجود عطل حرج."
                    next += "اتصل بخدمة المساعدة أو ميكانيكي مؤهل."
                }
                CanDriveVerdict.CAUTION -> {
                    next += "تجنّب التسارع القوي والسرعات العالية."
                    next += "إذا ازدادت الأعراض أو ظهر ضوء المحرك يومض: توقف فورا."
                    next += "راجع ميكانيكيا في أقرب وقت."
                }
                CanDriveVerdict.YES -> {
                    next += "تابع القيادة بشكل طبيعي."
                    next += "إذا ظهر ضوء المحرك أو لاحظت أعراضا جديدة: أعد الفحص."
                }
                CanDriveVerdict.UNKNOWN -> {
                    next += "راجع ميكانيكيا مؤهلا لتأكيد الحالة."
                    next += "سجّل الأعراض الجديدة لمساعدة التشخيص."
                }
            }
            val uncertaintyText = when (verdict) {
                CanDriveVerdict.UNKNOWN -> "الأدلة غير كافية. الجواب تقديري."
                CanDriveVerdict.NO -> "هذا التوجيه مبني على عطل حرج مسجّل في وحدة التحكم."
                CanDriveVerdict.CAUTION -> "مبني على وجود ضوء محرك مضاء أو عطل غير حرج."
                CanDriveVerdict.YES -> "لا توجد أعطال نشطة. القرار مسؤوليتك."
            }
            return DriverGuidance(verdict, next, uncertaintyText)
        } else {
            when (verdict) {
                CanDriveVerdict.NO -> {
                    next += "Garez le véhicule dans un endroit sûr."
                    next += "Ne continuez pas à rouler avec un défaut critique."
                    next += "Contactez une assistance ou un mécanicien qualifié."
                }
                CanDriveVerdict.CAUTION -> {
                    next += "Évitez les accélérations franches et les vitesses élevées."
                    next += "Si les symptômes s'aggravent ou si le voyant clignote : arrêtez-vous."
                    next += "Faites contrôler dès que possible."
                }
                CanDriveVerdict.YES -> {
                    next += "Vous pouvez continuer à rouler normalement."
                    next += "Si un voyant s'allume ou si vous remarquez un nouveau symptôme : relancez un diagnostic."
                }
                CanDriveVerdict.UNKNOWN -> {
                    next += "Faites confirmer l'état par un mécanicien qualifié."
                    next += "Notez les nouveaux symptômes pour aider le diagnostic."
                }
            }
            val uncertaintyText = when (verdict) {
                CanDriveVerdict.UNKNOWN -> "Preuves insuffisantes ; recommandation indicative."
                CanDriveVerdict.NO -> "Recommandation fondée sur un défaut critique enregistré."
                CanDriveVerdict.CAUTION -> "Fondé sur un voyant moteur allumé ou un défaut non critique."
                CanDriveVerdict.YES -> "Aucun défaut actif. La décision finale vous appartient."
            }
            return DriverGuidance(verdict, next, uncertaintyText)
        }
    }
}
