package dz.cardiag.app.core

import kotlinx.serialization.Serializable

@Serializable data class CorrelationObservation(val pid:String,val value:Double,val unit:String?=null,val min:Double?=null,val max:Double?=null)
@Serializable data class CorrelationFinding(val title:String,val reason:String,val severity:String,val confidence:Int,val supportingPids:List<String>)
@Serializable data class NextBestTest(val test:String,val reason:String,val priority:Int)

data class DiagnosticCorrelationResult(val findings:List<CorrelationFinding>,val nextBestTests:List<NextBestTest>)

object DiagnosticCorrelation {
 fun correlate(dtc:String,observations:List<CorrelationObservation>):List<CorrelationFinding>{return correlateAll(listOf(dtc),observations).findings}
 fun correlateAll(dtcs:List<String>,observations:List<CorrelationObservation>):DiagnosticCorrelationResult{
  val v=observations.associateBy{it.pid.uppercase()};fun value(pid:String)=v[pid]?.value;val findings=mutableListOf<CorrelationFinding>();val codes=dtcs.map{it.uppercase()}.distinct()
  if(codes.any{it in listOf("P0300","P0301","P0302","P0303","P0304")} && codes.any{it in listOf("P0171","P0174")} )findings+=CorrelationFinding("Misfire + lean condition","Multiple codes can share an air/fuel root cause; verify fuel trims, intake leaks and MAF before replacing ignition components.","high",82,listOf("10","0B"))
  for(dtc in codes)when(dtc){
   "P0300","P0301","P0302","P0303","P0304"->{val rpm=value("0C");val maf=value("10");val coolant=value("05");if(maf!=null&&maf<2)findings+=CorrelationFinding("Low MAF signal","Sampled MAF is unusually low; inspect intake leaks, contamination and wiring.","medium",72,listOf("10"));if(coolant!=null&&coolant<60)findings+=CorrelationFinding("Cold-engine condition","Warm the engine before judging persistent misfire behavior.","low",78,listOf("05"));if(rpm!=null&&rpm<500)findings+=CorrelationFinding("Low/unstable idle","RPM is below a normal idle region at the sampled point.","medium",68,listOf("0C"))}
   "P0171","P0174"->{val maf=value("10");val map=value("0B");if(maf!=null&&maf<2)findings+=CorrelationFinding("Possible unmetered air","Low MAF supports an intake-leak hypothesis; verify with fuel trims/smoke test.","medium",70,listOf("10"));if(map!=null&&map>90)findings+=CorrelationFinding("High MAP","Compare MAP with load/throttle and inspect vacuum/boost conditions.","medium",62,listOf("0B"))}
   "P0420","P0430"->{val coolant=value("05");if(coolant!=null&&coolant<70)findings+=CorrelationFinding("Catalyst test before warm-up","Repeat catalyst-efficiency assessment at operating temperature.","low",82,listOf("05"))}
  }
  val tests=mutableListOf<NextBestTest>();if(codes.any{it.startsWith("P03")})tests+=NextBestTest("Freeze-frame + fuel trims + ignition/fueling checks","Distinguish air/fuel, ignition and mechanical causes before parts replacement.",100);if(codes.any{it in listOf("P0420","P0430")})tests+=NextBestTest("Warm-engine O2/catalyst verification","Catalyst efficiency needs operating-temperature evidence.",90);if(codes.size>1)tests+=NextBestTest("Cross-code correlation scan","Multiple DTCs may share a common power, ground, air/fuel or communication cause.",95)
  return DiagnosticCorrelationResult(findings.distinctBy{it.title}.sortedByDescending{it.confidence},tests.distinctBy{it.test}.sortedByDescending{it.priority})
 }
}
