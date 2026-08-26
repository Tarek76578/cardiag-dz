package dz.cardiag.app.core

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class VehicleModelYearRow(val id:String,@SerialName("model_id") val modelId:String,@SerialName("generation_id") val generationId:String?=null,@SerialName("model_year") val modelYear:Int,val market:String?=null,@SerialName("data_status") val dataStatus:String?=null)
@Serializable data class VehicleYearEngineRow(@SerialName("model_year_id") val modelYearId:String,@SerialName("engine_id") val engineId:String,val market:String?=null,@SerialName("data_status") val dataStatus:String?=null)
@Serializable data class YearTrimLinkRow(@SerialName("model_year_id") val modelYearId:String,@SerialName("trim_id") val trimId:String)
@Serializable data class YearSpecLinkRow(@SerialName("model_year_id") val modelYearId:String,@SerialName("specification_id") val specificationId:String)
@Serializable data class YearEcuLinkRow(@SerialName("model_year_id") val modelYearId:String,@SerialName("ecu_id") val ecuId:String)
@Serializable data class VehicleGenerationRow(val id:String,@SerialName("model_id") val modelId:String,val name:String,val code:String?=null,@SerialName("year_from") val yearFrom:Int?=null,@SerialName("year_to") val yearTo:Int?=null,@SerialName("body_type") val bodyType:String?=null,@SerialName("platform_code") val platformCode:String?=null,@SerialName("description_fr") val descriptionFr:String?=null,@SerialName("description_ar") val descriptionAr:String?=null,@SerialName("image_url") val imageUrl:String?=null)
@Serializable data class VehicleEngineRow(val id:String,@SerialName("generation_id") val generationId:String,val name:String,@SerialName("engine_code") val engineCode:String?=null,@SerialName("fuel_type") val fuelType:String="unknown",@SerialName("displacement_cc") val displacementCc:Int?=null,val cylinders:Int?=null,val aspiration:String?=null,@SerialName("injection_type") val injectionType:String?=null,@SerialName("power_hp") val powerHp:Double?=null,@SerialName("power_kw") val powerKw:Double?=null,@SerialName("torque_nm") val torqueNm:Double?=null,@SerialName("transmission_types") val transmissionTypes:List<String> = emptyList(),@SerialName("year_from") val yearFrom:Int?=null,@SerialName("year_to") val yearTo:Int?=null,@SerialName("notes_fr") val notesFr:String?=null,@SerialName("notes_ar") val notesAr:String?=null)
@Serializable data class VehicleTrimRow(val id:String,@SerialName("generation_id") val generationId:String,@SerialName("engine_id") val engineId:String?=null,val name:String,val code:String?=null,val drivetrain:String?=null,val transmission:String?=null,val doors:Int?=null,val seats:Int?=null,@SerialName("year_from") val yearFrom:Int?=null,@SerialName("year_to") val yearTo:Int?=null,val market:String?=null)
@Serializable data class VehicleSpecificationRow(val id:String,@SerialName("generation_id") val generationId:String,@SerialName("engine_id") val engineId:String?=null,@SerialName("trim_id") val trimId:String?=null,val key:String,@SerialName("value_text") val valueText:String?=null,@SerialName("value_number") val valueNumber:Double?=null,val unit:String?=null)
@Serializable data class VehicleEcuRow(val id:String,@SerialName("generation_id") val generationId:String,@SerialName("engine_id") val engineId:String?=null,@SerialName("ecu_id") val ecuId:String,val required:Boolean=true,@SerialName("year_from") val yearFrom:Int?=null,@SerialName("year_to") val yearTo:Int?=null,val notes:String?=null)
@Serializable data class EcuModuleRow(val id:String,val manufacturer:String?=null,val name:String,val family:String?=null,@SerialName("ecu_type") val ecuType:String="other",@SerialName("part_numbers") val partNumbers:List<String> = emptyList(),val protocols:List<String> = emptyList())
@Serializable data class DiagnosticCodeVehicleRow(val id:String,@SerialName("code_id") val codeId:String,@SerialName("model_id") val modelId:String?=null,@SerialName("generation_id") val generationId:String?=null,@SerialName("engine_id") val engineId:String?=null,@SerialName("ecu_id") val ecuId:String?=null,val applicability:String="confirmed",@SerialName("notes_fr") val notesFr:String?=null,@SerialName("notes_ar") val notesAr:String?=null)
@Serializable data class DiagnosticCodeRow(val id:String,val code:String,val system:String?=null,@SerialName("title_fr") val titleFr:String?=null,@SerialName("title_ar") val titleAr:String?=null,@SerialName("description_fr") val descriptionFr:String?=null,@SerialName("description_ar") val descriptionAr:String?=null,val severity:String?=null,val category:String?=null,@SerialName("causes_fr") val causesFr:String?=null,@SerialName("causes_ar") val causesAr:String?=null,@SerialName("diagnostic_steps_fr") val diagnosticStepsFr:String?=null,@SerialName("diagnostic_steps_ar") val diagnosticStepsAr:String?=null,@SerialName("repair_summary_fr") val repairSummaryFr:String?=null,@SerialName("repair_summary_ar") val repairSummaryAr:String?=null)
@Serializable private data class VehicleModelIdentityRow(val id:String,@SerialName("make_id") val makeId:String,val name:String)

data class VehicleYearProfile(val year:Int,val generation:VehicleGenerationRow?,val engines:List<VehicleEngineRow>,val trims:List<VehicleTrimRow>,val specifications:List<VehicleSpecificationRow>,val ecus:List<Pair<VehicleEcuRow,EcuModuleRow>>,val diagnostics:List<DiagnosticCodeRow>)

class VehicleRepository {
    private val supabase = SupabaseClient.client
    private val profileCache = mutableMapOf<String,List<VehicleYearProfile>>()
    private fun containsYear(from:Int?,to:Int?,year:Int)=(from==null||year>=from)&&(to==null||year<=to)

    private suspend fun resolveModelId(modelId:String):String {
        val identity=runCatching{supabase.from("vehicle_models").select(Columns.list("id","make_id","name")){filter{eq("id",modelId)}}.decodeSingle<VehicleModelIdentityRow>()}.getOrNull()?:return modelId
        val candidates=runCatching{supabase.from("vehicle_models").select(Columns.list("id","make_id","name")){filter{eq("make_id",identity.makeId);eq("name",identity.name)}}.decodeList<VehicleModelIdentityRow>()}.getOrDefault(emptyList())
        if(candidates.size<=1)return modelId
        val ids=candidates.map{it.id}
        val yearRows=runCatching{supabase.from("vehicle_model_years").select(Columns.list("id","model_id","generation_id","model_year","market","data_status")){filter{inList("model_id",ids)}}.decodeList<VehicleModelYearRow>()}.getOrDefault(emptyList())
        val best=yearRows.groupingBy{it.modelId}.eachCount().maxByOrNull{it.value}?.key
        return best?:modelId
    }

    suspend fun getVehicleProfile(modelId:String):List<VehicleYearProfile>{
        require(modelId.isNotBlank())
        profileCache[modelId]?.let{return it}
        val resolved=resolveModelId(modelId)
        profileCache[resolved]?.let{profileCache[modelId]=it;return it}
        val years=supabase.from("vehicle_model_years").select(Columns.list("id","model_id","generation_id","model_year","market","data_status")){filter{eq("model_id",resolved)}}.decodeList<VehicleModelYearRow>()
        if(years.isEmpty())return emptyList()
        val generations=supabase.from("vehicle_generations").select(Columns.list("id","model_id","name","code","year_from","year_to","body_type","platform_code","description_fr","description_ar","image_url")){filter{eq("model_id",resolved)}}.decodeList<VehicleGenerationRow>()
        val generationIds=generations.map{it.id}.toSet()
        val allEngines=if(generationIds.isEmpty())emptyList() else supabase.from("vehicle_engines").select(Columns.list("id","generation_id","name","engine_code","fuel_type","displacement_cc","cylinders","aspiration","injection_type","power_hp","power_kw","torque_nm","transmission_types","year_from","year_to","notes_fr","notes_ar")){filter{inList("generation_id",generationIds.toList())}}.decodeList<VehicleEngineRow>()
        val allTrims=if(generationIds.isEmpty())emptyList() else supabase.from("vehicle_trims").select(Columns.list("id","generation_id","engine_id","name","code","drivetrain","transmission","doors","seats","year_from","year_to","market")){filter{inList("generation_id",generationIds.toList())}}.decodeList<VehicleTrimRow>()
        val allSpecs=if(generationIds.isEmpty())emptyList() else supabase.from("vehicle_specifications").select(Columns.list("id","generation_id","engine_id","trim_id","key","value_text","value_number","unit")){filter{inList("generation_id",generationIds.toList())}}.decodeList<VehicleSpecificationRow>()
        val allEcus=if(generationIds.isEmpty())emptyList() else supabase.from("vehicle_ecus").select(Columns.list("id","generation_id","engine_id","ecu_id","required","year_from","year_to","notes")){filter{inList("generation_id",generationIds.toList())}}.decodeList<VehicleEcuRow>()
        val moduleIds=allEcus.map{it.ecuId}.distinct()
        val modules=if(moduleIds.isEmpty())emptyList() else supabase.from("ecu_modules").select(Columns.list("id","manufacturer","name","family","ecu_type","part_numbers","protocols")){filter{inList("id",moduleIds)}}.decodeList<EcuModuleRow>()
        val dLinks=supabase.from("diagnostic_code_vehicles").select(Columns.list("id","code_id","model_id","generation_id","engine_id","ecu_id","applicability","notes_fr","notes_ar")){filter{eq("model_id",resolved)}}.decodeList<DiagnosticCodeVehicleRow>()
        val codeIds=dLinks.map{it.codeId}.distinct()
        val codes=if(codeIds.isEmpty())emptyList() else supabase.from("diagnostic_codes").select(Columns.list("id","code","system","title_fr","title_ar","description_fr","description_ar","severity","category","causes_fr","causes_ar","diagnostic_steps_fr","diagnostic_steps_ar","repair_summary_fr","repair_summary_ar")){filter{inList("id",codeIds)}}.decodeList<DiagnosticCodeRow>()
        val yearIds=years.map{it.id}
        val engineLinks=supabase.from("vehicle_year_engines").select(Columns.list("model_year_id","engine_id","market","data_status")){filter{inList("model_year_id",yearIds)}}.decodeList<VehicleYearEngineRow>().groupBy{it.modelYearId}
        val trimLinks=supabase.from("vehicle_year_trim_links").select(Columns.list("model_year_id","trim_id")){filter{inList("model_year_id",yearIds)}}.decodeList<YearTrimLinkRow>().groupBy{it.modelYearId}
        val specLinks=supabase.from("vehicle_year_specification_links").select(Columns.list("model_year_id","specification_id")){filter{inList("model_year_id",yearIds)}}.decodeList<YearSpecLinkRow>().groupBy{it.modelYearId}
        val ecuLinks=supabase.from("vehicle_year_ecu_links").select(Columns.list("model_year_id","ecu_id")){filter{inList("model_year_id",yearIds)}}.decodeList<YearEcuLinkRow>().groupBy{it.modelYearId}
        val result=years.sortedByDescending{it.modelYear}.map{year->
            val generation=year.generationId?.let{id->generations.firstOrNull{it.id==id&&containsYear(it.yearFrom,it.yearTo,year.modelYear)}}?:generations.firstOrNull{containsYear(it.yearFrom,it.yearTo,year.modelYear)}
            val gid=generation?.id
            val directIds=engineLinks[year.id].orEmpty().map{it.engineId}.toSet()
            val engines=(if(directIds.isNotEmpty())allEngines.filter{it.id in directIds}else allEngines).filter{(gid==null||it.generationId==gid)&&containsYear(it.yearFrom,it.yearTo,year.modelYear)}
            val engineIds=engines.map{it.id}.toSet()
            val directTrimIds=trimLinks[year.id].orEmpty().map{it.trimId}.toSet()
            val trims=(if(directTrimIds.isNotEmpty())allTrims.filter{it.id in directTrimIds}else allTrims).filter{(gid==null||it.generationId==gid)&&(it.engineId==null||it.engineId in engineIds)&&containsYear(it.yearFrom,it.yearTo,year.modelYear)}.distinctBy{it.id}
            val trimIds=trims.map{it.id}.toSet()
            val directSpecIds=specLinks[year.id].orEmpty().map{it.specificationId}.toSet()
            val specs=(if(directSpecIds.isNotEmpty())allSpecs.filter{it.id in directSpecIds}else allSpecs).filter{(gid==null||it.generationId==gid)&&(it.engineId==null||it.engineId in engineIds)&&(it.trimId==null||it.trimId in trimIds)}.distinctBy{it.id}
            val directEcuIds=ecuLinks[year.id].orEmpty().map{it.ecuId}.toSet()
            val ecuRows=(if(directEcuIds.isNotEmpty())allEcus.filter{it.id in directEcuIds}else allEcus).filter{(gid==null||it.generationId==gid)&&(it.engineId==null||it.engineId in engineIds)&&containsYear(it.yearFrom,it.yearTo,year.modelYear)}
            val ecus=ecuRows.mapNotNull{ecu->modules.firstOrNull{it.id==ecu.ecuId}?.let{ecu to it}}
            val diagnostics=dLinks.filter{(it.generationId==null||it.generationId==gid)&&(it.engineId==null||it.engineId in engineIds)}.mapNotNull{link->codes.firstOrNull{it.id==link.codeId}}.distinctBy{it.id}
            VehicleYearProfile(year.modelYear,generation,engines,trims,specs,ecus,diagnostics)
        }
        profileCache[resolved]=result
        profileCache[modelId]=result
        return result
    }

    @Serializable private data class VehicleYearIdRow(val id:String,@SerialName("model_year") val modelYear:Int)
    suspend fun getVehicleYearIds(modelId:String):List<Pair<String,Int>>=supabase.from("vehicle_model_years").select(Columns.list("id","model_year")){filter{eq("model_id",modelId)}}.decodeList<VehicleYearIdRow>().sortedByDescending{it.modelYear}.map{it.id to it.modelYear}
}
