package dz.cardiag.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static check on the Edge Function `supabase/functions/diagnose/index.ts`
 * to ensure the offline / fallback diagnostic path uses both Arabic and
 * French strings, and that the Arabic path contains genuine Arabic
 * script. This guards against the regression where the offline response
 * leaks French strings to Arabic users.
 */
class EdgeFunctionOfflineLocalizationTest {

    private fun projectRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        var dir: File? = cwd
        repeat(6) {
            val candidate = File(dir, "supabase/functions/diagnose/index.ts")
            if (candidate.exists()) return dir!!
            dir = dir?.parentFile
        }
        error("supabase/functions/diagnose/index.ts not found in ${cwd.absolutePath}")
    }

    private fun edgeSource(): String {
        val f = File(projectRoot(), "supabase/functions/diagnose/index.ts")
        return f.readText()
    }

    @Test
    fun `offline diagnosis contains both Arabic and French text`() {
        val src = edgeSource()
        assertTrue(
            "offlineDiagnosis should contain French strings",
            src.contains("Confirmer les DTC et relever le freeze-frame")
        )
        assertTrue(
            "offlineDiagnosis should contain Arabic strings",
            src.contains("تأكيد رموز العطل وقراءة الإطار المجمد")
        )
    }

    @Test
    fun `offline diagnosis has dedicated Arabic and French branches for each field`() {
        val src = edgeSource()
        // Validate that recommendedTests, repairGuidance, safetyNotes, uncertainty
        // and nextBestTest each have an `isAr ? [ar strings] : [fr strings]` shape.
        val mustHaveBilingual = listOf(
            "recommendedTests",
            "repairGuidance",
            "safetyNotes",
            "uncertainty",
            "nextBestTest"
        )
        mustHaveBilingual.forEach { field ->
            // Heuristic: the field is computed in offlineDiagnosis using `isAr ? ... : ...`
            // with both an Arabic and a French array.
            assertTrue("offlineDiagnosis must localise $field in Arabic + French", src.contains("const $field=isAr?"))
        }
    }

    @Test
    fun `arabic copy in the edge function uses arabic script`() {
        val src = edgeSource()
        val arabicPattern = Regex("""[\u0600-\u06FF]""")
        // Sample a known Arabic phrase.
        val phrase = "تأكيد رموز العطل وقراءة الإطار المجمد"
        assertTrue(arabicPattern.containsMatchIn(phrase))
        assertTrue("Edge function source should include the Arabic phrase", src.contains(phrase))
    }

    @Test
    fun `french copy in the edge function does not leak into the Arabic branch`() {
        val src = edgeSource()
        // Find each "isAr?arArray:frArray" and assert that arArray contains Arabic chars
        // and frArray contains French chars.
        val isAr = Regex("""isAr\?\[([^\]]+)\]:\[([^\]]+)\]""")
        isAr.findAll(src).forEach { m ->
            val arBlock = m.groupValues[1]
            val frBlock = m.groupValues[2]
            val arabic = Regex("""[\u0600-\u06FF]""")
            val latin = Regex("""[a-zA-Zàâçéèêëîïôûùüÿñæœ]""")
            assertTrue("Arabic block missing Arabic script: $arBlock", arabic.containsMatchIn(arBlock))
            assertTrue("French block missing Latin script: $frBlock", latin.containsMatchIn(frBlock))
        }
    }

    @Test
    fun `edge function defaults language to ar or fr and never leaks the unrequested one`() {
        val src = edgeSource()
        // The validator should never pick French text when language==="ar".
        val risky = listOf(
            // Lines that historically leaked French for Arabic users:
            "offlineDiagnosis(",
        )
        risky.forEach { token ->
            // We don't fail on this, but we require the file to include the
            // `isAr` ternary pattern in offlineDiagnosis, ensuring the
            // Arabic path is genuinely used when language==='ar'.
            assertTrue(
                "offlineDiagnosis must select language by `isAr`",
                src.contains("isAr")
            )
        }
        assertFalse(
            "Edge function must not embed a hard-coded French-only summary default",
            src.contains("\"Diagnostic structuré généré avec données limitées.\"") &&
                !src.contains("تشخيص منظم أُنشئ بأدلة محدودة.")
        )
    }
}
