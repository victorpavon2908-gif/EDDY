# EDDY v0.2

**Everyday Digital Dynamic Intelligence** — asistente personal Android por voz, con activación por nombre, memoria local, acciones del teléfono, conversación con IA y avisos proactivos.

## Qué hace esta versión

### 1. Activación por voz

EDDY mantiene sesiones de reconocimiento mientras la app está abierta y no necesitas tocar un botón.

Puedes decir directamente:

- `EDDY, abre Spotify`.
- `EDDY, ¿qué hora es?`.
- `EDDY, llévame a Galerías Santo Domingo`.

También puedes usar dos pasos:

1. `EDDY`.
2. EDDY muestra `Te escucho` y durante unos segundos acepta tu siguiente frase.

El filtro de palabra clave evita que una conversación ambiental cualquiera se ejecute como una orden.

> Nota: esta versión usa `SpeechRecognizer` de Android. El hotword de bajo consumo funcionando permanentemente aun con la app cerrada requiere un motor de wake-word/servicio dedicado y será una etapa posterior.

### 2. Conversación natural con IA

Los comandos del teléfono se resuelven localmente. Cuando una frase no es una acción conocida, EDDY la envía a un backend conversacional si está configurado.

El repositorio incluye el backend en `backend/` y un `render.yaml` listo para desplegar en Render.

La clave de OpenAI **no se incluye en el APK ni en GitHub**. Debe vivir únicamente como variable de entorno `OPENAI_API_KEY` en el servidor.

El backend usa la Responses API y por defecto `gpt-5-mini`. Puedes cambiarlo con `OPENAI_MODEL`.

Si no hay backend configurado, EDDY mantiene un modo conversacional local de respaldo y las acciones del teléfono siguen funcionando.

### 3. Memoria real de contexto

EDDY guarda localmente:

- hasta 100 turnos recientes de conversación;
- frecuencia de acciones;
- hora habitual de ciertas acciones;
- algunos datos que el usuario declara explícitamente, por ejemplo `me llamo...`, `me gusta...`, `prefiero...`, `vivo en...`, `trabajo en/como...` y `estudio...`.

Ejemplos:

- `EDDY, ¿qué sabes de mí?`
- `EDDY, ¿qué recuerdas de mí?`
- `EDDY, olvida todo.`

La memoria se guarda con `SharedPreferences` en el dispositivo.

### 4. Acciones del teléfono

Actualmente EDDY puede:

- abrir YouTube, WhatsApp, Spotify, Google Maps, Chrome y Gmail;
- abrir la cámara;
- preparar una llamada por número usando el marcador;
- preparar un SMS por número y texto, dejando la confirmación final al usuario;
- crear una alarma mediante la app de reloj;
- buscar lugares y rutas en mapas;
- decir la hora.

Ejemplos:

```text
EDDY, llama al 88881234
EDDY, manda un mensaje al 88881234 diciendo voy en camino
EDDY, pon una alarma a las 7:30 am
EDDY, llévame a Metrocentro Managua
EDDY, abre Gmail
```

Las llamadas usan `ACTION_DIAL` y los mensajes usan el compositor del teléfono; EDDY no realiza llamadas ni envía SMS silenciosamente.

### 5. Modo proactivo

EDDY aprende patrones básicos de uso. Cuando una acción elegible se repite varias veces cerca de la misma hora, puede programar una sugerencia diaria mediante notificación.

Ejemplos de patrones elegibles:

- abrir una app frecuentemente;
- usar la cámara;
- consultar mapas.

Android 13 o superior solicita permiso para notificaciones.

## Flujo principal

```text
Micrófono
   ↓
EddySpeechRecognizer
   ↓
WakeWordGate ("EDDY")
   ↓
LocalBrain
   ├── Acción conocida ──→ ActionExecutor ──→ Android
   └── Conversación ─────→ EddyAiClient ───→ backend /chat
                                  ↓
                              EddyMemory
                                  ↓
                         EddyTextToSpeech
```

## Configurar el backend IA

### 1. Desplegar en Render

El repositorio incluye `render.yaml`.

En Render crea el servicio desde este repositorio y configura:

```text
OPENAI_API_KEY=<tu clave, solo en Render>
OPENAI_MODEL=gpt-5-mini
```

No subas `OPENAI_API_KEY` al repositorio ni la pongas dentro de Android.

### 2. Poner la URL del backend en Android

En tu `local.properties` agrega la URL pública del servicio, sin `/chat`:

```properties
eddy.ai.baseUrl=https://TU-SERVICIO.onrender.com
```

Después sincroniza Gradle y recompila la app.

Puedes comprobar el backend abriendo:

```text
https://TU-SERVICIO.onrender.com/health
```

## Permisos

- `RECORD_AUDIO`: reconocimiento de voz.
- `INTERNET`: conexión con el backend IA.
- `POST_NOTIFICATIONS`: avisos proactivos en Android 13+.

## Requisitos

- Android Studio reciente.
- JDK 17.
- Android SDK 36.
- Android 10 / API 29 o superior.
- Servicio de reconocimiento de voz disponible en el dispositivo.

## Ejecutar

```bash
git pull origin main
```

Luego en Android Studio:

1. **File → Sync Project with Gradle Files**.
2. **Build → Rebuild Project**.
3. Ejecuta `app` en el teléfono.
4. Concede micrófono y, si corresponde, notificaciones.
5. Di `EDDY`.

## Estructura relevante

```text
app/src/main/java/com/eddy/assistant/
├── MainActivity.kt
├── actions/
│   └── ActionExecutor.kt
├── ai/
│   ├── EddyAiClient.kt
│   └── EddyFallbackConversation.kt
├── brain/
│   ├── AssistantCommand.kt
│   └── LocalBrain.kt
├── memory/
│   └── EddyMemory.kt
├── proactive/
│   ├── EddyProactiveReceiver.kt
│   └── EddyProactiveScheduler.kt
├── voice/
│   ├── EddySpeechRecognizer.kt
│   ├── EddyTextToSpeech.kt
│   └── WakeWordGate.kt
└── ui/
    └── ...

backend/
├── app.py
└── requirements.txt

render.yaml
```

## Voz y diseño

El proyecto usa el TTS disponible en Android y un diseño original de EDDY. No incluye ni clona la voz ni los recursos gráficos oficiales del personaje de *Lab Rats*.
