# Limpieza de código LEO — 2026-10-12

Base: `89d80c78e7d2fc28edabf4bed6a0599391b6235a`. Rama: `main`.

## Alcance y método

Inventario de los 528 archivos versionados, incluidos 162 Kotlin productivos: [inventario con blobs y tamaños](../evidencias/leo-code-cleanup-2026-10-12/base-inventory.json). Se revisaron entradas Android del manifest, ProGuard/JNI, imports y referencias Kotlin/TypeScript, recursos, catálogos, scripts, workflows, pruebas y documentación. Se siguieron especialmente inicio → servicio → conversación/acciones → respuesta → TTS y preparación/descarga de modelos.

El análisis léxico de declaraciones y referencias señaló cinco archivos Kotlin sin ruta desde las entradas Android: NikoActionPlanner, NikoBackendPrewarmer, ResearchQuality, LocalConversationPrompt y NikoMiniControls. Se verificaron manualmente sus consumidores antes de eliminarlos. Este análisis es una ayuda de alcance, no una prueba formal de ausencia de llamadas dinámicas ni una certificación de todas las ramas del repositorio.

## IMPLEMENTADO

| Retirado | Evidencia de obsolescencia |
|---|---|
| NikoActionPlanner y NikoBackendPrewarmer | Sin consumidores; devolvían null/Unit. Sin datos persistidos que migrar. |
| URL del antiguo backend y carga de propiedades asociada | BuildConfig.NIKO_AI_BASE_URL no tenía lectores. Se conserva BuildConfig para VERSION_NAME. |
| LocalConversationPrompt, catálogo Qwen y flags de selección | Prompt solo usado por su test; ningún runtime activo consumía las tareas Qwen. |
| ResearchQuality | Selector solo llamado desde su propio test; búsqueda activa usa otra política. |
| Copias Groq/ResearchSynthesis dentro de tests y test_streaming.sh | Implementaciones históricas exclusivas del árbol de test; sin llamadas productivas. |
| ProgressiveSpeech y conexiones nulas del servicio | No se construía ninguna instancia; el campo del servicio solo se asignaba a null. |
| NikoNeuralTextToSpeech y SpeechOutputPolicy | Lazy nunca inicializado; no había llamada a speak ni selección del backend. La salida real ya era Android TTS. |
| Descarga Piper y botón para prepararla | Solo alimentaban el sintetizador inactivo. Modelos ya descargados permanecen en disco. |
| Duplicación ensureRecommended/ensureAcousticCore | Ambas recorrían voiceCore con el mismo reintento y corte al fallar. Servicio usa ahora ensureAcousticCore. |
| NikoMiniControls, colores sin consumidores, LeoTransformLauncher e icono mascot_pro | Sin referencias Compose, XML o búsquedas dinámicas observadas. Se conserva el botón de transformación activo. |
| motion y lifecycle-viewmodel-compose | Sin imports/consumidores en el código; lockfile elimina motion y sus dependencias exclusivas. |
| Imports TypeScript sin uso | Eliminados; noUnusedLocals/noUnusedParameters ahora se exigen en lint. |

Lista exacta de archivos eliminados: [removed-files.txt](../evidencias/leo-code-cleanup-2026-10-12/removed-files.txt). Los tests retirados ejercitaban exclusivamente las implementaciones retiradas; una reducción del número de tests no representa una mejora de cobertura. Se actualizan las regresiones del catálogo para impedir descargas de modelos sin motor consumidor y mantener los módulos de escucha en todos los perfiles.

## Conservado

Identificadores Android, aliases Eddy/Niko de actualización, preferencias, esquemas y migraciones, modelos de escucha, Voice ID, embeddings entrenados/JNI, MicroGPT, memoria, aprendizaje, investigación nativa, Accessibility, herramientas, robot y companion web. También scripts de entrenamiento/diagnóstico y evidencias históricas: no son código muerto por no importarse desde la app.

No se borran descargas antiguas del teléfono. La limpieza no cambia el funcionamiento de la memoria semántica. La fachada local todavía recibe contexto que no incorpora a MicroGPT; se conserva esa frontera y la recuperación previa porque tiene efectos sobre el acceso a memoria. Corregir su uso requiere otra intervención funcional, no eliminarla como código muerto.

Las declaraciones opcionales OpenCL del manifest se conservan: la ausencia de referencias Kotlin no prueba ausencia de consumo por bibliotecas nativas. No se eliminan dependencias de infraestructura de pruebas ni se modifica el workflow de entrega explícita de APK.

## AUTOMATIZADAMENTE PROBADO

Ejecución local, JDK 17 / Gradle 8.13, sin APK:

| Validación | Resultado real |
|---|---|
| scripts/test_ai_core.sh | 65 tests aprobados |
| scripts/test_voice_fidelity.sh | 77 tests aprobados |
| scripts/test_agent.sh | 53 tests aprobados |
| npm test | 36 tests aprobados |
| Python unittest discover | 9 tests aprobados |
| npm run lint | Aprobado con detección de código sin uso habilitada |
| npm run build | Aprobado; es el complemento web, no Android |

Logs: [directorio de evidencia](../evidencias/leo-code-cleanup-2026-10-12/). Android completo, compilación JNI y lint se validan mediante el workflow de GitHub asociado al commit; estos tests JVM locales no los sustituyen. No se declara aprobación de CI antes de su ejecución.

## PENDIENTE DE VALIDACIÓN FÍSICA

Instalación nueva, actualización conservando datos y modelos existentes, salida de voz Android, disponibilidad offline, interrupción, pantalla apagada, Bluetooth, llamadas y música. Ninguna medición física de batería, memoria, temperatura, latencia o precisión se deduce de esta limpieza.

## PLANIFICADO

Repetir el análisis de referencias cuando se retiren motores o rutas futuras. Cualquier futura síntesis incremental deberá conectarse a un productor real y validarse. No se afirma que sea posible demostrar mediante búsqueda textual que ya no existe ningún código muerto.
