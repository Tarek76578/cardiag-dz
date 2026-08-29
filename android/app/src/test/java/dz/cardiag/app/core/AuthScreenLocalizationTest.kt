package dz.cardiag.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sanity check that the most relevant auth-related strings are present in
 * the production locale resources, both in French and Arabic, with the
 * Arabic strings genuinely using Arabic script.
 */
class AuthScreenLocalizationTest {

    private fun projectRoot(): File {
        // Tests run from the :app module. The strings.xml is at
        // src/main/res/values/strings.xml relative to the project root.
        val cwd = File(System.getProperty("user.dir") ?: ".")
        var dir: File? = cwd
        repeat(4) {
            val candidate = File(dir, "src/main/res/values/strings.xml")
            if (candidate.exists()) return dir!!
            dir = dir?.parentFile
        }
        error("strings.xml not found in ${cwd.absolutePath}")
    }

    private fun fr(): Map<String, String> {
        val file = File(projectRoot(), "src/main/res/values/strings.xml")
        return parseStrings(file.readText())
    }

    private fun ar(): Map<String, String> {
        val file = File(projectRoot(), "src/main/res/values-ar/strings.xml")
        return parseStrings(file.readText())
    }

    private fun parseStrings(xml: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pattern = Regex("""<string\s+name="([^"]+)"\s*>([^<]+)</string>""")
        pattern.findAll(xml).forEach { m ->
            map[m.groupValues[1]] = m.groupValues[2]
        }
        return map
    }

    @Test
    fun `auth strings exist in both languages`() {
        val fr = fr()
        val ar = ar()
        val required = listOf(
            "auth_continue_guest",
            "auth_continue_guest_desc",
            "auth_sign_in",
            "auth_sign_up",
            "auth_have_account",
            "auth_no_account",
            "auth_email",
            "auth_password",
            "auth_failed_invalid_credentials",
            "auth_failed_already_registered",
            "auth_failed_generic",
            "guest_banner_title",
            "guest_banner_body",
            "more_login"
        )
        required.forEach { key ->
            assertTrue("FR missing $key", fr[key]?.isNotBlank() == true)
            assertTrue("AR missing $key", ar[key]?.isNotBlank() == true)
        }
    }

    @Test
    fun `arabic auth strings actually use arabic script`() {
        val ar = ar()
        val arabicPattern = Regex("""[\u0600-\u06FF]""")
        listOf(
            "auth_continue_guest",
            "auth_sign_in",
            "auth_sign_up",
            "auth_email",
            "auth_password",
            "guest_banner_title",
            "more_login"
        ).forEach { key ->
            val v = ar[key] ?: error("missing $key")
            assertTrue("$key has no Arabic script: $v", arabicPattern.containsMatchIn(v))
        }
    }

    @Test
    fun `french and arabic guest copy differ`() {
        val fr = fr()
        val ar = ar()
        val keys = listOf("auth_continue_guest", "auth_continue_guest_desc", "more_login", "guest_banner_body")
        keys.forEach { k ->
            assertNotEquals("FR and AR differ expected for $k", fr[k], ar[k])
        }
    }

    @Test
    fun `french and arabic auth_failed_invalid_credentials differ`() {
        val fr = fr()
        val ar = ar()
        val a = fr["auth_failed_invalid_credentials"] ?: ""
        val b = ar["auth_failed_invalid_credentials"] ?: ""
        assertFalse(a.isBlank())
        assertFalse(b.isBlank())
        assertNotEquals(a, b)
    }
}
