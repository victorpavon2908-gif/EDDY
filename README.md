# LEO Assistant 0.11.0 (Web React)

Compañero personal inteligente con control por voz en español, robot 3D articulado interactivo, búsqueda web en tiempo real con fuentes citadas, suite de herramientas integradas y compatibilidad con Gemini y GroqCloud.

## Características Principales

- **Robot 3D Articulado**: Modelo 3D interactivo (`leo_robot.glb`) en WebGL/Three.js con transiciones suaves (crossfading de 280 ms) entre animaciones:
  - *Idle*: Respiración y postura relajada con seguimiento sutil del cursor.
  - *Listen*: Inclinación atenta de cabeza durante la escucha.
  - *Think*: Gesto de concentración durante la búsqueda/procesamiento.
  - *Talk*: Gestos y articulaciones activas durante la respuesta de voz.
  - *Acciones manuales y por voz*: Bailar («Leo, bailá»), saltar («Leo, saltá»), girar («Leo, girá») y saludar («Leo, saludá»), además de activación al tocar al robot.
- **Control por Voz & Interrupciones**:
  - Reconocimiento de voz en vivo en español (Web Speech API).
  - Interrupción inmediata («Leo, pará», «pará», «detener») que cancela de inmediato la voz y la búsqueda en curso.
  - Síntesis de voz neural en español con control de tono y velocidad.
  - Visualizador de ondas neuronales (`NeuralWaveform`) con 38 barras reactivas al volumen acústico y relación señal/ruido (SNR).
- **Cerebro Local (Local Brain)**:
  - Resolución matemática offline rápida («cuánto es 45 * 12», «raíz de 144», porcentajes).
  - Consulta de hora y batería del dispositivo.
  - Activación de linterna y accesos directos (WhatsApp, YouTube, Spotify, Google Maps).
  - Memoria local de preferencias y hechos con opción de borrado.
- **Búsqueda Web y Síntesis de Información**:
  - Búsqueda en Wikipedia, DuckDuckGo y fuentes de noticias sin necesidad de claves externas.
  - Citas numeradas directas en pantalla con enlaces a fuentes originales.
  - Soporte para Gemini API (`@google/genai`) y clave opcional de GroqCloud (Llama 3.3).
- **Suite de Aplicaciones Embebidas**:
  - Calculadora con histórico de operaciones.
  - Cronómetro con vueltas de alta precisión.
  - Temporizador con presets y alerta auditiva.
  - Reloj mundial con husos horarios internacionales.
  - Bloc de notas local.
  - Conversor de unidades (distancia, peso, temperatura).
- **Telemetría y Diagnóstico de Voz**:
  - Medición en tiempo real de dBFS, ruido de fondo y SNR.
  - Banco de pruebas de 100 llamadas (True Positives, False Negatives, False Positives).
- **Domótica (Casa Inteligente)**:
  - Integración y prueba de entidades de Home Assistant (luces, ventilador, termostato, cerradura).

## Requisitos y Ejecución

- **Node.js**: 22+
- **Comandos**:
  - Desarrollo: `npm run dev`
  - Construcción de producción: `npm run build`
  - Verificación de tipos: `npm run lint`

## 12. Anexos — Evidencias técnicas

Repositorio: [victorpavon2908-gif/EDDY](https://github.com/victorpavon2908-gif/EDDY).

- [README.md](README.md) — descripción de LEO 0.11.0 y características.
- [docs/LEO_011_PHYSICAL_TEST_PROTOCOL.md](docs/LEO_011_PHYSICAL_TEST_PROTOCOL.md) — protocolo de validación física.
- [docs/LEO_011_PHYSICAL_TEST_RESULTS.md](docs/LEO_011_PHYSICAL_TEST_RESULTS.md) — plantilla de resultados; estado **NO EJECUTADO**.
- [docs/LEO_PHASE3_VOICE_VALIDATION.md](docs/LEO_PHASE3_VOICE_VALIDATION.md) — validación de voz y banco de 100 llamadas.
- [evidencias/README.md](evidencias/README.md) — matriz de métricas cuantitativas y reglas para adjuntar evidencia por medición.
- [evidencias/mediciones.csv](evidencias/mediciones.csv) — registro central de mediciones reales por intento.
- [scripts/calcular_metricas.mjs](scripts/calcular_metricas.mjs) — cálculo reproducible de tasas, medianas, precisión, consumo e incidentes a partir del CSV.
- [src/services/voiceService.ts](src/services/voiceService.ts) — reconocimiento, síntesis y diagnóstico de audio.
- [src/services/localBrain.ts](src/services/localBrain.ts) — interpretación local de comandos.
- [src/services/memoryStore.ts](src/services/memoryStore.ts) — configuración, memoria, historial y domótica.

El repositorio incluye ahora una estructura explícita de **métricas cuantitativas + evidencia por medición**. Los valores permanecen **NO EJECUTADO/PENDIENTE** hasta registrar observaciones reales; el proyecto no inventa resultados.
