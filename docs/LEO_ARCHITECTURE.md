# LEO: arquitectura incremental del agente

Base inspeccionada: `e59c27403fe23398c127a96380f4eaac4575b7ca`, rama main. Producto público: LEO. `com.eddy.assistant`, `com.niko.assistant`, alias y formatos existentes conservados.

## Rutas reales

1. Manifest → EddyAssistantService (compatibilidad) → NikoAssistantService.
2. ensureVoiceListening → LeoPlatformVoiceEngine como ruta primaria; NikoLocalVoiceEngine como respaldo. No se cambia la selección, ASR, KWS, VAD ni los modelos de escucha.
3. onCommand → submitCommand → LeoAgentRuntime.begin → TurnController.Token en el contexto de coroutine → handleCommand.
4. Acciones determinísticas → registro LeoSkillRegistry → adaptadores → ejecutores existentes. Preguntas → ConversationCoordinator → MicroGPT/conocimiento local o Groq. Investigación → NikoAiClient → búsqueda nativa → síntesis con fuentes.
5. Respuesta → comprobación de turno → ConversationManager → estado visible → TTS Android. Interrupción invalida generación y cancela job, cola y streaming; los motores existentes mantienen la responsabilidad de detectar la voz.

El servicio conserva preparación del micrófono, overlays, ciclo de vida Android y compatibilidad. El nuevo núcleo contiene estado de conversación, capacidades de turno y confirmación. La extracción es incremental; el servicio sigue siendo grande y no se declara refactorizado por completo.

## Hallazgos priorizados

| Hallazgo comprobado en código | Intervención / situación |
|---|---|
| Historial de acciones rápidas se escribía en job separado; podía llegar después de la respuesta o del borrado | Escritura ordenada dentro del turno |
| runCatching consumía CancellationException en dos planificadores | Cancelación propagada; comprobación antes de pasos UI |
| Reconocedor Android se pausa al responder | Conservado por estabilidad; full-duplex principal pendiente |
| MicroGPT recibía prefijo arbitrario y reclasificaba la frase | Entrada cruda para familias soportadas; referencias pasan a contexto/cloud |
| Preferencias contrarias seguían en memoria semántica/episodios/notas | Retiro de hechos superados, con esquema SQLite conservado |
| Proactividad conversacional rotaba tres frases | Candidatos de hábitos contados, motivo, límites y supresión persistente |
| clickText elegía el primer resultado de varias coincidencias | Coincidencia exacta y única; liberación de nodos obtenidos |
| SmartHome.control usa runBlocking | Servicio utiliza controlAsync existente |
| Embeddings recalculaban K/V dentro de cada cabeza y token | Proyecciones precalculadas; caché protegida de mutación externa |
| NikoActionPlanner / NikoBackendPrewarmer sin consumidores productivos encontrados | Conservados como compatibilidad; no contados como capacidades |
| NikoVisualContext.capture no se conecta a conversación en la base | Conectado ahora a peticiones explícitas de pantalla; Accessibility aporta texto, no píxeles |
| Neural TTS tiene construcción lazy pero ruta normal habla por Android TTS | No se atribuye síntesis neural a la ruta activa |
| ResearchQuality existe pero NikoAiClient seleccionaba por su propia política | No se cuenta el archivo como orquestación activa |

## Superficie conservada

Compose y robot 3D, herramientas declarativas, familias de juegos, cámara, audio, música, aprendizaje adaptativo, MicroGPT, corpus congelado, servidor y companion web. No se eliminan funcionalidades ni se cambia el diseño visual. Los tests existentes de esas áreas siguen siendo la regresión de referencia.

Inventario trazable de todos los archivos versionados de la base: `evidencias/leo-agent-2026-10-06/inventory.json` (hash, tamaño y declaraciones). El inventario cubre todo el árbol; no equivale a una demostración dinámica de cada rama. La revisión profundiza en las rutas citadas; no certifica ausencia total de defectos.


## Evidencia y límites

Estado de esta iteración: IMPLEMENTADO donde se identifica una llamada real; AUTOMATIZADAMENTE PROBADO solo para los casos ejecutados en [LEO_VALIDATION.md](LEO_VALIDATION.md). Toda propiedad del teléfono permanece **PENDIENTE DE VALIDACIÓN FÍSICA**. Los contratos o tipos sin ruta productiva se indican como PLANIFICADO.


Actualización: LeoVisionContext conecta peticiones explícitas de pantalla con observaciones reales de Accessibility, con caducidad. Sin entrada accesible no afirma visión. Offline describe etiquetas; Groq puede interpretar el texto observado, sin herramientas web. Cámara, imágenes y OCR de documentos siguen pendientes. Memoria y observaciones cloud se adjuntan como datos de usuario no confiables, nunca dentro del rol system.
