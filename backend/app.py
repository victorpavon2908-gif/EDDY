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

try:
    from .ai_provider import configured as ai_configured, plan as ai_plan, answer_from_web
except ImportError:
    from ai_provider import configured as ai_configured, plan as ai_plan, answer_from_web

app = Flask(__name__)
SEARCH_TIMEOUT_SECONDS = float(os.getenv("EDDY_SEARCH_TIMEOUT", "4.5"))
SEARCH_LIMIT = max(4, min(int(os.getenv("EDDY_SEARCH_LIMIT", "7")), 10))
DDG_HTML_URL = "https://html.duckduckgo.com/html/"
WIKIPEDIA_API_URL = "https://es.wikipedia.org/w/api.php"
_SESSION = requests.Session()
_SESSION.headers.update({
    "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 EDDY/0.5.1",
    "Accept-Language": "es-NI,es;q=0.9,en;q=0.7",
})


def _clean_text(value):
    return re.sub(r"\s+", " ", unescape(str(value or ""))).strip()


def _ascii_text(value):
    normalized = unicodedata.normalize("NFD", str(value or "").lower())
    return "".join(c for c in normalized if unicodedata.category(c) != "Mn")


def _source_domain(url):
    try:
        return urlparse(url).netloc.lower().removeprefix("www.")
    except Exception:
        return ""


def _unwrap_ddg_url(href):
    value = str(href or "").strip()
    if value.startswith("//"):
        value = "https:" + value
    parsed = urlparse(value)
    if "duckduckgo.com" in parsed.netloc.lower() and parsed.path.startswith("/l/"):
        return unquote(parse_qs(parsed.query).get("uddg", [""])[0]).strip()
    return value


def _query_tokens(query):
    stop = {"para", "como", "esta", "este", "esto", "sobre", "desde", "hasta", "dime", "decime", "busca", "investiga"}
    return {t for t in re.findall(r"[a-záéíóúñü0-9]{3,}", query.lower()) if t not in stop}


def _score_result(query, item):
    tokens = _query_tokens(query)
    hay = f"{item.get('title', '')} {item.get('snippet', '')}".lower()
    score = sum(8 for token in tokens if token in hay)
    domain = _source_domain(item.get("url", ""))
    if domain.endswith((".gov", ".gob.ni", ".edu", ".org")):
        score += 5
    if domain:
        score += 2
    return score


def _dedupe_rank(query, items):
    seen = set()
    per_domain = {}
    ranked = []
    for item in sorted(items, key=lambda value: _score_result(query, value), reverse=True):
        url = str(item.get("url", "")).strip()
        domain = _source_domain(url)
        if not url.startswith(("http://", "https://")) or not domain or url in seen:
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
    response = _SESSION.post(
        DDG_HTML_URL,
        data={"q": query, "kl": "us-es"},
        timeout=SEARCH_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")
    output = []
    for block in soup.select(".result"):
        anchor = block.select_one("a.result__a")
        if anchor is None:
            continue
        url = _unwrap_ddg_url(anchor.get("href", ""))
        if not url.startswith(("http://", "https://")):
            continue
        snippet = block.select_one(".result__snippet")
        output.append({
            "title": _clean_text(anchor.get_text(" ", strip=True)) or _source_domain(url),
            "url": url,
            "snippet": _clean_text(snippet.get_text(" ", strip=True) if snippet else ""),
        })
    return output


def _search_wikipedia(query):
    response = _SESSION.get(
        WIKIPEDIA_API_URL,
        params={
            "action": "opensearch",
            "search": query,
            "limit": min(SEARCH_LIMIT, 5),
            "namespace": 0,
            "format": "json",
        },
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
            "snippet": _clean_text(data[2][index] if isinstance(data[2], list) and index < len(data[2]) else ""),
        }
        for index, (title, url) in enumerate(zip(data[1], data[3]))
        if str(url).startswith(("http://", "https://"))
    ]


def _search_variants(query):
    base = _clean_text(query)
    stripped = re.sub(
        r"\b(?:eddy|por favor|porfa|busca|buscame|investiga|averigua|dime|decime|quiero saber|en internet|en la web)\b",
        " ",
        _ascii_text(base),
    )
    stripped = re.sub(r"\s+", " ", stripped).strip()
    return [base] if not stripped or stripped == base.lower() else [base, stripped]


def _search_web(query):
    collected = []
    jobs = []
    with ThreadPoolExecutor(max_workers=3) as pool:
        for variant in _search_variants(query)[:2]:
            jobs.append(pool.submit(_search_duckduckgo, variant))
        jobs.append(pool.submit(_search_wikipedia, query))
        for future in as_completed(jobs):
            try:
                collected.extend(future.result())
            except Exception:
                app.logger.warning("Search source failed", exc_info=True)
    return _dedupe_rank(query, collected)


def _build_reply(query, results):
    if not results:
        return "No encontré resultados confiables para esa búsqueda ahorita."
    snippets = [_clean_text(item.get("snippet", "")) for item in results[:4]]
    snippets = [text for text in snippets if text]
    if not snippets:
        return f"Encontré fuentes sobre {query}, pero no pude extraer suficiente contenido para responder con seguridad."
    answer = f"Sobre {query}: {snippets[0][:700]}"
    if len(snippets) > 1:
        answer += " También encontré otras fuentes relevantes para contrastar el dato."
    return answer[:1800]


def _try_ai_web_answer(query, results, memory_context):
    if not ai_configured() or not results:
        return None
    try:
        return answer_from_web(query, results[:6], memory_context)
    except Exception:
        app.logger.exception("Web synthesis failed")
        return None


def _normalize_math_expression(message):
    text = _ascii_text(message).replace("×", "*").replace("÷", "/").replace(",", ".")
    text = re.sub(r"\bdividido (?:entre|por)\b", " / ", text)
    text = re.sub(r"\bmultiplicado por\b", " * ", text)
    text = re.sub(r"\bmas\b", " + ", text)
    text = re.sub(r"\bmenos\b", " - ", text)
    text = re.sub(r"\bentre\b", " / ", text)
    text = re.sub(r"\bpor\b", " * ", text)
    text = re.sub(r"\bal cuadrado\b", " **2 ", text)
    text = re.sub(r"\bal cubo\b", " **3 ", text)
    text = text.replace("^", "**")
    text = re.sub(
        r"^(?:eddy\s*[,.:;-]?\s*)?(?:cuanto\s+es|cuanto\s+da|calcula(?:me)?|resuelve|resultado\s+de)\s+",
        "",
        text,
    ).strip(" ¿?!.:;")
    if not re.search(r"\d", text) or not re.search(r"(?:\*\*|[+\-*/%])", text):
        return None
    if re.search(r"[^0-9.+\-*/%()\s]", text):
        return None
    return re.sub(r"\s+", "", text)


def _eval_math_node(node):
    if isinstance(node, ast.Expression):
        return _eval_math_node(node.body)
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return float(node.value)
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
        value = _eval_math_node(node.operand)
        return value if isinstance(node.op, ast.UAdd) else -value
    if isinstance(node, ast.BinOp):
        a = _eval_math_node(node.left)
        b = _eval_math_node(node.right)
        if isinstance(node.op, ast.Add): value = a + b
        elif isinstance(node.op, ast.Sub): value = a - b
        elif isinstance(node.op, ast.Mult): value = a * b
        elif isinstance(node.op, ast.Div): value = a / b
        elif isinstance(node.op, ast.Mod): value = a % b
        elif isinstance(node.op, ast.Pow): value = a ** b
        else: raise ValueError("operator")
        if not math.isfinite(value) or abs(value) > 1e15:
            raise ValueError("range")
        return value
    raise ValueError("expression")


def _format_number(value):
    return str(int(round(value))) if abs(value - round(value)) < 1e-10 else f"{value:.10f}".rstrip("0").rstrip(".")


def _try_calculate(message):
    plain = _ascii_text(message)

    percent_words = re.search(
        r"(-?\d+(?:[.,]\d+)?)\s*(?:%|por\s+ciento)\s*(?:de|del)\s*(-?\d+(?:[.,]\d+)?)",
        plain,
    )
    if percent_words:
        percent = float(percent_words.group(1).replace(",", "."))
        base = float(percent_words.group(2).replace(",", "."))
        return _format_number(percent * base / 100.0)

    sqrt_match = re.search(r"raiz(?: cuadrada)? de\s+(-?\d+(?:[.,]\d+)?)", plain)
    if sqrt_match:
        value = float(sqrt_match.group(1).replace(",", "."))
        if value >= 0:
            return _format_number(math.sqrt(value))

    expression = _normalize_math_expression(message)
    if not expression:
        return None
    try:
        return _format_number(_eval_math_node(ast.parse(expression, mode="eval")))
    except Exception:
        return None


def _looks_like_research(message):
    raw = str(message)
    text = _ascii_text(raw).strip(" ¿?!.:;")
    return "?" in raw or text.startswith((
        "quien ", "que ", "cual ", "cuando ", "donde ", "por que ", "como ", "cuanto ",
        "explicame ", "hablame ", "noticias ", "precio ", "clima ", "busca ", "buscame ",
        "investiga ", "averigua ", "compara ",
    ))


def _status_payload():
    return {
        "status": "ok",
        "service": "EDDY Backend",
        "engine": "eddy-web+math",
        "provider": "configurable-api+fast-web",
        "mode": "automatic-research+calculator",
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
    force_web = bool(payload.get("force_web", default_force_web))
    memory = str(payload.get("memory_context", "")).strip()

    if not message:
        return jsonify(error="message is required"), 400

    calculation = _try_calculate(message)
    if calculation is not None:
        return jsonify(
            reply=f"El resultado es {calculation}.",
            web_used=False,
            sources=[],
            evidence=[],
            kind="calculation",
        )

    if not force_web and ai_configured():
        try:
            result = ai_plan(message, memory)
            if result and not result.get("actions") and not _looks_like_research(message):
                return jsonify(
                    reply=result.get("reply") or "Aquí estoy.",
                    web_used=False,
                    sources=[],
                    evidence=[],
                    kind="conversation",
                )
        except Exception:
            app.logger.exception("AI conversation failed")

    if not force_web and not _looks_like_research(message):
        return jsonify(error="local_command_unknown"), 422

    results = _search_web(message)
    if not results:
        return jsonify(
            reply=_build_reply(message, []),
            web_used=False,
            sources=[],
            evidence=[],
            kind="research",
        ), 502

    sources = [
        {
            "title": str(item.get("title", ""))[:180] or _source_domain(item.get("url", "")) or "Fuente web",
            "url": str(item.get("url", ""))[:2000],
        }
        for item in results
    ]
    evidence = [
        {
            "title": str(item.get("title", ""))[:180],
            "url": str(item.get("url", ""))[:2000],
            "snippet": _clean_text(item.get("snippet", ""))[:1800],
        }
        for item in results[:7]
    ]
    reply = _try_ai_web_answer(message, results, memory) or _build_reply(message, results)
    return jsonify(reply=reply, web_used=True, sources=sources, evidence=evidence, kind="research")


@app.post("/search")
def search():
    return _query_response(True)


@app.post("/chat")
def chat():
    return _query_response(False)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "10000")))
