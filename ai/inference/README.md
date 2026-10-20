# Servidor de inferencia Python de LEO

Estado: código integrado con el contrato Android, **no certificado con un modelo ni con
el HONOR físico**. No cambia la escucha, TTS, memoria, permisos ni el APK.

## Cómo funciona

`NikoAssistantService` ya emplea `LeoInferenceClient`, que envía peticiones
OpenAI-compatible a `POST /chat/completions` con consentimiento explícito.
`ai/inference/leo_local_server.py` sirve esa ruta usando **llama-cpp-python**,
que ejecuta un modelo GGUF de verdad en un ordenador/servidor con CPU.

Esto no significa Python dentro del APK. Para **inferencia local en el teléfono**
hay que integrar y probar el binding Android C++/JNI de llama.cpp; aún está pendiente.

## Modelo inicial propuesto

- **Qwen3-0.6B**, modelo original: https://huggingface.co/Qwen/Qwen3-0.6B
- Licencia de pesos declarada: Apache-2.0. Revisar avisos/licencia de cada
  distribución cuantizada, tokenizer y dependencias antes de comercializar.
- GGUF Q4_K_M de referencia: https://huggingface.co/tensorblock/Qwen_Qwen3-0.6B-GGUF
- Tamaño publicado por esa distribución: aproximadamente 484 MB.
- Alternativa: convertir el modelo oficial con llama.cpp, verificando
  compatibilidad de formato y licencia.
- **No se descarga automáticamente ningún modelo**, no hay SHA-256 inventado y
  el checkpoint propuesto todavía no ha pasado pruebas de calidad ni hardware.

Un modelo de ~484 MB ocupa más RAM una vez cargado; no prometer que un teléfono
con RAM limitada rinda igual que un PC. Probar primero el HONOR NIC-LX3.

## Arranque en PC/servidor

Con Python 3.11+ y compilador C++ instalado:

```bash
python -m venv .venv
source .venv/bin/activate
python -m pip install -r ai/inference/requirements.txt
export LEO_LOCAL_API_KEY="reemplazar-por-una-clave-aleatoria-larga"
python ai/inference/leo_local_server.py \
  --model-file /ruta/privada/Qwen3-0.6B-Q4_K_M.gguf \
  --model-id leo-local --host 127.0.0.1 --port 8765 \
  --context 2048 --threads 4
```

En Windows use el entorno virtual PowerShell y configure la clave por variable
de entorno, sin guardarla en GitHub. No ponga las claves ni las respuestas en logs.

Prueba de transporte local:

```bash
curl -fsS http://127.0.0.1:8765/health
curl -sS http://127.0.0.1:8765/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer reemplazar-por-una-clave-aleatoria-larga' \
  -d '{"model":"leo-local","stream":false,"messages":[{"role":"system","content":"Sos LEO. Respondé en español."},{"role":"user","content":"Explicá qué es un generador eólico."}]}'
```

`LEO_LOCAL_API_KEY` debe coincidir con la clave introducida en ajustes de
Android. **No usar HTTP público**: para conexión desde el teléfono se necesita
un dominio HTTPS válido con un proxy TLS (por ejemplo Caddy) hacia
`127.0.0.1:8765`, con acceso y credenciales restringidos.

Ejemplo de Caddyfile en un servidor con dominio propio y DNS configurado:

```caddyfile
leo.example.com {
    reverse_proxy 127.0.0.1:8765
}
```

En LEO Android, en Ajustes → «MODELO DE RAZONAMIENTO Y CÓDIGO», introducir:
- URL: `https://leo.example.com/chat/completions`
- Modelo: `leo-local`
- Clave: valor de `LEO_LOCAL_API_KEY`
- Activar voluntariamente el envío y pulsar «PROBAR CONEXIÓN».

Esta llamada **envía la pregunta al servidor**; «servidor local» significa
que el modelo corre en la máquina que lo aloja, no necesariamente en el teléfono.
No activar historial ni extractos de investigación sin consentimiento informado.
No exponer el servidor sin autenticación, HTTPS, actualizaciones y control de acceso.

## Seguridad y límites

- Solo escucha en loopback. Las credenciales se comparan en tiempo constante.
- Máximo 64 KiB por petición, 16.000 caracteres de prompt, ocho mensajes y
  4.096 tokens solicitados (la capacidad real depende del contexto del modelo).
- Rechaza streaming, campos de herramientas, roles extraños, respuestas truncadas,
  peticiones concurrentes y contenidos vacíos.
- No ejecuta código generado, no lee archivos arbitrarios ni obtiene páginas web.
- El modelo puede responder mal; investigación con citas sigue siendo validada por
  `LeoResearchSynthesis` del Android y debe usar su fallback si falla la salida.
- Módulos de emoción, memoria, aprendizaje y acciones siguen ejecutándose
  principalmente en Android, no en este servicio.
- El servidor no implementa cuentas multiusuario ni rate limiting persistente:
  no publicarlo como servicio comercial multiinquilino tal como está.

## Pruebas reproducibles

```bash
python -m unittest discover -s ai/inference -p 'test_*.py' -v
python -m unittest discover -s training -p 'test_*.py' -v
python -m unittest discover -s model-pipeline -p 'test_*.py' -v
```

Los tests HTTP usan un backend falso para comprobar **el transporte**, no
la calidad de un modelo. Exigir además evaluación con modelo real, benchmarks
en español, latencia/pico de RAM y pruebas físicas con el dispositivo.

## Criterios de entrega comercial pendientes

1. Integra una implementación nativa de GGUF para Android 12+ si se necesita
   inferencia sin conexión en el propio teléfono; verificar ABI, STL y JNI.
2. Publica un manifiesto autenticado/firma para actualizaciones de modelos,
   conservando hash, versión anterior y rollback.
3. Prueba como mínimo 100 activaciones de voz y falsos positivos en condiciones reales.
4. Evalúa emociones, memoria, razonamiento, programación e investigación con
   conjuntos externos independientes, no solo ejemplos programados.
5. Compila, firma y ensaya la app en dispositivo real, revisa privacidad y licencias.
6. No declarar `100/100` ni `comercializable` antes de esas verificaciones.
