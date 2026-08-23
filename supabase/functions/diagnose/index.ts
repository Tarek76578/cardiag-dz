import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const H = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const J = (b: unknown, s = 200) => new Response(JSON.stringify(b), {
  status: s,
  headers: { ...H, "Content-Type": "application/json" },
});

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: H });
  if (req.method !== "POST") return J({ error: "Method not allowed" }, 405);

  try {
    const auth = req.headers.get("Authorization");
    if (!auth?.startsWith("Bearer ")) return J({ error: "Authorization required" }, 401);
    const token = auth.slice(7);
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: auth } } },
    );

    const { data: { user }, error: userError } = await supabase.auth.getUser(token);
    if (userError || !user) return J({ error: "Invalid authentication" }, 401);

    const key = Deno.env.get("openai_api_key");
    if (!key) return J({ error: "AI service is not configured" }, 503);

    const b = await req.json();
    const { session_id, codes = [], symptoms = {}, measurements = {}, vehicle = {}, language = "fr" } = b;
    if (!session_id) return J({ error: "session_id is required" }, 400);
    if (!["ar", "fr"].includes(language)) return J({ error: "language must be ar or fr" }, 400);
    if (!Array.isArray(codes)) return J({ error: "codes must be an array" }, 400);

    const { data: session, error: sessionError } = await supabase
      .from("diagnostic_sessions")
      .select("id,user_id")
      .eq("id", session_id)
      .eq("user_id", user.id)
      .maybeSingle();
    if (sessionError) return J({ error: "Could not validate diagnostic session" }, 500);
    if (!session) return J({ error: "Diagnostic session not found" }, 404);

    const lang = language === "ar" ? "Arabic" : "French";
    const prompt = `You are the diagnostic reasoning engine for CarDiag DZ. Give a cautious structured automotive diagnostic assessment. Do not claim certainty without evidence or recommend unsafe actions. Return JSON keys: summary, severity, likely_causes, recommended_tests, repair_guidance, safety_notes, confidence. Use ${lang}. Vehicle:${JSON.stringify(vehicle)} DTC:${JSON.stringify(codes)} Symptoms:${JSON.stringify(symptoms)} Measurements:${JSON.stringify(measurements)}`;

    const r = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({ model: "gpt-5-mini", input: prompt, text: { format: { type: "json_object" } } }),
    });
    if (!r.ok) {
      console.error("OpenAI failed", r.status, (await r.text()).slice(0, 1000));
      return J({ error: "AI diagnostic service failed" }, 502);
    }

    const d = await r.json();
    if (!d.output_text) return J({ error: "AI returned no diagnostic result" }, 502);
    let diagnosis;
    try { diagnosis = JSON.parse(d.output_text); } catch { diagnosis = { summary: d.output_text }; }

    const rows = (codes.length ? codes : [null]).map((code: string | null) => ({
      session_id,
      raw_code: code,
      symptoms,
      measurements,
      diagnosis,
    }));
    const { error: insertError } = await supabase.from("diagnostic_results").insert(rows);
    if (insertError) {
      console.error("diagnostic_results insert failed", insertError);
      return J({ error: "Diagnosis generated but could not be saved", session_id, language, diagnosis }, 500);
    }

    return J({ ok: true, session_id, user_id: user.id, language, diagnosis, saved: true });
  } catch (e) {
    console.error(e);
    return J({ error: "Invalid request" }, 400);
  }
});
