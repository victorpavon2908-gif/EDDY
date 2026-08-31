"""Provider-agnostic AI brain for EDDY.

Configure with environment variables only; never ship provider keys in the APK.
Supports OpenAI-compatible chat endpoints so free/self-hosted providers can be swapped
without rebuilding Android.
"""
import json
import os
import requests

TIMEOUT = float(os.getenv("EDDY_AI_TIMEOUT", "45"))

SYSTEM_PROMPT = """Sos el cerebro de EDDY, un asistente personal Android. Hablá español natural y breve.
Entendé muletillas, cortesía, referencias y varias órdenes en una frase. Cuando el usuario pida
acciones del teléfono, devolvé SOLO JSON válido con esta forma:
{"reply":"confirmación breve","actions":[{"type":"...","args":{}}],"needs_confirmation":false}
Tipos permitidos: open_app, torch, dial, sms, whatsapp, spotify, alarm, timer, maps, web_search,
volume, brightness, system_panel, camera, back, home, recents, notifications, quick_settings,
click_text, type_text, scroll_forward, scroll_backward.
No inventés capacidades. Si falta un dato imprescindible, actions=[] y reply debe preguntar ese dato.
Para conversación sin acción, actions=[] y reply contiene la respuesta. Podés producir varias actions
en el orden solicitado. Acciones sensibles o ambiguas deben usar needs_confirmation=true.
"""


def configured() -> bool:
    return bool(os.getenv("EDDY_AI_API_KEY", "").strip() and os.getenv("EDDY_AI_MODEL", "").strip())


def plan(message: str, memory_context: str = "") -> dict | None:
    key = os.getenv("EDDY_AI_API_KEY", "").strip()
    model = os.getenv("EDDY_AI_MODEL", "").strip()
    base = os.getenv("EDDY_AI_API_BASE", "https://api.openai.com/v1").strip().rstrip("/")
    if not key or not model:
        return None
    context = memory_context[-12000:]
    payload = {
        "model": model,
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "system", "content": "Memoria/contexto local:\n" + context},
            {"role": "user", "content": message},
        ],
    }
    response = requests.post(
        f"{base}/chat/completions",
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
        json=payload,
        timeout=TIMEOUT,
    )
    response.raise_for_status()
    data = response.json()
    content = data["choices"][0]["message"]["content"].strip()
    if content.startswith("```"):
        content = content.strip("`").removeprefix("json").strip()
    result = json.loads(content)
    if not isinstance(result, dict):
        return None
    actions = result.get("actions", [])
    if not isinstance(actions, list):
        actions = []
    return {
        "reply": str(result.get("reply", "")).strip(),
        "actions": actions[:12],
        "needs_confirmation": bool(result.get("needs_confirmation", False)),
    }
