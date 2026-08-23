package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdParserTest {
    @Test fun parsesRpm() = assertEquals(1726.0, ObdParser.parseRpm("41 0C 1A F8 >"), 0.0)
    @Test fun parsesCoolant() = assertEquals(50.0, ObdParser.parseCoolantCelsius("41 05 5A"), 0.0)
    @Test fun parsesSpeed() = assertEquals(80.0, ObdParser.parseSpeed("41 0D 50"), 0.0)
    @Test fun parsesCommonDtcs() = assertEquals(listOf("P0300", "P0301"), ObdParser.parseDtc("43 03 00 03 01 00 00"))
    @Test fun ignoresNoData() = assertTrue(ObdParser.normalize("SEARCHING...\rNO DATA\r>").isBlank())
    @Test fun validatesVin() {
        assertTrue(VinValidator.isValid("VF1AAAAA1BBBBBBBB"))
        assertTrue(!VinValidator.isValid("VF1IOQ"))
        assertTrue(!VinValidator.isValid("VF1IOQ12345678901"))
    }
}
