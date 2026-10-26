# LEO · Android 0.15.0 (100% Soberano)

Identidad visual actual: [emblema negro proporcionado por el usuario](docs/design/LEO_EMBLEM.md), en sustitución del robot 3D.

Aplicación móvil nativa para Android 12 o superior, escrita en Kotlin y Jetpack Compose.
La identidad pública del proyecto es **LEO**. Ver [identidad y compatibilidad para auditoría](docs/IDENTIDAD_LEO.md).

Conserva el identificador `com.eddy.assistant` y las bases SQLite existentes; el asistente de voz mantiene su nombre y palabra de activación **LEO**.

## Novedades 0.15.0 (Soberanía Total — Cero Dependencia de GPT, Gemini o Modelos Externos)

- **Corazón Emocional Multiturno (`LeoEmotionalHeart`)**: seguimiento afectivo de sesión (valencia, activación y trayectoria emocional: recuperación, fatiga sostenida o entusiasmo) con vocabulario español y nicaragüense ampliado (`LeoEmotionClassifier`), modulación empática de respuestas y abstención estricta ante negaciones, citas o preguntas.
- **Motor Autodidacta y Autoentrenamiento Local (`LeoAutodidactEngine`)**: aprendizaje directo de reglas enseñadas por el usuario (*«LEO, aprendé que cuando te diga X me respondás Y»*), perfil de afinidad temática y ciclos autónomos de autoentrenamiento supervisado (`runSelfTrainingCycle`) sobre `OnlineIntentNetwork` con compuerta de no-regresión (`LeoAdaptiveTrainer`).
- **Motor RAG Multimodal Local con Índice Vectorial Híbrido (`LeoLocalRagEngine`)**: ingesta y fragmentación semántica de documentos locales (PDF, OCR de fotos, notas de voz y apuntes), ranking híbrido con vectores densos L2 de 64 dimensiones (`EddyTransformerEmbedder`) + BM25 léxico y síntesis extractiva con citas.
- **Sincronización Cifrada Soberana Teléfono ↔ Web (`LeoEncryptedSync`)**: cifrado autenticado `AES-256-GCM` con derivación `PBKDF2-HMAC-SHA256` (60 000 iteraciones) y verificación de integridad `SHA-256` sin depender de servidores externos.
- **Escucha Superior, Wake-Word Acústico de Ultrabajo Consumo y Full-Duplex (`LeoAcousticWakeScorer`, `LeoDuplexPolicy`, `TranscriptQuality`)**: prefiltro acústico PCM 16 kHz por envolvente RMS, ZCR, banda formántica vocal y cadencia bisílaba (*«Le-o»*), rechazo de eco acústico/espectral (`isAcousticEcho`), frases naturales de barge-in y reparador fonético ASR (`repairAsrTranscript`).
- **Agente Autónomo de Acciones Encadenadas en Pantalla con Confirmación Segura (`LeoUiStepPlanner.planLocalChainStep`, `NikoUiTaskPolicy.evaluateWithConfirmation`)**: planificación local paso a paso sobre el árbol de Accesibilidad y barrera de confirmación explícita para acciones modificadoras o cadenas largas.
- **Evaluador Ejecutable del Protocolo Físico y Autocalibración (`LeoHardwareBenchmark`)**: evaluación determinista de latencia p95, falsos positivos/h, corte full-duplex, fluidez 60 FPS, batería y rechazo de eco con ajuste automático de `LeoVoiceTuningProfile`.
- **Evolución «Eddy Lab Rats» (`LeoEddyPersona`, `LeoLabProtocols`, `LeoLiveVisionAnalyzer`, `LeoEpisodicTimeline`)**:
  - **Personalidad Dinámica (`LeoEddyPersona`)**: conmutación por voz entre *Modo Eddy Clásico* (ingenioso y leal, respetando límites de humor y estados emocionales), *Modo Misión / Centro de Mando* y *Modo Enfoque Empático*, con prosodia expresiva vinculada al pulso del emblema.
  - **Macro-Protocolos Biónicos y Conciencia de Sensores (`LeoLabProtocols`)**: ejecución encadenada con una sola orden (*Protocolo Estudio*, *Protocolo Nocturno*, *Protocolo Emergencia*, *Protocolo Laboratorio* y protocolos personalizados) más alertas proactivas por sensores físicos (oscuridad en lux, golpe/caída en G, temperatura de batería y nivel crítico).
  - **Ojos de Eddy — Visión en Vivo y Escáner OCR Local (`LeoLiveVisionAnalyzer`)**: descripción de escena en tiempo real (personas, objetos, iluminación y texto OCR) e indexación directa en la base RAG local (*«escaneá este documento»*).
  - **Memoria Episódica Autobiográfica y Destilador Cognitivo (`LeoEpisodicTimeline`)**: línea de tiempo de actividades (*«¿qué hicimos hoy?»*, *«¿en qué quedamos?»*) y destilación continua de investigaciones en fichas permanentes para uso offline.

## Novedades 0.14.0

- Diseño perla y verde oscuro, controles más legibles y respuesta completa seleccionable con todas sus fuentes.
- Conversación espontánea opcional en Ajustes: LEO puede iniciar una charla y deja hasta 12 segundos para responder sin repetir su nombre.
- Interpretación del contexto y síntesis web mejoradas; acepta respuestas breves con citas y valida estrictamente sus referencias.
- El robot 3D del complemento web se carga por separado del inicio.
- **Motor matemático, científico y conversor determinista local** (`NikoMathEngine` y `LocalBrain` web): soporte nativo para funciones científicas (`sqrt`, `cbrt`, `sin`, `cos`, `tan`, `log`, `ln`, `abs`, factoriales), constantes (`pi`, `e`), porcentajes compuestos, descuentos, números en español y conversión de unidades en lenguaje natural (longitud, masa, temperatura, volumen, datos, tiempo y velocidad).
- **Inteligencia de código local ampliada** (`LeoCodeIntelligence`): generación y auditoría estática local para 7 arquitecturas verificadas (calculadora en Python, conversor de unidades en Python, gestor de tareas en Python, repositorio CRUD SQLite parametrizado en Python, validador de contraseñas en Python, reintento exponencial con corrutinas en Kotlin y caché LRU genérica $O(1)$ en TypeScript).
- **Memoria personal determinista y paridad web** (`LeoPersonalMemory` / `MemoryStore`): soporte ampliado de categorías personales (nombre, profesión, color, ciudad, meta, comida, bebida, pasatiempo, lenguaje de programación, música, lectura, cine, deporte) con reemplazo sin duplicados, retractación explícita, resumen de preferencias y paridad entre Android y el complemento web.
- **Currículum offline ampliado** (`brain/leo_seed_knowledge.jsonl`): 60 entradas verificadas de conocimiento local offline en español, programación, arquitectura Android/Kotlin, algoritmos, seguridad y geografía de Nicaragua.

Ver [alcance, pruebas y límites de esta revisión](docs/LEO_014_VALIDATION.md).

## Abrir y validar

Abrir la raíz en Android Studio, usar JDK 17 e instalar Android SDK 36. No configurar rutas personales de Java en archivos versionados.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Los commits ejecutan pruebas Android y Lint; no generan APK. Las descargas iniciales de modelos necesitan conexión y espacio disponible.

## Funciones

- Activación y reconocimiento local de voz en español, voz sintetizada y modo de servicio en primer plano.
- Memoria local, acciones del teléfono y búsqueda web nativa.
- Calculadora, notas, reloj, cronómetro, temporizador y conversor integrados.
- Diagnóstico de voz y protocolo de medición física con evidencia.

## Recuperación y mejoras 0.12.0

Se recuperó la aplicación nativa desde `66930d1`, conservando las mejoras posteriores de la versión web como complemento independiente. Gradle vuelve a validar Android, sin redirigir sus tareas a npm.

- Eliminada la ruta de JDK de una computadora Windows que impedía ejecutar Gradle en otras máquinas.
- Actualizaciones de preferencias por eventos, sin consultas cada 500 ms.
- Botón Atrás vuelve al asistente desde las herramientas.
- Herramientas desplazables con márgenes para barras del sistema y teclado; calculadora adaptable al ancho disponible.
- Estado de herramientas conservado al recrear la pantalla; relojes dejan de actualizar la interfaz cuando no está visible.
- Temporizador basado en tiempo monotónico para evitar acumulación de retrasos. Es una herramienta visual: no sustituye una alarma del sistema ni promete avisos con la aplicación cerrada.
- Errores de preparación recuperables y eliminación de la rama inalcanzable de descarga Qwen; el cerebro activo sigue siendo Kotlin/MicroGPT.

Ver [validación de esta revisión](docs/ANDROID_012_VALIDATION.md). Consumo, latencia del micrófono y funcionamiento físico siguen pendientes de prueba en un teléfono.

## Complemento web y evidencias

El código React se conserva para no perder trabajo previo; sus pruebas no equivalen a pruebas Android. Ver [documentación web](docs/WEB_COMPANION.md).

- [Métricas y evidencia](evidencias/README.md)
- [Protocolo físico](docs/LEO_011_PHYSICAL_TEST_PROTOCOL.md)
- [Registro de mediciones](evidencias/mediciones.csv)

## Voz y limpieza de código (2026-10-12)

La salida activa utiliza el TTS de Android. Se retiraron el transporte Groq histórico, la cola progresiva desconectada y el sintetizador neuronal que nunca se inicializaba. Se conservan el reconocedor principal, el respaldo local, la cancelación por generación de turno y la instrumentación de voz.

Ver [rutas de voz vigentes](docs/VOICE_STREAMING.md) y [alcance, eliminaciones y validación de la limpieza](docs/LEO_CODE_CLEANUP.md). Las capacidades físicas siguen **PENDIENTES DE VALIDACIÓN FÍSICA**.

## Usabilidad y voz 0.12.2

- Cada herramienta conserva su estado al volver al asistente y regresar, mediante un contenedor de estado por pantalla.
- Las notas se guardan automáticamente tras una pausa breve de escritura, al salir de Notas y al pasar a segundo plano; muestran el estado de guardado. El guardado usa preferencias locales con escritura asíncrona, no sincronización en nube.
- El conversor acepta coma decimal y muestra error para entradas inválidas en lugar de convertirlas silenciosamente a cero.
- La cola de streaming de aquella versión se retiró el 2026-10-12 al comprobar que ya no tenía productores activos.

La revisión anterior `eae91c6` aprobó pruebas Android y Lint en GitHub (ejecución 36722239966). Cada nueva revisión debe validar su propio workflow. La fluidez y los cambios de pantalla aún requieren verificación visual y auditiva en dispositivo.

## Transformaciones multimedia 0.13.0

Botón **Transformarme** y órdenes como «LEO, conviértete en una cámara» abren herramientas dentro de LEO: cámara, video con audio, grabadora de audio y música, junto a la calculadora, cronómetro y demás utilidades existentes.

- Cámara frontal/trasera, captura y exportación de archivos privados.
- Grabación con permisos, pausa exclusiva del micrófono del asistente y liberación al terminar.
- Reproductor de archivos locales, búsqueda de muestras Apple/iTunes y enlaces a YouTube/Spotify.
- Modo fiesta con baile y frases ocasionales de LEO; interruptor para desactivarlo.
- Órdenes locales de transformación antes de la búsqueda web.

Ver [funcionamiento, límites y comprobaciones en teléfono](docs/POLYMORPHIC_TOOLS.md). La música de los resultados es una vista previa; las reacciones son programadas. Las nuevas funciones todavía requieren validación física.

## Rendimiento y estabilidad 0.13.1

- Capturas recientes: lectura fuera del hilo de interfaz y selección de las 30 más recientes sin ordenar toda la carpeta ni consultar fechas repetidamente.
- El indicador de reproducción actualiza únicamente su propio componente; no vuelve a componer la pantalla y el robot cada 500 ms. Arrastrar el control realiza un solo salto al soltarlo.
- Búsquedas cancelables: cierre de conexión, dos trabajadores y cola limitada. Una respuesta anterior no reemplaza resultados ni el estado de carga de una nueva petición. Salir de Música cancela la búsqueda.
- Lectura del nombre de archivos de proveedores externos fuera del hilo de interfaz. No se inicia reproducción si el proveedor responde después de salir de la pantalla.
- El fin de una frase de LEO no restaura el volumen por encima de la reducción solicitada por Android.

Pruebas de regresión para cancelación de conexión bloqueada, búsquedas superpuestas, salida de pantalla y selección de archivos. Son correcciones verificables de código; aún no hay mediciones de latencia, FPS o batería en teléfono.


## Ingeniería del agente LEO (2026-10-06)

Arquitectura y límites comprobables: [LEO_ARCHITECTURE](docs/LEO_ARCHITECTURE.md), [validación](docs/LEO_VALIDATION.md) y [matriz de evidencia](docs/LEO_EVALUATION.md). La nueva coordinación preserva los motores existentes; no implica full-duplex principal, autonomía abierta o superioridad comercial comprobada.

## Búsqueda: estabilidad y fuentes

La revisión del 2026-10-26 incorpora proveedores web en paralelo, cancelación de consultas, errores visibles y citas vinculadas a las fuentes mostradas. Detalles, pruebas y límites pendientes: [SEARCH_RELIABILITY](docs/SEARCH_RELIABILITY.md).
