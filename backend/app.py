import ast
import math
import os
import re
import unicodedata
from html import unescape
from urllib.parse import parse_qs, unquote, urlparse

import requests
from bs4 import BeautifulSoup
from flask import Flask, jsonify, request
from ai_provider import configured as ai_configured, plan as ai_plan

app = Flask(__name__)
SEARCH_TIMEOUT_SECONDS = float(os.getenv("EDDY_SEARCH_TIMEOUT", "12"))
SEARCH_LIMIT = max(3, min(int(os.getenv("EDDY_SEARCH_LIMIT", "8")), 12))
DDG_HTML_URL = "https://html.duckduckgo.com/html/"
WIKIPEDIA_API_URL = "https://es.wikipedia.org/w/api.php"
_SESSION = requests.Session()
_SESSION.headers.update({"User-Agent":"Mozilla/5.0 (Linux; Android 14) EDDY/0.5","Accept-Language":"es-NI,es;q=0.9,en;q=0.7"})

def _clean_text(value): return re.sub(r"\s+", " ", unescape(str(value or ""))).strip()
def _ascii_text(value):
    normalized=unicodedata.normalize("NFD",str(value or "").lower()); return "".join(c for c in normalized if unicodedata.category(c)!="Mn")
def _unwrap_ddg_url(href):
    value=str(href or "").strip(); value="https:"+value if value.startswith("//") else value; parsed=urlparse(value)
    if "duckduckgo.com" in parsed.netloc.lower() and parsed.path.startswith("/l/"): return unquote(parse_qs(parsed.query).get("uddg",[""])[0]).strip()
    return value
def _source_domain(url):
    try: return urlparse(url).netloc.lower().removeprefix("www.")
    except Exception: return ""
def _query_tokens(query): return {t for t in re.findall(r"[a-záéíóúñü0-9]{3,}",query.lower()) if t not in {"para","como","esta","este","esto","sobre","desde","hasta"}}
def _score_result(query,item):
    tokens=_query_tokens(query); hay=f"{item.get('title','')} {item.get('snippet','')}".lower(); score=sum(8 for t in tokens if t in hay); domain=_source_domain(item.get("url","")); return score+(5 if domain.endswith((".gov",".gob.ni",".edu",".org")) else 0)+(2 if domain else 0)
def _dedupe_rank(query,items):
    seen=set(); per={}; ranked=[]
    for item in sorted(items,key=lambda v:_score_result(query,v),reverse=True):
        url=str(item.get("url","")).strip(); domain=_source_domain(url)
        if not url.startswith(("http://","https://")) or url in seen or not domain or per.get(domain,0)>=2: continue
        seen.add(url); per[domain]=per.get(domain,0)+1; ranked.append(item)
        if len(ranked)>=SEARCH_LIMIT: break
    return ranked
def _search_duckduckgo(query):
    r=_SESSION.post(DDG_HTML_URL,data={"q":query,"kl":"us-es"},timeout=SEARCH_TIMEOUT_SECONDS); r.raise_for_status(); soup=BeautifulSoup(r.text,"html.parser"); out=[]
    for block in soup.select(".result"):
        a=block.select_one("a.result__a")
        if a is None: continue
        url=_unwrap_ddg_url(a.get("href","")); sn=block.select_one(".result__snippet")
        if url.startswith(("http://","https://")): out.append({"title":_clean_text(a.get_text(" ",strip=True)) or _source_domain(url),"url":url,"snippet":_clean_text(sn.get_text(" ",strip=True) if sn else "")})
    return out
def _search_wikipedia(query):
    r=_SESSION.get(WIKIPEDIA_API_URL,params={"action":"opensearch","search":query,"limit":min(SEARCH_LIMIT,6),"namespace":0,"format":"json"},timeout=SEARCH_TIMEOUT_SECONDS); r.raise_for_status(); d=r.json()
    if not isinstance(d,list) or len(d)<4:return []
    return [{"title":_clean_text(t),"url":str(u).strip(),"snippet":_clean_text(d[2][i] if isinstance(d[2],list) and i<len(d[2]) else "")} for i,(t,u) in enumerate(zip(d[1],d[3])) if str(u).startswith(("http://","https://"))]
def _search_web(query):
    out=[]
    try: out.extend(_search_duckduckgo(query))
    except requests.RequestException: app.logger.exception("DuckDuckGo search failed")
    if len(out)<3:
        try: out.extend(_search_wikipedia(query))
        except requests.RequestException: app.logger.exception("Wikipedia fallback failed")
    return _dedupe_rank(query,out)
def _build_reply(query,results):
    if not results:return "No encontré resultados confiables para esa búsqueda ahorita."
    snippets=[_clean_text(i.get("snippet","")) or f"Encontré la fuente {_clean_text(i.get('title',''))}" for i in results[:4]]
    reply=f"Encontré información sobre {query}. Lo más relevante: {snippets[0].rstrip('. ')}."
    if len(snippets)>1:reply+=" También encontré: "+". ".join(s.rstrip('. ') for s in snippets[1:3])+"."
    return (reply+" Te dejé las fuentes para verificar los detalles.")[:1800]
def _normalize_math_expression(message):
    text=_ascii_text(message).replace("×","*").replace("÷","/").replace(",",".")
    text=re.sub(r"\bdividido entre\b|\bdividido por\b"," / ",text); text=re.sub(r"\bmultiplicado por\b"," * ",text); text=re.sub(r"\bmas\b"," + ",text); text=re.sub(r"\bmenos\b"," - ",text); text=re.sub(r"\bentre\b"," / ",text); text=re.sub(r"\bpor\b"," * ",text); text=re.sub(r"\^","**",text)
    text=re.sub(r"^(?:eddy\s*[,.:;-]?\s*)?(?:cuanto\s+es|cuanto\s+da|calcula(?:me)?|resuelve|resultado\s+de)\s+","",text).strip(" ¿?!.:;")
    return re.sub(r"\s+","",text) if re.search(r"\d",text) and re.search(r"(?:\*\*|[+\-*/%])",text) and not re.search(r"[^0-9.+\-*/%()\s]",text) else None
def _eval_math_node(node):
    if isinstance(node,ast.Expression):return _eval_math_node(node.body)
    if isinstance(node,ast.Constant) and isinstance(node.value,(int,float)):return float(node.value)
    if isinstance(node,ast.UnaryOp) and isinstance(node.op,(ast.UAdd,ast.USub)):
        v=_eval_math_node(node.operand); return v if isinstance(node.op,ast.UAdd) else -v
    if isinstance(node,ast.BinOp):
        a,b=_eval_math_node(node.left),_eval_math_node(node.right)
        if isinstance(node.op,ast.Add):v=a+b
        elif isinstance(node.op,ast.Sub):v=a-b
        elif isinstance(node.op,ast.Mult):v=a*b
        elif isinstance(node.op,ast.Div):v=a/b
        elif isinstance(node.op,ast.Mod):v=a%b
        elif isinstance(node.op,ast.Pow):v=a**b
        else:raise ValueError()
        if not math.isfinite(v) or abs(v)>1e15:raise ValueError()
        return v
    raise ValueError()
def _try_calculate(message):
    e=_normalize_math_expression(message)
    if not e:return None
    try:
        v=_eval_math_node(ast.parse(e,mode="eval")); return str(int(round(v))) if abs(v-round(v))<1e-10 else f"{v:.10f}".rstrip("0").rstrip(".")
    except Exception:return None
def _looks_like_research(message):
    t=_ascii_text(message).strip(" ¿?!.:;"); return "?" in str(message) or t.startswith(("quien ","que ","cual ","cuando ","donde ","por que ","como ","cuanto ","explicame ","hablame ","noticias ","precio ","clima ","busca ","investiga ","averigua ","compara "))
def _status_payload(): return {"status":"ok","service":"EDDY Backend","engine":"eddy-hybrid","provider":"configurable-api+duckduckgo+wikipedia","mode":"planner+conversation+research+calculator","remote_model":ai_configured()}
@app.get("/")
def index():return jsonify(_status_payload())
@app.get("/health")
def health():return jsonify(_status_payload())
@app.post("/plan")
def plan_route():
    p=request.get_json(silent=True) or {}; message=str(p.get("message","")).strip(); memory=str(p.get("memory_context","")).strip()
    if not message:return jsonify(error="message is required"),400
    if not ai_configured():return jsonify(error="ai_not_configured"),503
    try:
        result=ai_plan(message,memory)
        return jsonify(result) if result else (jsonify(error="empty_ai_result"),502)
    except Exception:
        app.logger.exception("AI planner failed"); return jsonify(error="ai_provider_failed"),502
def _query_response(default_force_web):
    p=request.get_json(silent=True) or {}; message=str(p.get("message",p.get("query",""))).strip(); force=bool(p.get("force_web",default_force_web)); memory=str(p.get("memory_context","")).strip()
    if not message:return jsonify(error="message is required"),400
    calc=_try_calculate(message)
    if calc is not None:return jsonify(reply=f"El resultado es {calc}.",web_used=False,sources=[],kind="calculation")
    if not force and ai_configured():
        try:
            result=ai_plan(message,memory)
            if result and not result.get("actions"):
                return jsonify(reply=result.get("reply") or "Aquí estoy.",web_used=False,sources=[],kind="conversation")
        except Exception:app.logger.exception("AI conversation failed")
    if not force and not _looks_like_research(message):return jsonify(error="local_command_unknown"),422
    results=_search_web(message)
    if not results:return jsonify(reply=_build_reply(message,[]),web_used=False,sources=[],kind="research"),502
    sources=[{"title":i["title"][:180] or _source_domain(i["url"]) or "Fuente web","url":i["url"][:2000]} for i in results]
    return jsonify(reply=_build_reply(message,results),web_used=True,sources=sources,kind="research")
@app.post("/search")
def search():return _query_response(True)
@app.post("/chat")
def chat():return _query_response(False)
if __name__=="__main__":app.run(host="0.0.0.0",port=int(os.getenv("PORT","10000")))
