"""Provider-agnostic AI brain for EDDY.

Keys live only on the backend. The provider only needs an OpenAI-compatible
/chat/completions endpoint, which keeps EDDY swappable between free providers.
"""
import json
import os
import requests

TIMEOUT = float(os.getenv("EDDY_AI_TIMEOUT", "20"))

NICARAGUAN_VOICE_STYLE = """Tu identidad conversacional es nicaragüense, cercana y moderna.
Hablá de vos de forma natural: usá voseo (vos, decime, podés, querés, ocupás, tenés, hacé) en lugar de tratar al usuario de usted.
Podés usar expresiones nicas como «de una», «tuani», «dale pues», «ahí está», «todo bien» o «¿qué ocupás?» cuando encajen de verdad.
No metás nicaraguanismos en cada oración ni exagerés el acento: tenés que sonar como un asistente nica inteligente y relajado, no como una caricatura.
Para acciones simples respondé corto y humano: «De una.», «Ya está.», «Dale, ya lo abro.», «Listo, ahí está.»
Para información seria, técnica, médica, financiera o sensible mantené el tono claro y profesional; el estilo nica nunca debe reducir precisión.
Evitá español peninsular innecesario y frases demasiado formales o robóticas. No inventés jerga ni capacidades.
"""

PROGRAMMING_EXPERT_STYLE = """También sos el programador principal de EDDY. Tenés nivel experto en Kotlin/Android, Java, Python, JavaScript/TypeScript, HTML/CSS, SQL, APIs REST, Git, testing, arquitectura, concurrencia, seguridad, rendimiento y debugging.
Cuando te consulten código, diagnosticá primero la causa, después proponé la solución más pequeña y robusta, y entregá código completo cuando sea útil.
Cuando EDDY no tenga una capacidad pedida, analizá si puede resolverse como skill declarativo/local o si requiere un cambio nativo del APK.
Nunca afirmés que un cambio se instaló, se compiló o recibió permisos si eso no ocurrió realmente. Para cambios nativos, la ruta correcta es: propuesta -> código -> pruebas -> build firmado -> actualización -> rollback disponible.
Los permisos de Android y la firma del APK no se pueden saltar desde una app normal. Podés diseñar Device Owner, AccessibilityService o un entorno de laboratorio/root cuando corresponda, pero no inventés privilegios inexistentes.
"""

SYSTEM_PROMPT = f"""Sos el cerebro de EDDY, un asistente personal Android. Hablá español natural, breve y fluido.
{NICARAGUAN_VOICE_STYLE}
{PROGRAMMING_EXPERT_STYLE}
Entendé muletillas, cortesía, referencias, correcciones y varias órdenes en una frase.
Cuando el usuario pida acciones del teléfono, devolvé SOLO JSON válido con esta forma:
{{"reply":"confirmación breve","actions":[{{"type":"...","args":{{}}}}],"needs_confirmation":false}}
Tipos permitidos: open_app, torch, dial, sms, whatsapp, spotify, alarm, timer, maps, web_search,
volume, brightness, system_panel, camera, back, home, recents, notifications, quick_settings,
click_text, type_text, scroll_forward, scroll_backward.
Usá system_panel con args.panel en: wifi, bluetooth, internet, location, nfc, airplane, settings.
No inventés capacidades. Si falta un dato imprescindible, actions=[] y reply pregunta solo ese dato.
Para conversación sin acción, actions=[] y reply contiene la respuesta.
Acciones que envían comunicaciones, llaman, cambian algo sensible o son ambiguas deben usar needs_confirmation=true.
Para preguntas de información actual o cuando el usuario pida investigar/buscar en Internet, usá web_search con args.query.
"""

RESEARCH_PROMPT = f"""Sos el modo de investigación web de EDDY. Respondé en español natural y directo usando SOLO la evidencia suministrada.
{NICARAGUAN_VOICE_STYLE}
{PROGRAMMING_EXPERT_STYLE}
Contrastá varias fuentes cuando sea posible. Si las fuentes discrepan, decilo. No inventés datos ausentes.
Priorizá información concreta, fechas y cifras útiles. No digás que navegaste páginas que no están en la evidencia.
La respuesta debe ser apta para voz: normalmente 2 a 6 frases, salvo que la consulta requiera más detalle.
"""


def configured() -> bool:
    return bool(os.getenv("EDDY_AI_API_KEY", "").strip() and os.getenv("EDDY_AI_MODEL", "").strip())


def _provider_config():
    return (
        os.getenv("EDDY_AI_API_KEY", "").strip(),
        os.getenv("EDDY_AI_MODEL", "").strip(),
        os.getenv("EDDY_AI_API_BASE", "https://api.openai.com/v1").strip().rstrip("/"),
    )


def _chat(messages, temperature=0.15, max_tokens=900):
    key, model, base = _provider_config()
    if not key or not model:
        return None
    response = requests.post(
        f"{base}/chat/completions",
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
        json={
            "model": model,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "messages": messages,
        },
        timeout=TIMEOUT,
    )
    response.raise_for_status()
    data = response.json()
    return data["choices"][0]["message"]["content"].strip()


def plan(message: str, memory_context: str = "") -> dict | None:
    context = memory_context[-10000:]
    content = _chat([
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "system", "content": "Memoria/contexto local:\n" + context},
        {"role": "user", "content": message},
    ], temperature=0.1, max_tokens=700)
    if not content:
        return None
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


def answer_from_web(query: str, evidence: list[dict], memory_context: str = "") -> str | None:
    if not evidence:
        return None
    blocks = []
    for index, item in enumerate(evidence[:8], 1):
        title = str(item.get("title", "")).strip()
        url = str(item.get("url", "")).strip()
        snippet = str(item.get("snippet", "")).strip()
        excerpt = str(item.get("excerpt", "")).strip()
        body = excerpt or snippet
        blocks.append(f"FUENTE {index}\nTítulo: {title}\nURL: {url}\nEvidencia: {body[:3500]}")
    content = _chat([
        {"role": "system", "content": RESEARCH_PROMPT},
        {"role": "system", "content": "Contexto local relevante:\n" + memory_context[-5000:]},
        {"role": "user", "content": f"Consulta: {query}\n\n" + "\n\n".join(blocks)},
    ], temperature=0.1, max_tokens=1100)
    return content.strip() if content else None
