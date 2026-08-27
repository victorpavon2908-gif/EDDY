import os
from urllib.parse import urlparse

from flask import Flask, jsonify, request
from openai import OpenAI

app = Flask(__name__)
MODEL = os.getenv("OPENAI_MODEL", "gpt-5-mini")
WEB_SEARCH_ENABLED = os.getenv("OPENAI_WEB_SEARCH", "true").lower() not in {"0", "false", "no"}
_client = None

SYSTEM_INSTRUCTIONS = """
Eres EDDY, un asistente personal conversacional para Android.
Habla en español nicaragüense natural, claro y útil. Usa voseo de forma natural
("vos", "decime", "querés", "ocupás", "podés") y expresiones nicas ligeras como
"de una", "tuani" o "ahorita" cuando encajen. No caricaturices el acento ni llenes
cada frase de modismos. Tu personalidad es inteligente, rápida, amable y segura.

El teléfono ejecuta por separado acciones locales como abrir apps, llamadas,
WhatsApp, Spotify, linterna, volumen, brillo, batería, alarmas, temporizadores,
mapas, ajustes del sistema y dispositivos de casa inteligente por Wi-Fi. Tú atiendes
conversación general, preguntas, explicaciones, investigación y continuidad contextual.

Cuando la pregunta dependa de información reciente, cambiante, local, específica o
requiera verificación, usa la búsqueda web. Investiga antes de responder, compara
fuentes cuando sea útil y prioriza fuentes oficiales, primarias y confiables. No inventes
hechos ni enlaces. Si las fuentes discrepan, dilo. Cuando uses la web, responde de forma
clara y deja que las citas de la plataforma respalden las afirmaciones verificables.

Usa la memoria proporcionada solo cuando sea relevante. No inventes recuerdos.
Si la memoria no contiene un dato, dilo con naturalidad. Evita respuestas innecesariamente
largas salvo que el usuario pida detalle. Cuando una acción local ya fue ejecutada, no la
simules ni afirmes haber hecho algo que Android no confirmó.
""".strip()


def get_client():
    global _client
    if _client is None:
        _client = OpenAI()
    return _client


def _response_dict(response):
    if isinstance(response, dict):
        return response
    model_dump = getattr(response, "model_dump", None)
    if callable(model_dump):
        return model_dump()
    return {}


def _source_title(url: str) -> str:
    try:
        host = urlparse(url).netloc.lower().removeprefix("www.")
        return host or "Fuente web"
    except Exception:
        return "Fuente web"


def _extract_sources(response):
    data = _response_dict(response)
    sources = []
    seen = set()

    def add_source(url, title=None):
        url = str(url or "").strip()
        if not url or url in seen:
            return
        seen.add(url)
        sources.append(
            {
                "title": str(title or _source_title(url)).strip()[:180] or "Fuente web",
                "url": url[:2_000],
            }
        )

    for item in data.get("output", []) or []:
        if not isinstance(item, dict):
            continue

        if item.get("type") == "web_search_call":
            action = item.get("action") or {}
            if isinstance(action, dict):
                for source in action.get("sources", []) or []:
                    if isinstance(source, dict):
                        add_source(source.get("url"))

        if item.get("type") == "message":
            for content in item.get("content", []) or []:
                if not isinstance(content, dict):
                    continue
                for annotation in content.get("annotations", []) or []:
                    if not isinstance(annotation, dict):
                        continue
                    if annotation.get("type") == "url_citation":
                        add_source(annotation.get("url"), annotation.get("title"))

    return sources[:8]


@app.get("/health")
def health():
    return jsonify(status="ok", model=MODEL, web_search=WEB_SEARCH_ENABLED)


@app.post("/chat")
def chat():
    payload = request.get_json(silent=True) or {}
    message = str(payload.get("message", "")).strip()
    context = str(payload.get("context", "")).strip()
    force_web = bool(payload.get("force_web", False))

    if not message:
        return jsonify(error="message is required"), 400

    if len(message) > 8_000:
        return jsonify(error="message too long"), 413

    context = context[:12_000]

    try:
        request_args = {
            "model": MODEL,
            "instructions": SYSTEM_INSTRUCTIONS,
            "input": (
                f"Contexto local de EDDY:\n{context or 'Sin memoria disponible.'}\n\n"
                f"Usuario: {message}"
            ),
            "max_output_tokens": 900,
        }

        if WEB_SEARCH_ENABLED:
            request_args["tools"] = [
                {
                    "type": "web_search",
                    "search_context_size": "high",
                    "user_location": {
                        "type": "approximate",
                        "country": "NI",
                        "timezone": "America/Managua",
                    },
                }
            ]
            request_args["tool_choice"] = "required" if force_web else "auto"

        response = get_client().responses.create(**request_args)
        reply = (response.output_text or "").strip()
        if not reply:
            return jsonify(error="empty model response"), 502

        sources = _extract_sources(response)
        return jsonify(
            reply=reply,
            web_used=bool(sources),
            sources=sources,
        )
    except Exception:
        app.logger.exception("AI request failed")
        return jsonify(error="ai_request_failed"), 502


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "10000")))
