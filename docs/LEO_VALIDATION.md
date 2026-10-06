# LEO: validación reproducible de la iteración 2026-10-06

Base: e59c274. Los resultados históricos de otras fechas no se reutilizan como evidencia de este commit.

## Ejecución local

- `bash scripts/test_agent.sh`: control de turnos, continuidad, confirmación, planificación acotada, proactividad, revisiones de memoria, routing, procedencia de citas y embeddings.
- `bash scripts/test_ai_core.sh`: regresión existente del núcleo.
- `LEO_JSON_JAR=/ruta/json-20250517.jar bash scripts/test_streaming.sh`: regresión SSE y Groq.
- `bash scripts/test_voice_fidelity.sh`: políticas de voz/JVM; no micrófono.
- `python3 -m unittest discover -s tests -p 'test_*.py' -v`: parsers y recolectores con fixtures.
- `npm test`, `npm run lint`, `npm run build`, `npm run measure`: companion web.
- `python3 scripts/validate_leo_microgpt_assets.py`: integridad del checkpoint.

Gradle local `:app:testDebugUnitTest :app:lintDebug` quedó bloqueado al resolver com.android.application:8.13.2. No se rebaja la dependencia ni se declara aprobado. Los workflows Android/web de GitHub deben verificarse sobre el SHA entregado; resultado final y manifiesto en `evidencias/leo-agent-2026-10-06/`.

## Teléfono

Todo este bloque: **PENDIENTE DE VALIDACIÓN FÍSICA**.

```powershell
adb devices
python scripts/collect_leo_device_evidence.py --serial NUMERO_ADB --scenario barge-in --duration 120 --output evidencias/dispositivo/interrupcion-001
python scripts/collect_leo_device_evidence.py --serial NUMERO_ADB --scenario screen-off --duration 600 --output evidencias/dispositivo/pantalla-001
```

Escenarios admitidos: idle, conversation, wake, false-positive, asr, tts, barge-in, screen-off, bluetooth, noise, distance, call, music. Se capturan dumpsys de CPU, RAM, batería, térmica, audio, energía, información de paquete y preferencias de diagnóstico si run-as está permitido. No resetea estadísticas, instala apps, cambia ajustes ni envía mensajes. Directorios existentes se rechazan para conservar evidencia. Cada archivo tiene SHA-256, comando y código de salida. Sin permisos/ADB, el error se conserva.

El recolector NO aprueba pruebas por encontrar el dispositivo. Cargar batería invalida una comparación de descarga; temperatura de batería no es temperatura de CPU. Para consumo, usar también `medir_bateria.py` con protocolo sin cargador. Para wake/falsos positivos, usar el módulo Diagnóstico de LEO y registrar intentos, aciertos, fallos, duración observada y escenarios. Para interrupción y primer audio audible, adjuntar video/audio con tiempos; los callbacks TTS no bastan. FPS necesita un protocolo separado de frames y no se inventa a partir de CPU.

No hay teléfono conectado ni se ejecutaron estos escenarios aquí. No hay comparación experimental con Siri/Alexa. Ver [LEO_EVALUATION.md](LEO_EVALUATION.md).


## Evidencia y límites

Estado de esta iteración: IMPLEMENTADO donde se identifica una llamada real; AUTOMATIZADAMENTE PROBADO solo para los casos ejecutados en [LEO_VALIDATION.md](LEO_VALIDATION.md). Toda propiedad del teléfono permanece **PENDIENTE DE VALIDACIÓN FÍSICA**. Los contratos o tipos sin ruta productiva se indican como PLANIFICADO.
