package dz.cardiag.app.core

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.call.body
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable data class DiagnosticSessionInsert(@SerialName("user_id") val userId:String,@SerialName("vehicle_model_id") val vehicleModelId:String?=null,@SerialName("user_vehicle_id") val userVehicleId:String?=null,val complaint:String?=null,val language:String="fr",val status:String="created")
@Serializable data class DiagnosticSession(val id:String,@SerialName("user_id") val userId:String?=null)
@Serializable data class DiagnosticMeasurementInsert(@SerialName("session_id") val sessionId:String,@SerialName("pid_id") val pidId:String?=null,val name:String?=null,@SerialName("value_numeric") val valueNumeric:Double?=null,@SerialName("value_text") val valueText:String?=null,val unit:String?=null,val source:String="obd")

class DiagnosticService {
 private val supabase=SupabaseClient.client
 private suspend fun ensureUserId():String{supabase.auth.currentUserOrNull()?.let{return it.id};runCatching{supabase.auth.signInAnonymously()};return supabase.auth.currentUserOrNull()?.id?:error("Unable to create a guest session")}
 suspend fun createSession(vehicleModelId:String?,userVehicleId:String?,complaint:String,language:String):DiagnosticSession{require(language in setOf("ar","fr","en")){"language must be ar, fr or en"};val userId=ensureUserId();return withTimeout(10_000){supabase.from("diagnostic_sessions").insert(DiagnosticSessionInsert(userId,vehicleModelId,userVehicleId,complaint.ifBlank{null},language,"running")){select(Columns.list("id","user_id"))}.decodeSingle()}}
 suspend fun saveMeasurements(sessionId:String,measurements:List<DiagnosticMeasurementInsert>){if(measurements.isEmpty())return;withTimeout(10_000){supabase.from("diagnostic_measurements").insert(measurements)}}
 suspend fun diagnose(sessionId:String,codes:List<String>=emptyList(),symptoms:JsonObject=buildJsonObject{},measurements:JsonObject=buildJsonObject{},vehicle:JsonObject=buildJsonObject{},language:String="fr"):JsonObject{
  require(language in setOf("ar","fr","en")){"language must be ar, fr or en"};val normalizedCodes=codes.map{it.trim().uppercase()}.filter{it.matches(Regex("[PBCU][0-3][0-9A-F]{3}"))}.distinct();require(normalizedCodes.isNotEmpty()){ "At least one valid DTC is required"};val payload=buildJsonObject{put("session_id",sessionId);put("codes",JsonArray(normalizedCodes.map{JsonPrimitive(it)}));put("symptoms",symptoms);put("measurements",measurements);put("vehicle",vehicle);put("language",language)};var last:Throwable?=null
  repeat(3){attempt->try{return withTimeout(45_000){val response=supabase.functions.invoke(function="diagnose",body=payload);val body=response.body<JsonObject>();if(body["error"]!=null||body["ok"]?.toString()=="false")throw IllegalStateException(body["error"]?.toString()?:"Diagnostic service error");body}}catch(e:Throwable){last=e;Log.e("CarDiag-Diagnostic","diagnose attempt ${attempt+1} failed: ${e.message}",e);if(attempt<2)delay(800L*(attempt+1))}}
  throw IllegalStateException("Diagnostic AI unavailable: ${last?.message?:"unknown error"}",last)
 }
 suspend fun runDiagnostic(vehicleModelId:String?,userVehicleId:String?,complaint:String,language:String,codes:List<String>=emptyList(),symptoms:JsonObject=buildJsonObject{},measurements:JsonObject=buildJsonObject{},vehicle:JsonObject=buildJsonObject{}):JsonObject{val session=createSession(vehicleModelId,userVehicleId,complaint,language);return diagnose(session.id,codes,symptoms,measurements,vehicle,language)}
}
