# EDDY v0.1

**Everyday Digital Dynamic Intelligence** — primer MVP Android de un asistente personal por voz.

EDDY v0.1 está diseñado para probar el núcleo del producto antes de conectar un modelo de IA remoto: escuchar al usuario, entender comandos locales simples, responder con voz y ejecutar acciones reales en Android.

## Qué funciona ahora

- Interfaz Android con Jetpack Compose.
- Solicitud de permiso de micrófono en tiempo de ejecución.
- Reconocimiento de voz usando `SpeechRecognizer` de Android.
- Respuesta hablada usando `TextToSpeech`.
- Comandos en español:
  - `Hola EDDY` / `EDDY`.
  - `EDDY, ¿qué hora es?`.
  - `EDDY, abre YouTube`.
  - `EDDY, abre WhatsApp`.
  - `EDDY, abre Spotify`.
  - `EDDY, abre la cámara`.
- Arquitectura separada en `brain`, `voice` y `actions` para poder añadir IA real posteriormente.

## Requisitos

- Android Studio reciente.
- JDK 17.
- Android SDK 36 instalado.
- Dispositivo/emulador con Android 10 (API 29) o superior.
- Un servicio de reconocimiento de voz disponible en el dispositivo.

## Abrir el proyecto

1. Clona el repositorio.
2. Abre la carpeta `EDDY` desde Android Studio.
3. Deja que Gradle sincronice las dependencias.
4. Conecta un Android físico o inicia un emulador.
5. Ejecuta `app`.
6. Al tocar el micrófono por primera vez, concede permiso de audio.

## Arquitectura

```text
Tu voz
   ↓
EddySpeechRecognizer
   ↓
LocalBrain
   ↓
AssistantCommand
   ↓
ActionExecutor ──→ Android / apps
   ↓
EddyTextToSpeech
```

## Próximos hitos

### v0.2
- Conectar un modelo de IA conversacional.
- Function/tool calling para que la IA elija acciones.
- Historial de conversación.
- Alarmas y temporizadores.
- Abrir cualquier aplicación instalada.

### v0.3
- Contactos y llamadas con confirmación.
- Mapas/rutas.
- Calendario y recordatorios.
- Memoria local controlada por el usuario.

### v0.4+
- Activación por palabra clave `EDDY`.
- Integración con el rol de asistente de Android.
- Cámara/visión.
- Servicios externos y automatización multi-paso.

## Importante sobre la voz

Este proyecto usa la voz TTS instalada en el dispositivo. No incluye ni clona la voz del personaje EDDY de *Lab Rats*. Una voz original con personalidad similar en energía y estilo puede añadirse posteriormente usando un proveedor de voz compatible.

## Licencia

Proyecto privado/prototipo. Define una licencia antes de distribuirlo públicamente.

## Gradle wrapper incluido

El repositorio incluye un pequeño bootstrap de Gradle compatible con `./gradlew` y `gradlew.bat` que descarga Gradle 8.13 desde `services.gradle.org` la primera vez. Para una publicación de producción se recomienda reemplazarlo por el wrapper oficial generado con `gradle wrapper` desde una instalación confiable de Gradle.
