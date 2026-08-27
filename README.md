# EDDY v0.3.0

**Everyday Digital Dynamic Intelligence** — asistente personal Android por voz con activación “EDDY”, memoria local, acciones del teléfono, conversación con IA, burbuja flotante, ejecución persistente y acceso desde pantalla bloqueada.

## Novedades de v0.3.0

- activación estricta por la palabra **EDDY**, evitando falsos positivos como “Freddy”;
- servicio de micrófono en primer plano para mantener el asistente disponible fuera de la app;
- burbuja flotante movible que recuerda su última posición;
- pantalla de EDDY sobre el bloqueo cuando Android permite la intención de pantalla completa;
- botón **Pausar / Activar** para controlar el asistente sin desinstalarlo ni revocar permisos;
- interfaz que muestra por separado lo que dijo el usuario y la respuesta de EDDY;
- alarmas y temporizadores por voz;
- “EDDY, olvida todo” borra la memoria local y cancela también las sugerencias proactivas programadas;
- Gradle 8.13 reproducible con verificación SHA-256 y JDK 17;
- CI que ejecuta pruebas Android, pruebas del backend, Lint y genera el APK de depuración.

## Activación por voz

Puedes decir directamente:

- `EDDY, abre Spotify`.
- `EDDY, ¿qué hora es?`.
- `EDDY, llévame a Galerías Santo Domingo`.
- `EDDY, pon un temporizador de 3 minutos`.

También puedes usar dos pasos:

1. `EDDY`.
2. EDDY responde `Te escucho` y durante unos segundos acepta tu siguiente frase.

La detección exige la palabra completa **EDDY**; palabras que solo la contienen como parte de otra palabra no activan el asistente.

> Android impone restricciones de batería, micrófono, pantalla completa y procesos en segundo plano. EDDY usa un servicio foreground de micrófono y solicita los permisos disponibles, pero el comportamiento exacto con la pantalla apagada puede variar según fabricante y versión de Android.

## Burbuja y segundo plano

Al salir de la aplicación, EDDY puede mostrar una burbuja flotante si otorgas el permiso **Mostrar sobre otras apps**. Puedes arrastrarla y su posición queda guardada para la próxima vez.

Mientras el asistente está activo, una notificación foreground mantiene visible que EDDY usa el micrófono. Desde la interfaz puedes pulsar **Pausar** para detener el servicio y **Activar** para iniciarlo nuevamente.

## Pantalla bloqueada

Cuando EDDY detecta su palabra de activación con el teléfono bloqueado, intenta despertar brevemente la pantalla y presentar una interfaz segura sobre el bloqueo. Para proteger privacidad, esa pantalla muestra estados genéricos como `Te escucho`, `Pensando` o `Respondiendo` y no expone el contenido completo de la conversación.

En Android 14+ puede ser necesario autorizar el uso de intenciones de pantalla completa desde Configuración.

## Conversación natural con IA

Los comandos del teléfono se resuelven localmente. Cuando una frase no es una acción conocida, EDDY la envía a un backend conversacional si está configurado.

El repositorio incluye el backend en `backend/` y un `render.yaml` para despliegue. La clave de OpenAI **no se incluye en el APK ni en GitHub**; debe configurarse únicamente como variable de entorno `OPENAI_API_KEY` en el servidor.

El backend usa la Responses API y por defecto `gpt-5-mini`. Puedes cambiarlo con `OPENAI_MODEL`.

Si no hay backend configurado, EDDY conserva un modo conversacional local de respaldo y las acciones del teléfono siguen disponibles.

## Memoria local

EDDY guarda localmente:

- hasta 100 turnos recientes de conversación;
- frecuencia de acciones;
- hora habitual de ciertas acciones;
- datos que el usuario declara explícitamente, como nombre, gustos, preferencias, lugar de residencia, trabajo o estudios.

Ejemplos:

- `EDDY, ¿qué sabes de mí?`
- `EDDY, ¿qué recuerdas de mí?`
- `EDDY, olvida todo.`

La última orden elimina la memoria local y cancela las sugerencias proactivas previamente programadas.

## Acciones del teléfono

Actualmente EDDY puede:

- abrir YouTube, WhatsApp, Spotify, Google Maps, Chrome y Gmail;
- abrir la cámara;
- preparar una llamada por número usando el marcador;
- preparar un SMS por número y texto, dejando la confirmación final al usuario;
- crear alarmas mediante la app de reloj;
- crear temporizadores mediante la app de reloj;
- buscar lugares y rutas en mapas;
- decir la hora.

Ejemplos:

```text
EDDY, llama al 88881234
EDDY, manda un mensaje al 88881234 diciendo voy en camino
EDDY, pon una alarma a las 7:30 am
EDDY, pon un temporizador de 5 minutos
EDDY, llévame a Metrocentro Managua
EDDY, abre Gmail
```

Las llamadas usan `ACTION_DIAL` y los mensajes usan el compositor del teléfono; EDDY no realiza llamadas ni envía SMS silenciosamente.

## Modo proactivo

EDDY aprende patrones básicos de uso. Cuando una acción elegible se repite varias veces cerca de la misma hora, puede programar una sugerencia diaria mediante notificación.

Ejemplos de patrones elegibles:

- abrir una app frecuentemente;
- usar la cámara;
- consultar mapas.

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

En Render configura:

```text
OPENAI_API_KEY=<tu clave, solo en el servidor>
OPENAI_MODEL=gpt-5-mini
```

En `local.properties` agrega la URL pública del backend, sin `/chat`:

```properties
eddy.ai.baseUrl=https://TU-SERVICIO.onrender.com
```

Puedes comprobar el backend con:

```text
https://TU-SERVICIO.onrender.com/health
```

## Permisos principales

- `RECORD_AUDIO`: reconocimiento de voz.
- `INTERNET`: conexión con el backend IA.
- `POST_NOTIFICATIONS`: notificación foreground y avisos.
- `SYSTEM_ALERT_WINDOW`: burbuja flotante.
- `FOREGROUND_SERVICE_MICROPHONE`: servicio persistente de micrófono.
- `USE_FULL_SCREEN_INTENT`: presentación de EDDY sobre pantalla bloqueada cuando Android lo autoriza.
- `WAKE_LOCK`: despertar brevemente la pantalla al activar EDDY.

## Requisitos

- Android Studio reciente.
- **JDK 17**.
- Android SDK 36.
- Android 10 / API 29 o superior.
- Servicio de reconocimiento de voz disponible en el dispositivo.

El proyecto usa **Gradle 8.13**. Los lanzadores verifican el SHA-256 del wrapper oficial antes de ejecutarlo.

## Compilar y probar

```bash
git pull origin main
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

El APK de depuración queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

El workflow de GitHub Actions ejecuta además:

```bash
pytest -q backend
```

y publica el APK como artefacto `EDDY-v0.3.0-debug` cuando la validación termina correctamente.

## Estructura relevante

```text
app/src/main/java/com/eddy/assistant/
├── MainActivity.kt
├── EddyWakeActivity.kt
├── actions/
├── ai/
├── background/
├── brain/
├── memory/
├── proactive/
├── ui/
└── voice/

app/src/test/java/com/eddy/assistant/
├── brain/
└── voice/

backend/
├── app.py
├── requirements.txt
└── test_app.py
```

## Voz y diseño

El proyecto usa el TTS disponible en Android y un diseño original de EDDY. No incluye ni clona la voz ni recursos gráficos oficiales de personajes de terceros.
