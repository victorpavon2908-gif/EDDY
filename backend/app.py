import os
import re
from html import unescape
from urllib.parse import parse_qs, unquote, urlparse

import requests
from bs4 import BeautifulSoup
from flask import Flask, jsonify, request

app = Flask(__name__)
SEARCH_TIMEOUT_SECONDS = float(os.getenv("EDDY_SEARCH_TIMEOUT", "12"))
SEARCH_LIMIT = max(3, min(int(os.getenv("EDDY_SEARCH_LIMIT", "8")), 12))
DDG_HTML_URL = "https://html.duckduckgo.com/html/"
WIKIPEDIA_API_URL = "https://es.wikipedia.org/w/api.php"

_SESSION = requests.Session()
_SESSION.headers.update(
    {
        "User-Agent": (
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 EDDY/0.4"
        ),
        "Accept-Language": "es-NI,es;q=0.9,en;q=0.7",
    }
)


def _clean_text(value: str) -> str:
    text = unescape(str(value or ""))
    return re.sub(r"\s+", " ", text).strip()


def _unwrap_ddg_url(href: str) -> str:
    value = str(href or "").strip()
    if value.startswith("//"):
        value = "https:" + value
    parsed = urlparse(value)
    if "duckduckgo.com" in parsed.netloc.lower() and parsed.path.startswith("/l/"):
        target = parse_qs(parsed.query).get("uddg", [""])[0]
        return unquote(target).strip()
    return value


def _source_domain(url: str) -> str:
    try:
        return urlparse(url).netloc.lower().removeprefix("www.")
    except Exception:
        return ""


def _query_tokens(query: str) -> set[str]:
    return {
        token
        for token in re.findall(r"[a-záéíóúñü0-9]{3,}", query.lower())
        if token not in {"para", "como", "esta", "este", "esto", "sobre", "desde", "hasta"}
    }


def _score_result(query: str, item: dict) -> int:
    tokens = _query_tokens(query)
    haystack = f"{item.get('title', '')} {item.get('snippet', '')}".lower()
    score = sum(8 for token in tokens if token in haystack)
    domain = _source_domain(item.get("url", ""))
    if domain.endswith((".gov", ".gob.ni", ".edu", ".org")):
        score += 5
    if domain:
        score += 2
    return score


def _dedupe_rank(query: str, items: list[dict]) -> list[dict]:
    seen_urls = set()
    per_domain = {}
    ranked = []

    for item in sorted(items, key=lambda value: _score_result(query, value), reverse=True):
        url = str(item.get("url", "")).strip()
        if not url.startswith(("http://", "https://")) or url in seen_urls:
            continue
        domain = _source_domain(url)
        if not domain:
            continue
        if per_domain.get(domain, 0) >= 2:
            continue
        seen_urls.add(url)
        per_domain[domain] = per_domain.get(domain, 0) + 1
        ranked.append(item)
        if len(ranked) >= SEARCH_LIMIT:
            break

    return ranked


def _search_duckduckgo(query: str) -> list[dict]:
    response = _SESSION.post(
        DDG_HTML_URL,
        data={"q": query, "kl": "us-es"},
        timeout=SEARCH_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")
    results = []

    for block in soup.select(".result"):
        anchor = block.select_one("a.result__a")
        if anchor is None:
            continue
        url = _unwrap_ddg_url(anchor.get("href", ""))
        if not url.startswith(("http://", "https://")):
            continue
        snippet_node = block.select_one(".result__snippet")
        results.append(
            {
                "title": _clean_text(anchor.get_text(" ", strip=True)) or _source_domain(url),
                "url": url,
                "snippet": _clean_text(snippet_node.get_text(" ", strip=True) if snippet_node else ""),
            }
        )

    return results


def _search_wikipedia(query: str) -> list[dict]:
    response = _SESSION.get(
        WIKIPEDIA_API_URL,
        params={
            "action": "opensearch",
            "search": query,
            "limit": min(SEARCH_LIMIT, 6),
            "namespace": 0,
            "format": "json",
        },
        timeout=SEARCH_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    data = response.json()
    if not isinstance(data, list) or len(data) < 4:
        return []

    titles = data[1] if isinstance(data[1], list) else []
    descriptions = data[2] if isinstance(data[2], list) else []
    urls = data[3] if isinstance(data[3], list) else []
    return [
        {
            "title": _clean_text(title),
            "url": str(url).strip(),
            "snippet": _clean_text(descriptions[index] if index < len(descriptions) else ""),
        }
        for index, (title, url) in enumerate(zip(titles, urls))
        if str(url).startswith(("http://", "https://"))
    ]


def _search_web(query: str) -> list[dict]:
    results = []
    try:
        results.extend(_search_duckduckgo(query))
    except requests.RequestException:
        app.logger.exception("DuckDuckGo search failed")

    if len(results) < 3:
        try:
            results.extend(_search_wikipedia(query))
        except requests.RequestException:
            app.logger.exception("Wikipedia fallback search failed")

    return _dedupe_rank(query, results)


def _build_reply(query: str, results: list[dict]) -> str:
    if not results:
        return "No encontré resultados confiables para esa búsqueda ahorita."

    snippets = []
    for item in results[:4]:
        snippet = _clean_text(item.get("snippet", ""))
        title = _clean_text(item.get("title", ""))
        if snippet:
            snippets.append(snippet.rstrip(". "))
        elif title:
            snippets.append(f"Encontré la fuente {title}")

    if not snippets:
        return f"Encontré {len(results)} fuentes sobre {query}. Revisalas en la pantalla de EDDY."

    first = snippets[0]
    extras = snippets[1:3]
    reply = f"Encontré información sobre {query}. Lo más relevante: {first}."
    if extras:
        reply += " También encontré: " + ". ".join(extras) + "."
    reply += " Te dejé las fuentes para que podás abrirlas y verificar los detalles."
    return reply[:1800]


@app.get("/health")
def health():
    return jsonify(
        status="ok",
        engine="eddy-web",
        provider="duckduckgo+wikipedia",
        mode="search-only",
        remote_model=False,
    )


def _search_response():
    payload = request.get_json(silent=True) or {}
    message = str(payload.get("message", payload.get("query", ""))).strip()
    force_web = bool(payload.get("force_web", True))

    if not message:
        return jsonify(error="message is required"), 400
    if len(message) > 2_000:
        return jsonify(error="message too long"), 413
    if not force_web:
        return jsonify(error="web_search_required"), 422

    results = _search_web(message)
    if not results:
        return jsonify(
            reply=_build_reply(message, []),
            web_used=False,
            sources=[],
        ), 502

    sources = [
        {
            "title": item["title"][:180] or _source_domain(item["url"]) or "Fuente web",
            "url": item["url"][:2_000],
        }
        for item in results
    ]
    return jsonify(
        reply=_build_reply(message, results),
        web_used=True,
        sources=sources,
    )


@app.post("/search")
def search():
    return _search_response()


@app.post("/chat")
def legacy_search_route():
    # Se conserva temporalmente para compatibilidad con APK anteriores.
    return _search_response()


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "10000")))
