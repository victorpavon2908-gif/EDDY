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


## Resultados observados (actualización 2026-10-07)

| Validación local | Resultado real |
|---|---:|
| Agente, memoria, skills, citas, visión textual y embeddings | 53/53 |
| Núcleo existente | 44/44 |
| Streaming/Groq | 31/31 |
| Políticas de voz/JVM | 85/85 |
| Companion web | 36/36 |
| Recolectores Python | 9/9 |
| TypeScript, build web, métricas e integridad MicroGPT | Código 0 en cada comando |

Total local: 258 pruebas; no sumar este número a Android CI porque hay casos compartidos. Logs y hashes: [manifiesto](../evidencias/leo-agent-2026-10-06/validation.json). `source_base_commit` más los hashes identifican las fuentes realmente utilizadas, incluidos ajustes posteriores a la base. El manifiesto separa el error inicial de compilación y su corrección.

La integración Android en `40b7c00` pasó `testDebugUnitTest` y `lintDebug` en el [workflow 37633714241](https://github.com/victorpavon2908-gif/EDDY/actions/runs/37633714241); el [workflow web 37633714431](https://github.com/victorpavon2908-gif/EDDY/actions/runs/37633714431) pasó. El commit de entrega vuelve a ejecutar ambos workflows por incluir el ajuste final de cambio de tema. Consultar Actions sobre ese SHA; no convertir un run pendiente en aprobado. No se ejecutaron workflows manuales de APK o publicación del cerebro congelado.


## Iteración 2026-10-07: embeddings, reconocedor principal e interfaz

- `evidencias/leo-semantic-duplex-2026-10-07/semantic-jvm.log`: 7 tests reales, incluyendo seis contrastes de paráfrasis en español con pesos DistilUSE INT8 verificados.
- `voice-jvm.log`: 90 tests de políticas/audio. `agent-jvm.log`: 53 tests. `python.log`: 9 tests.
- Android local bloqueado al resolver el plugin AGP 8.13.2; no se registra como aprobado. El resultado integrado se obtiene del workflow de GitHub de esta revisión.
- [Rutas, reproducción y protocolo físico](LEO_SEMANTIC_DUPLEX.md). La nueva interfaz no tiene captura validada en teléfono. Dúplex acústico, RAM, batería, temperatura y Bluetooth: **PENDIENTE DE VALIDACIÓN FÍSICA**.

## Iteración 2026-10-10: sin Groq

Ver [LEO_WEB_FIRST.md](LEO_WEB_FIRST.md) y `evidencias/leo-web-first-2026-10-10/`. Los resultados integrados corresponden al workflow del SHA publicado, no a ejecuciones anteriores.
