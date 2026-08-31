package dz.cardiag.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies the Road Assistant / GPS UI strings are present in both
 * French and Arabic, and that the Arabic copy genuinely uses Arabic
 * script. This guards against accidental English / French leakage in the
 * production UI.
 */
class RoadAssistantLocalizationTest {

    private fun projectRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        var dir: File? = cwd
        repeat(4) {
            val candidate = File(dir, "src/main/res/values/strings.xml")
            if (candidate.exists()) return dir!!
            dir = dir?.parentFile
        }
        error("strings.xml not found in ${cwd.absolutePath}")
    }

    private fun parseStrings(xml: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pattern = Regex("""<string\s+name="([^"]+)"\s*>([^<]+)</string>""")
        pattern.findAll(xml).forEach { m ->
            map[m.groupValues[1]] = m.groupValues[2]
        }
        return map
    }

    private fun fr() = parseStrings(File(projectRoot(), "src/main/res/values/strings.xml").readText())
    private fun ar() = parseStrings(File(projectRoot(), "src/main/res/values-ar/strings.xml").readText())

    @Test
    fun `road assistant strings exist in both languages`() {
        val fr = fr()
        val ar = ar()
        val required = listOf(
            "ra_title",
            "ra_subtitle",
            "ra_need_permission_title",
            "ra_need_permission_body",
            "ra_request_permission",
            "ra_categories",
            "ra_category_mechanic",
            "ra_category_auto_electrician",
            "ra_category_roadside",
            "ra_category_spare_parts",
            "ra_category_fuel",
            "ra_category_hospital",
            "ra_category_towing",
            "ra_radius",
            "ra_search_hint",
            "ra_open_map",
            "ra_search_external",
            "ra_hazards_title",
            "ra_hazards_subtitle",
            "ra_hazards_empty",
            "ra_offline_explainer",
            "ra_live_source_required",
            "emergency_title",
            "emergency_subtitle",
            "emergency_police",
            "emergency_fire",
            "emergency_ambulance",
            "emergency_protection_civile",
            "driver_actions_find_service",
            "ra_map_title",
            "ra_map_subtitle",
            "ra_map_default_location",
            "ra_map_no_location"
        )
        required.forEach { key ->
            assertTrue("FR missing $key", fr[key]?.isNotBlank() == true)
            assertTrue("AR missing $key", ar[key]?.isNotBlank() == true)
        }
    }

    @Test
    fun `arabic road assistant strings use arabic script`() {
        val ar = ar()
        val arabicPattern = Regex("""[\u0600-\u06FF]""")
        listOf(
            "ra_title",
            "ra_categories",
            "ra_category_mechanic",
            "ra_category_roadside",
            "ra_hazards_title",
            "driver_actions_find_service",
            "emergency_police",
            "ra_map_title",
            "ra_map_subtitle",
            "ra_map_default_location",
            "ra_map_no_location"
        ).forEach { key ->
            val v = ar[key] ?: error("missing $key")
            assertTrue("$key has no Arabic script: $v", arabicPattern.containsMatchIn(v))
        }
    }

    @Test
    fun `french and arabic road assistant copy differ`() {
        val fr = fr()
        val ar = ar()
        listOf("ra_title", "ra_subtitle", "ra_categories", "ra_category_mechanic").forEach { k ->
            assertNotEquals("FR and AR should differ for $k", fr[k], ar[k])
        }
    }

    @Test
    fun `arabic search hint uses Arabic script`() {
        val ar = ar()["ra_search_hint"] ?: ""
        val fr = fr()["ra_search_hint"] ?: ""
        // Both languages expose the same hint text including the Arabic keywords
        // for the convenience of users typing Arabic automotive terms; the
        // Arabic version uses Arabic punctuation while the French version
        // uses Latin punctuation.
        assertTrue(ar.contains("ميكانيكي"))
        assertTrue(ar.contains("قطع الغيار"))
        // French hint is in Latin script.
        assertTrue(fr.contains("Rechercher"))
    }

    @Test
    fun `arabic road assistant string keys are mirrored to french`() {
        val fr = fr()
        val ar = ar()
        // Sanity check that the same set of "ra_*" keys exists in both files.
        val frKeys = fr.keys.filter { it.startsWith("ra_") }.toSet()
        val arKeys = ar.keys.filter { it.startsWith("ra_") }.toSet()
        assertEquals("FR and AR ra_* keys differ", frKeys, arKeys)
    }

    private fun assertEquals(msg: String, expected: Any?, actual: Any?) {
        if (expected != actual) throw AssertionError("$msg: expected $expected, was $actual")
    }
}
