package dz.cardiag.app.core

data class VehicleHealth(val overall:Int,val engine:Int,val transmission:Int,val emissions:Int,val electrical:Int,val obd:Int,val reasons:List<String>)
object VehicleHealthEngine {
 fun score(dtcCount:Int,pendingCount:Int,permanentCount:Int,rpm:Double?,coolant:Double?,batteryVoltage:Double?,readinessReady:Boolean?,milOn:Boolean?):VehicleHealth{
  val reasons=mutableListOf<String>();var obd=100-dtcCount*12-pendingCount*6-permanentCount*10;if(milOn==true)obd-=8;obd=obd.coerceIn(0,100)
  var engine=100-dtcCount*10;rpm?.let{if(it<500||it>5000)engine-=12};coolant?.let{if(it<60||it>115)engine-=8};engine=engine.coerceIn(0,100)
  var emissions=100-dtcCount*7;if(readinessReady==false)emissions-=15;emissions=emissions.coerceIn(0,100)
  var electrical=100;if(batteryVoltage!=null&&(batteryVoltage<11.8||batteryVoltage>15.0)){electrical-=25;reasons+="Battery/charging voltage is outside the expected diagnostic range."};electrical=electrical.coerceIn(0,100)
  val transmission=100;if(dtcCount>0)reasons+="$dtcCount confirmed DTC(s) affect the health score.";if(permanentCount>0)reasons+="$permanentCount permanent DTC(s) require follow-up.";if(readinessReady==false)reasons+="Readiness monitors are not all ready."
  val overall=(engine*35+obd*25+emissions*20+electrical*15+transmission*5)/100
  return VehicleHealth(overall,engine,transmission,emissions,electrical,obd,reasons.distinct())
 }
}
