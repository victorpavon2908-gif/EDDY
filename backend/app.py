import ast
import math
import os
import re
import unicodedata
from concurrent.futures import ThreadPoolExecutor, as_completed
from html import unescape
from urllib.parse import parse_qs, unquote, urlparse

import requests
from bs4 import BeautifulSoup
from flask import Flask, jsonify, request
from ai_provider import configured as ai_configured, plan as ai_plan, answer_from_web

app = Flask(__name__)
SEARCH_TIMEOUT_SECONDS = float(os.getenv("EDDY_SEARCH_TIMEOUT", "7"))
PAGE_TIMEOUT_SECONDS = float(os.getenv("EDDY_PAGE_TIMEOUT", "5"))
SEARCH_LIMIT = max(4, min(int(os.getenv("EDDY_SEARCH_LIMIT", "8")), 12))
PAGE_FETCH_LIMIT = max(2, min(int(os.getenv("EDDY_PAGE_FETCH_LIMIT", "5")), 6))
DDG_HTML_URL = "https://html.duckduckgo.com/html/"
WIKIPEDIA_API_URL = "https://es.wikipedia.org/w/api.php"
_SESSION = requests.Session()
_SESSION.headers.update({
    "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 EDDY/0.5",
    "Accept-Language": "es-NI,es;q=0.9,en;q=0.7",
})


def _clean_text(value):
    return re.sub(r"\s+", " ", unescape(str(value or ""))).strip()


def _ascii_text(value):
    normalized = unicodedata.normalize("NFD", str(value or "").lower())
    return "".join(c for c in normalized if unicodedata.category(c) != "Mn")


def _unwrap_ddg_url(href):
    value = str(href or "").strip()
    if value.startswith("//"):
        value = "https:" + value
    parsed = urlparse(value)
    if "duckduckgo.com" in parsed.netloc.lower() and parsed.path.startswith("/l/"):
        return unquote(parse_qs(parsed.query).get("uddg", [""])[0]).strip()
    return value


def _source_domain(url):
    try:
        return urlparse(url).netloc.lower().removeprefix("www.")
    except Exception:
        return ""


def _query_tokens(query):
    stop = {"para", "como", "esta", "este", "esto", "sobre", "desde", "hasta", "dime", "busca", "investiga"}
    return {t for t in re.findall(r"[a-záéíóúñü0-9]{3,}", query.lower()) if t not in stop}


def _score_result(query, item):
    tokens = _query_tokens(query)
    hay = f"{item.get('title', '')} {item.get('snippet', '')} {item.get('excerpt', '')}".lower()
    score = sum(8 for t in tokens if t in hay)
    domain = _source_domain(item.get("url", ""))
    if domain.endswith((".gov", ".gob.ni", ".edu", ".org")):
        score += 5
    if domain:
        score += 2
    if item.get("excerpt"):
        score += 3
    return score


def _dedupe_rank(query, items):
    seen = set()
    per_domain = {}
    ranked = []
    for item in sorted(items, key=lambda v: _score_result(query, v), reverse=True):
        url = str(item.get("url", "")).strip()
        domain = _source_domain(url)
        if not url.startswith(("http://", "https://")) or url in seen or not domain:
            continue
        if per_domain.get(domain, 0) >= 2:
            continue
        seen.add(url)
        per_domain[domain] = per_domain.get(domain, 0) + 1
        ranked.append(item)
        if len(ranked) >= SEARCH_LIMIT:
            break
    return ranked


def _search_duckduckgo(query):
    response = _SESSION.post(DDG_HTML_URL, data={"q": query, "kl": "us-es"}, timeout=SEARCH_TIMEOUT_SECONDS)
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")
    output = []
    for block in soup.select(".result"):
        anchor = block.select_one("a.result__a")
        if anchor is None:
            continue
        url = _unwrap_ddg_url(anchor.get("href", ""))
        snippet = block.select_one(".result__snippet")
        if url.startswith(("http://", "https://")):
            output.append({
                "title": _clean_text(anchor.get_text(" ", strip=True)) or _source_domain(url),
                "url": url,
                "snippet": _clean_text(snippet.get_text(" ", strip=True) if snippet else ""),
            })
    return output


def _search_wikipedia(query):
    response = _SESSION.get(
        WIKIPEDIA_API_URL,
        params={"action": "opensearch", "search": query, "limit": min(SEARCH_LIMIT, 6), "namespace": 0, "format": "json"},
        timeout=SEARCH_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    data = response.json()
    if not isinstance(data, list) or len(data) < 4:
        return []
    return [
        {
            "title": _clean_text(title),
            "url": str(url).strip(),
            "snippet": _clean_text(data[2][i] if isinstance(data[2], list) and i < len(data[2]) else ""),
        }
        for i, (title, url) in enumerate(zip(data[1], data[3]))
        if str(url).startswith(("http://", "https://"))
    ]


def _search_variants(query):
    base = _clean_text(query)
    ascii_query = _ascii_text(base)
    stripped = re.sub(r"\b(?:eddy|por favor|porfa|busca|investiga|averigua|dime|decime|quiero saber|en internet|en la web)\b", " ", ascii_query)
    stripped = re.sub(r"\s+", " ", stripped).strip()
    variants = [base]
    if stripped and stripped.lower() != base.lower():
        variants.append(stripped)
    return variants[:2]


def _search_web(query):
    collected = []
    variants = _search_variants(query)
    jobs = []
    with ThreadPoolExecutor(max_workers=4) as pool:
        for variant in variants:
            jobs.append(pool.submit(_search_duckduckgo, variant))
        jobs.append(pool.submit(_search_wikipedia, query))
        for future in as_completed(jobs):
            try:
                collected.extend(future.result())
            except requests.RequestException:
                app.logger.warning("Search source failed", exc_info=True)
            except Exception:
                app.logger.warning("Unexpected search failure", exc_info=True)
    return _dedupe_rank(query, collected)


def _extract_page(item):
    url = item.get("url", "")
    try:
        response = _SESSION.get(url, timeout=PAGE_TIMEOUT_SECONDS, allow_redirects=True)
        response.raise_for_status()
        content_type = response.headers.get("content-type", "").lower()
        if "text/html" not in content_type:
            return item
        soup = BeautifulSoup(response.text[:1_500_000], "html.parser")
        for tag in soup(["script", "style", "nav", "footer", "header", "aside", "form", "noscript"]):
            tag.decompose()
        chunks = []
        for node in soup.select("article p, main p, p"):
            text = _clean_text(node.get_text(" ", strip=True))
            if len(text) >= 60:
                chunks.append(text)
            if sum(len(x) for x in chunks) >= 7000:
                break
        excerpt = _clean_text(" ".join(chunks))[:7000]
        if excerpt:
            enriched = dict(item)
            enriched["excerpt"] = excerpt
            return enriched
    except Exception:
        pass
    return item


def _enrich_results(results):
    if not results:
        return results
    top = results[:PAGE_FETCH_LIMIT]
    enriched_by_url = {}
    with ThreadPoolExecutor(max_workers=PAGE_FETCH_LIMIT) as pool:
        futures = {pool.submit(_extract_page, item): item.get("url", "") for item in top}
        for future in as_completed(futures):
            try:
                enriched = future.result()
                enriched_by_url[enriched.get("url", "")] = enriched
            except Exception:
                pass
    return [enriched_by_url.get(item.get("url", ""), item) for item in results]


def _build_reply(query, results):
    if not results:
        return "No encontré resultados confiables para esa búsqueda ahorita."
    snippets = [_clean_text(i.get("excerpt") or i.get("snippet") or "") for i in results[:4]]
    snippets = [s for s in snippets if s]
    if not snippets:
        return f"Encontré fuentes sobre {query}, pero no pude extraer suficiente contenido para responder con seguridad."
    reply = f"Sobre {query}: {snippets[0][:650]}"
    if len(snippets) > 1:
        reply += " También encontré evidencia adicional que coincide en los puntos principales."
    return reply[:1800]


def _advanced_research(query, memory_context=""):
    ranked = _search_web(query)
    if not ranked:
        return [], None
    enriched = _enrich_results(ranked)
    answer = None
    if ai_configured():
        try:
            answer = answer_from_web(query, enriched, memory_context)
        except Exception:
            app.logger.exception("Grounded web synthesis failed")
    return enriched, answer


def _normalize_math_expression(message):
    text = _ascii_text(message).replace("×", "*").replace("÷", "/").replace(",", ".")
    text = re.sub(r"\bdividido entre\b|\bdividido por\b", " / ", text)
    text = re.sub(r"\bmultiplicado por\b", " * ", text)
    text = re.sub(r"\bmas\b", " + ", text)
    text = re.sub(r"\bmenos\b", " - ", text)
    text = re.sub(r"\bentre\b", " / ", text)
    text = re.sub(r"\bpor\b", " * ", text)
    text = re.sub(r"\bal cuadrado\b", " **2 ", text)
    text = re.sub(r"\bal cubo\b", " **3 ", text)
    text = text.replace("^", "**")
    text = re.sub(r"^(?:eddy\s*[,.:;-]?\s*)?(?:cuanto\s+es|cuanto\s+da|calcula(?:me)?|resuelve|resultado\s+de)\s+", "", text).strip(" ¿?!.:;")
    return re.sub(r"\s+", "", text) if re.search(r"\d", text) and re.search(r"(?:\*\*|[+\-*/%])", text) and not re.search(r"[^0-9.+\-*/%()\s]", text) else None


def _eval_math_node(node):
    if isinstance(node, ast.Expression):
        return _eval_math_node(node.body)
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return float(node.value)
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
        value = _eval_math_node(node.operand)
        return value if isinstance(node.op, ast.UAdd) else -value
    if isinstance(node, ast.BinOp):
        a, b = _eval_math_node(node.left), _eval_math_node(node.right)
        if isinstance(node.op, ast.Add): value = a + b
        elif isinstance(node.op, ast.Sub): value = a - b
        elif isinstance(node.op, ast.Mult): value = a * b
        elif isinstance(node.op, ast.Div): value = a / b
        elif isinstance(node.op, ast.Mod): value = a % b
        elif isinstance(node.op, ast.Pow): value = a ** b
        else: raise ValueError()
        if not math.isfinite(value) or abs(value) > 1e15:
            raise ValueError()
        return value
    raise ValueError()


def _try_calculate(message):
    plain = _ascii_text(message)
    sqrt_match = re.search(r"raiz(?: cuadrada)? de\s+(-?\d+(?:[.,]\d+)?)", plain)
    if sqrt_match:
        value = float(sqrt_match.group(1).replace(",", "."))
        if value >= 0:
            result = math.sqrt(value)
            return str(int(result)) if result.is_integer() else f"{result:.10f}".rstrip("0").rstrip(".")
    percent_match = re.search(r"(\d+(?:[.,]\d+)?)\s*%\s*(?:de|del)\s*(\d+(?:[.,]\d+)?)", plain)
    if percent_match:
        a = float(percent_match.group(1).replace(",", "."))
        b = float(percent_match.group(2).replace(",", "."))
        value = a * b / 100.0
        return str(int(value)) if value.is_integer() else f"{value:.10f}".rstrip("0").rstrip(".")
    expression = _normalize_math_expression(message)
    if not expression:
        return None
    try:
        value = _eval_math_node(ast.parse(expression, mode="eval"))
        return str(int(round(value))) if abs(value - round(value)) < 1e-10 else f"{value:.10f}".rstrip("0").rstrip(".")
    except Exception:
        return None


def _looks_like_research(message):
    text = _ascii_text(message).strip(" ¿?!.:;")
    return "?" in str(message) or text.startswith((
        "quien ", "que ", "cual ", "cuando ", "donde ", "por que ", "como ", "cuanto ",
        "explicame ", "hablame ", "noticias ", "precio ", "clima ", "busca ", "investiga ", "averigua ", "compara ",
    ))


def _status_payload():
    return {
        "status": "ok",
        "service": "EDDY Backend",
        "engine": "eddy-hybrid",
        "provider": "configurable-api+advanced-web",
        "mode": "planner+conversation+grounded-research+calculator",
        "remote_model": ai_configured(),
        "advanced_web": True,
    }


@app.get("/")
def index():
    return jsonify(_status_payload())


@app.get("/health")
def health():
    return jsonify(_status_payload())


@app.post("/plan")
def plan_route():
    payload = request.get_json(silent=True) or {}
    message = str(payload.get("message", "")).strip()
    memory = str(payload.get("memory_context", "")).strip()
    if not message:
        return jsonify(error="message is required"), 400
    if not ai_configured():
        return jsonify(error="ai_not_configured"), 503
    try:
        result = ai_plan(message, memory)
        return jsonify(result) if result else (jsonify(error="empty_ai_result"), 502)
    except Exception:
        app.logger.exception("AI planner failed")
        return jsonify(error="ai_provider_failed"), 502


def _query_response(default_force_web):
    payload = request.get_json(silent=True) or {}
    message = str(payload.get("message", payload.get("query", ""))).strip()
    force = bool(payload.get("force_web", default_force_web))
    memory = str(payload.get("memory_context", "")).strip()
    if not message:
        return jsonify(error="message is required"), 400

    calculation = _try_calculate(message)
    if calculation is not None:
        return jsonify(reply=f"El resultado es {calculation}.", web_used=False, sources=[], evidence=[], kind="calculation")

    if not force and ai_configured():
        try:
            result = ai_plan(message, memory)
            if result and not result.get("actions"):
                return jsonify(reply=result.get("reply") or "Aquí estoy.", web_used=False, sources=[], evidence=[], kind="conversation")
        except Exception:
            app.logger.exception("AI conversation failed")

    if not force and not _looks_like_research(message):
        return jsonify(error="local_command_unknown"), 422

    results, synthesized = _advanced_research(message, memory)
    if not results:
        return jsonify(reply=_build_reply(message, []), web_used=False, sources=[], evidence=[], kind="research"), 502

    sources = [
        {"title": item["title"][:180] or _source_domain(item["url"]) or "Fuente web", "url": item["url"][:2000]}
        for item in results
    ]
    evidence = [
        {
            "title": item.get("title", "")[:180],
            "url": item.get("url", "")[:2000],
            "snippet": _clean_text(item.get("excerpt") or item.get("snippet") or "")[:2500],
        }
        for item in results[:8]
    ]
    reply = synthesized or _build_reply(message, results)
    return jsonify(reply=reply, web_used=True, sources=sources, evidence=evidence, kind="research")


@app.post("/search")
def search():
    return _query_response(True)


@app.post("/chat")
def chat():
    return _query_response(False)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "10000")))
