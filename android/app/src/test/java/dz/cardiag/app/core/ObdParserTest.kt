package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdParserTest {
    @Test fun parsesRpm() = assertEquals(1726.0, requireNotNull(ObdParser.parseRpm("41 0C 1A F8 >")), 0.0)
    @Test fun parsesCoolant() = assertEquals(50.0, requireNotNull(ObdParser.parseCoolantCelsius("41 05 5A")), 0.0)
    @Test fun parsesSpeed() = assertEquals(80.0, requireNotNull(ObdParser.parseSpeed("41 0D 50")), 0.0)
    @Test fun parsesMaf() = assertEquals(5.14, requireNotNull(ObdParser.parseMaf("41 10 02 02")), 0.01)
    @Test fun parsesMap() = assertEquals(100.0, requireNotNull(ObdParser.parseMap("41 0B 64")), 0.0)
    @Test fun parsesVoltage() = assertEquals(12.0, requireNotNull(ObdParser.parseVoltage("41 42 2E E0")), 0.01)
    @Test fun parsesCommonDtcs() = assertEquals(listOf("P0300", "P0301"), ObdParser.parseDtc("43 03 00 03 01 00 00"))
    @Test fun parsesPendingDtcs() = assertEquals(listOf("P0301"), ObdParser.parseDtc("47 03 01 00 00"))
    @Test fun parsesPermanentDtcs() = assertEquals(listOf("P0301"), ObdParser.parseDtc("4A 03 01 00 00"))
    @Test fun parsesFreezeFrameDtcs() = assertEquals(listOf("P0301"), ObdParser.parseDtc("42 03 01 00 00", 0x42))

    @Test fun parsesSupportedPidRanges() {
        val first = ObdParser.parseSupportedPids("41 00 FF FF FF FF", 0x00)
        val second = ObdParser.parseSupportedPids("41 20 80 00 00 01", 0x20)
        val third = ObdParser.parseSupportedPids("41 40 80 00 00 01", 0x40)
        assertEquals(32, first.size)
        assertTrue(1 in first && 32 in first)
        assertTrue(0x21 in second && 0x40 in second)
        assertTrue(0x41 in third && 0x60 in third)
    }

    @Test fun parsesReadinessAndMil() {
        val r = ObdParser.parseReadiness("41 01 80 07 65 00 00 00")
        assertEquals(true, r.milOn)
        assertEquals(false, r.monitorsReady)
    }

    @Test fun ignoresNoDataAndInvalidResponses() {
        assertTrue(ObdParser.normalize("SEARCHING...\rNO DATA\r>").isBlank())
        assertNull(ObdParser.parseRpm("NO DATA>"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSupportedPidBase() {
        ObdParser.parseSupportedPids("41 00 FF FF FF FF", 0x10)
    }

    @Test fun validatesVin() {
        assertTrue(VinValidator.isValid("VF1AAAAA1BBBBBBBB"))
        assertTrue(!VinValidator.isValid("VF1IOQ"))
        assertTrue(!VinValidator.isValid("VF1IOQ12345678901"))
    }
}
