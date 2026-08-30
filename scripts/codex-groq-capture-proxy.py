#!/usr/bin/env python3
"""Codex -> Groq Responses compatibility proxy with proactive TPM/context protection."""
import http.client, json, os, re, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HOST="127.0.0.1"; PORT=int(os.environ.get("CAPTURE_PROXY_PORT","8787")); UPSTREAM_HOST="api.groq.com"
CAPTURE=Path(os.environ.get("CAPTURE_FILE","/tmp/codex-groq-request.json")); ADAPTED_CAPTURE=Path(os.environ.get("ADAPTED_CAPTURE_FILE","/tmp/codex-groq-adapted-request.json")); RESPONSE_CAPTURE=Path(os.environ.get("RESPONSE_CAPTURE_FILE","/tmp/codex-groq-response.json")); MAX_CAPTURE=2_000_000
PREFERRED_MODEL=os.environ.get("GROQ_PRIMARY_MODEL",""); FALLBACK_MODEL=os.environ.get("GROQ_FALLBACK_MODEL","openai/gpt-oss-20b")
MAX_RETRIES=int(os.environ.get("GROQ_RATE_LIMIT_RETRIES","2")); DEFAULT_RETRY_SECONDS=int(os.environ.get("GROQ_DEFAULT_RETRY_SECONDS","45")); MAX_OUTPUT_TOKENS=int(os.environ.get("GROQ_MAX_OUTPUT_TOKENS","1500")); TPM_LIMIT=int(os.environ.get("GROQ_TPM_LIMIT","8000")); TPM_SAFETY_MARGIN=int(os.environ.get("GROQ_TPM_SAFETY_MARGIN","500")); MAX_INPUT_TOKENS=int(os.environ.get("GROQ_MAX_INPUT_TOKENS","5500"))
UNSUPPORTED_REQUEST_FIELDS={"previous_response_id","store","truncation","include","safety_identifier","prompt_cache_key","prompt"}; CODEX_ONLY_FIELDS={"client_metadata","access_programs"}

def sanitize(v):
    if isinstance(v,dict): return {k:("[REDACTED]" if k.lower() in {"authorization","api_key","apikey","key","token"} else sanitize(x)) for k,x in v.items()}
    if isinstance(v,list): return [sanitize(x) for x in v]
    return v

def token_estimate(v):
    raw=v if isinstance(v,(bytes,bytearray)) else json.dumps(v,ensure_ascii=False,separators=(",",":" )).encode()
    return max(1,(len(raw)+3)//4)

def adapt_request(parsed):
    if not isinstance(parsed,dict): raise ValueError("Codex request body is not a JSON object")
    a=dict(parsed); removed=[]
    for k in list(a):
        if k in UNSUPPORTED_REQUEST_FIELDS or k in CODEX_ONLY_FIELDS: removed.append(k); a.pop(k,None)
    reasoning=a.get("reasoning")
    if isinstance(reasoning,dict) and "summary" in reasoning:
        r=dict(reasoning); r.pop("summary",None)
        if r:a["reasoning"]=r
        else:a.pop("reasoning",None)
        removed.append("reasoning.summary")
    tools=a.get("tools")
    if isinstance(tools,list):
        kept=[t for t in tools if isinstance(t,dict) and t.get("type")!="namespace"]
        if kept:a["tools"]=kept
        else:a.pop("tools",None)
    return a,removed

def compact_input(obj, max_tokens):
    """Keep system/developer instructions and the newest conversation/tool turns."""
    inp=obj.get("input")
    if not isinstance(inp,list): return obj,False
    if token_estimate(obj)<=max_tokens: return obj,False
    keep=[]; recent=[]
    for item in inp:
        role=item.get("role") if isinstance(item,dict) else None
        if role in ("system","developer"): keep.append(item)
        else: recent.append(item)
    # Add newest items first until the whole request fits. This avoids inventing
    # content and preserves the current tool result/user turn whenever possible.
    selected=[]
    for item in reversed(recent):
        trial=dict(obj); trial["input"]=keep+list(reversed(selected+[item]))
        if token_estimate(trial)<=max_tokens: selected.append(item)
        else: break
    new=dict(obj); new["input"]=keep+list(reversed(selected))
    return new,new["input"]!=inp

def header(headers,name):
    n=name.lower()
    for k,v in headers.items():
        if k.lower()==n:return v
    return None

def duration(v):
    if not v:return None
    m=re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)\s*(ms|s|m)?",str(v).strip().lower())
    if not m:return None
    n=float(m.group(1)); u=m.group(2) or "s"
    return n/1000 if u=="ms" else n*60 if u=="m" else n

def retry_seconds(headers,body):
    for n in ("retry-after","x-ratelimit-reset-tokens"):
        d=duration(header(headers,n))
        if d is not None:return max(1,min(300,int(d+1)))
    m=re.search(r"try again in\s+([0-9]+(?:\.[0-9]+)?)\s*seconds",body.decode("utf-8","replace"),re.I)
    return max(1,min(300,int(float(m.group(1))+1))) if m else DEFAULT_RETRY_SECONDS

def send(body,headers):
    c=http.client.HTTPSConnection(UPSTREAM_HOST,443,timeout=180)
    try:
        c.request("POST","/openai/v1/responses",body=body,headers=headers); r=c.getresponse(); return r.status,r.reason,dict(r.getheaders()),r.read()
    finally:c.close()

class Handler(BaseHTTPRequestHandler):
    protocol_version="HTTP/1.1"
    def log_message(self,fmt,*args):print("CAPTURE_PROXY "+(fmt%args),flush=True)
    def do_POST(self):
        raw=self.rfile.read(int(self.headers.get("Content-Length","0")))
        try:
            parsed=json.loads(raw); CAPTURE.write_text(json.dumps(sanitize(parsed),indent=2,ensure_ascii=False)[:MAX_CAPTURE],encoding="utf-8"); adapted,removed=adapt_request(parsed)
        except Exception as e:
            self.send_error(400,"invalid Codex request JSON"); print(f"ADAPTER_REQUEST_ERROR={type(e).__name__}: {e}",flush=True); return
        headers={k:self.headers[k] for k in ("Authorization","Content-Type","Accept","OpenAI-Beta","X-Client-Request-Id") if self.headers.get(k)}; headers["Host"]=UPSTREAM_HOST; headers["Connection"]="close"
        models=[adapted.get("model") or PREFERRED_MODEL]
        if FALLBACK_MODEL and FALLBACK_MODEL not in models:models.append(FALLBACK_MODEL)
        last=(502,"Bad Gateway",{},b"")
        for mi,model in enumerate(models):
            if not model:continue
            obj=dict(adapted); obj["model"]=model
            obj["max_output_tokens"]=min(obj.get("max_output_tokens",MAX_OUTPUT_TOKENS),MAX_OUTPUT_TOKENS) if isinstance(obj.get("max_output_tokens",MAX_OUTPUT_TOKENS),int) else MAX_OUTPUT_TOKENS
            attempts=0
            while True:
                # Reserve a safety margin under the 8K combined TPM ceiling.
                budget=max(512,TPM_LIMIT-TPM_SAFETY_MARGIN-obj["max_output_tokens"])
                obj,compacted=compact_input(obj,min(MAX_INPUT_TOKENS,budget))
                body=json.dumps(obj,ensure_ascii=False,separators=(",",":")).encode()
                estimated=token_estimate(body)
                if compacted: print(f"GROQ_CONTEXT_COMPACTED estimated_tokens={estimated} max_input={min(MAX_INPUT_TOKENS,budget)}",flush=True)
                if estimated+obj["max_output_tokens"]>TPM_LIMIT-TPM_SAFETY_MARGIN:
                    obj["max_output_tokens"]=max(512,(TPM_LIMIT-TPM_SAFETY_MARGIN-estimated))
                    body=json.dumps(obj,ensure_ascii=False,separators=(",",":")).encode(); estimated=token_estimate(body)
                if estimated+obj["max_output_tokens"]>TPM_LIMIT-TPM_SAFETY_MARGIN:
                    # Last safe compaction pass with the reduced completion budget.
                    obj,compacted=compact_input(obj,max(512,TPM_LIMIT-TPM_SAFETY_MARGIN-obj["max_output_tokens"]))
                    body=json.dumps(obj,ensure_ascii=False,separators=(",",":")).encode(); estimated=token_estimate(body)
                headers["Content-Length"]=str(len(body)); print(f"GROQ_REQUEST model={model} attempt={attempts+1} input_estimate={estimated} output_budget={obj['max_output_tokens']} total_budget={estimated+obj['max_output_tokens']}",flush=True)
                try:status,reason,rh,rb=send(body,headers)
                except Exception as e:self.send_error(502,"capture proxy upstream error"); print(f"UPSTREAM_FORWARD_ERROR={type(e).__name__}: {e}",flush=True); return
                last=(status,reason,rh,rb); print(f"GROQ_LIMIT remaining_tokens={header(rh,'x-ratelimit-remaining-tokens') or 'unknown'} reset_tokens={header(rh,'x-ratelimit-reset-tokens') or 'unknown'}",flush=True)
                if status==429:
                    wait=retry_seconds(rh,rb)
                    if attempts<MAX_RETRIES:attempts+=1; print(f"GROQ_WAIT_RATE_LIMIT seconds={wait}",flush=True); time.sleep(wait); continue
                    if mi+1<len(models):print(f"GROQ_FALLBACK from={model} to={models[mi+1]}",flush=True)
                    break
                if status in (500,502,503) and attempts<MAX_RETRIES:attempts+=1; time.sleep(min(60,DEFAULT_RETRY_SECONDS*attempts)); continue
                if status==413 and obj["max_output_tokens"]>512 and attempts<MAX_RETRIES:obj["max_output_tokens"]=max(512,obj["max_output_tokens"]//2); attempts+=1; print(f"GROQ_413_REDUCE_OUTPUT to={obj['max_output_tokens']}",flush=True); continue
                break
            if last[0]<400 or mi+1>=len(models):break
        status,reason,rh,rb=last; RESPONSE_CAPTURE.write_text(rb[:MAX_CAPTURE].decode("utf-8","replace"),encoding="utf-8"); self.send_response(status,reason)
        for k,v in rh.items():
            if k.lower() not in {"connection","keep-alive","transfer-encoding"}:self.send_header(k,v)
        self.send_header("Connection","close"); self.end_headers(); self.wfile.write(rb); self.wfile.flush(); print(f"UPSTREAM_RESPONSE status={status} bytes={len(rb)}",flush=True)

class Server(ThreadingHTTPServer):daemon_threads=True; allow_reuse_address=True
if __name__=="__main__":Server((HOST,PORT),Handler).serve_forever()
