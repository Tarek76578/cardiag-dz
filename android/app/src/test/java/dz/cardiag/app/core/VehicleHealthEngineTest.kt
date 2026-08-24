package dz.cardiag.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleHealthEngineTest {
 @Test fun healthyVehicleScoresHigh(){val h=VehicleHealthEngine.score(0,0,0,800.0,90.0,13.8,true,false);assertTrue(h.overall>=95)}
 @Test fun faultsReduceScore(){val h=VehicleHealthEngine.score(2,1,1,450.0,120.0,11.5,false,true);assertTrue(h.overall<80);assertTrue(h.reasons.isNotEmpty())}
 @Test fun scoreIsBounded(){val h=VehicleHealthEngine.score(99,99,99,null,null,5.0,false,true);assertTrue(h.overall in 0..100);assertEquals(0,h.obd)}
}
