package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VinDecoderTest {
    @Test fun normalizesVin() {
        assertEquals("VF1AAAAA41BBBBBBB", VinDecoder.normalize("vf1-aaaaa41-bbbbbbb"))
    }

    @Test fun decodesWmiRegionAndYear() {
        val d = VinDecoder.decode("VF1AAAAA41BBBBBBB")
        assertEquals("VF1", d.wmi)
        assertEquals("Europe", d.region)
        assertEquals(2031, d.modelYear)
        assertTrue(d.checkDigitValid)
        assertTrue(d.valid)
    }
}
