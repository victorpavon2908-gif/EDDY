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
