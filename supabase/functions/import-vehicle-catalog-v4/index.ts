import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const H={"Content-Type":"application/json"};
const clean=(x:any)=>String(x??"").trim();
const num=(x:any)=>{if(x===null||x===undefined||String(x).trim()==="")return null;const n=Number(String(x).replace(/,/g,""));return Number.isFinite(n)?n:null};
Deno.serve(async req=>{
 if(req.method!=="POST")return new Response(JSON.stringify({ok:false,error:"POST required"}),{status:405,headers:H});
 try{
  const body=await req.json(); const records=Array.isArray(body.records)?body.records:[]; if(!records.length)throw new Error("records is empty");
  const rows=records.map((r:any)=>({source_id:clean(r.source_id),make_name:clean(r.make_name),model_name:clean(r.model_name),generation_name:clean(r.generation_name)||null,model_year:num(r.model_year),engine_name:clean(r.engine_name)||null,engine_year:num(r.engine_year),engine_displacement:num(r.engine_displacement),engine_cylinders:num(r.engine_cylinders),engine_power_hp:num(r.engine_power_hp),transmission:clean(r.transmission)||null,drivetrain:clean(r.drivetrain)||null,fuel_type:clean(r.fuel_type)||null,source_url:clean(r.source_url)||null})).filter((r:any)=>r.source_id&&r.make_name&&r.model_name&&r.model_year);
  if(!rows.length)throw new Error("No valid canonical rows");
  let created=0;
  for(let i=0;i<rows.length;i+=200){const batch=rows.slice(i,i+200);const {data,error}=await db.from("vehicle_catalog_canonical").upsert(batch,{onConflict:"source_id",ignoreDuplicates:true}).select("id");if(error)throw error;created+=(data??[]).length;}
  return new Response(JSON.stringify({ok:true,received:records.length,valid:rows.length,canonicalCreated:created}),{headers:H});
 }catch(e){return new Response(JSON.stringify({ok:false,error:String(e)}),{status:500,headers:H})}
});
