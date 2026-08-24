package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VinDecoderTest {
 @Test fun normalizesVin(){assertEquals("VF1AAAAA1BBBBBBBB",VinDecoder.normalize("vf1-aaaaa1-bbbbbbbb"))}
 @Test fun decodesWmiRegionAndYear(){val d=VinDecoder.decode("VF1AAAAA1BBBBBBBB");assertEquals("VF1",d.wmi);assertEquals("Europe",d.region);assertEquals(2010,d.modelYear);assertTrue(d.valid)}
}
