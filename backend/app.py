import os

from flask import Flask, jsonify, request
from openai import OpenAI

app = Flask(__name__)
client = OpenAI()
MODEL = os.getenv("OPENAI_MODEL", "gpt-5-mini")

SYSTEM_INSTRUCTIONS = """
Eres EDDY, un asistente personal conversacional para Android.
Habla en español natural, claro y breve. Tu personalidad es inteligente, rápida,
amable y segura, con un toque ligero de humor cuando encaje.

El teléfono ejecuta por separado acciones locales como abrir apps, llamadas,
mensajes, alarmas y mapas. Tú atiendes conversación general, preguntas,
explicaciones y continuidad contextual.

Usa la memoria proporcionada solo cuando sea relevante. No inventes recuerdos.
Si la memoria no contiene un dato, dilo con naturalidad. Evita respuestas largas
salvo que el usuario pida detalle.
""".strip()


@app.get("/health")
def health():
    return jsonify(status="ok", model=MODEL)


@app.post("/chat")
def chat():
    payload = request.get_json(silent=True) or {}
    message = str(payload.get("message", "")).strip()
    context = str(payload.get("context", "")).strip()

    if not message:
        return jsonify(error="message is required"), 400

    try:
        response = client.responses.create(
            model=MODEL,
            instructions=SYSTEM_INSTRUCTIONS,
            input=(
                f"Contexto local de EDDY:\n{context or 'Sin memoria disponible.'}\n\n"
                f"Usuario: {message}"
            ),
            max_output_tokens=500,
        )
        reply = (response.output_text or "").strip()
        if not reply:
            return jsonify(error="empty model response"), 502
        return jsonify(reply=reply)
    except Exception as exc:
        app.logger.exception("AI request failed")
        return jsonify(error="ai_request_failed", detail=str(exc)[:200]), 502


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "10000")))
