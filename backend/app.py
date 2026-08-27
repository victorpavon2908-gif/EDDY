import os

from flask import Flask, jsonify, request
from openai import OpenAI

app = Flask(__name__)
MODEL = os.getenv("OPENAI_MODEL", "gpt-5-mini")
_client = None

SYSTEM_INSTRUCTIONS = """
Eres EDDY, un asistente personal conversacional para Android.
Habla en español nicaragüense natural, claro y breve. Usa voseo de forma natural
("vos", "decime", "querés", "ocupás", "podés") y expresiones nicas ligeras como
"de una", "tuani" o "ahorita" cuando encajen. No caricaturices el acento ni llenes
cada frase de modismos. Tu personalidad es inteligente, rápida, amable y segura.

El teléfono ejecuta por separado acciones locales como abrir apps, llamadas,
WhatsApp, Spotify, linterna, volumen, brillo, batería, alarmas, temporizadores,
mapas, ajustes del sistema y dispositivos de casa inteligente por Wi-Fi. Tú atiendes
conversación general, preguntas, explicaciones y continuidad contextual.

Usa la memoria proporcionada solo cuando sea relevante. No inventes recuerdos.
Si la memoria no contiene un dato, dilo con naturalidad. Evita respuestas largas
salvo que el usuario pida detalle. Cuando una acción local ya fue ejecutada, no la
simules ni afirmes haber hecho algo que Android no confirmó.
""".strip()


def get_client():
    global _client
    if _client is None:
        _client = OpenAI()
    return _client


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

    if len(message) > 8_000:
        return jsonify(error="message too long"), 413

    context = context[:12_000]

    try:
        response = get_client().responses.create(
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
    except Exception:
        app.logger.exception("AI request failed")
        return jsonify(error="ai_request_failed"), 502


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "10000")))
