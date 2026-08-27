# EDDY v0.4.2

**Everyday Digital Dynamic Intelligence** — asistente personal Android por voz, centrado en funciones locales del teléfono, memoria local, búsqueda web mediante un backend propio, burbuja flotante y acceso desde pantalla bloqueada.

## Arquitectura actual

EDDY está dividido en dos partes:

1. **Android**: reconocimiento de voz, palabra de activación `EDDY`, comandos locales, memoria, TTS, acciones del teléfono, casa inteligente y UI.
2. **Backend EDDY Web**: servidor Flask independiente que realiza búsquedas en Internet, ordena resultados, elimina duplicados y devuelve fuentes a la aplicación.

El backend de búsqueda usa actualmente DuckDuckGo como fuente principal y Wikipedia en español como respaldo. No necesita claves de modelos conversacionales para funcionar.

## Activación por voz

Podés decir la orden completa:

```text
EDDY, abrí Spotify
EDDY, abrí Binance
EDDY, buscá noticias de Nicaragua de hoy
EDDY, investigá qué pasó con Bitcoin
```

O usar dos pasos:

```text
EDDY
abrí Calculadora
```

Después de detectar `EDDY`, el asistente mantiene una ventana temporal para recibir la siguiente orden.

## Abrir aplicaciones

EDDY ya no está limitado a una lista fija. Busca entre las aplicaciones lanzables instaladas y compara el nombre que decís con sus etiquetas y paquetes.

Ejemplos:

```text
EDDY, abrí Facebook
EDDY, abrí Binance
EDDY, abrí TikTok
EDDY, abrí Calculadora
```

## Búsqueda web

Cuando decís `buscá`, `investigá`, `averiguá` u otra variante soportada, Android envía solamente la consulta al backend de EDDY.

El backend:

- busca resultados web;
- ordena por relevancia;
- favorece fuentes oficiales/educativas cuando corresponde;
- limita duplicados por dominio;
- usa Wikipedia como respaldo si hay pocos resultados;
- devuelve una respuesta breve y hasta varias fuentes para abrirlas desde la aplicación.

Endpoints:

```text
GET  /health
POST /search
POST /chat   # compatibilidad temporal con APK anteriores
```

Ejemplo de `/health`:

```json
{
  "status": "ok",
  "engine": "eddy-web",
  "provider": "duckduckgo+wikipedia",
  "mode": "search-only",
  "remote_model": false
}
```

## Desplegar backend en Render

El repositorio incluye `render.yaml`.

Configuración equivalente:

```text
Name: eddy-backend
Runtime: Python
Root Directory: backend
Build Command: pip install -r requirements.txt
Start Command: gunicorn app:app
Health Check Path: /health
```

No hace falta configurar claves de modelos conversacionales. Opcionalmente podés ajustar:

```text
EDDY_SEARCH_TIMEOUT=12
EDDY_SEARCH_LIMIT=8
```

Después de desplegar, copiá la URL pública, por ejemplo:

```text
https://eddy-backend-xxxx.onrender.com
```

y en EDDY abrí la pantalla **BACKEND + WEB** para guardarla y probar `/health`.

## Comandos locales

EDDY puede, según permisos y restricciones de Android:

- abrir aplicaciones instaladas;
- abrir cámara;
- preparar llamadas y SMS;
- preparar mensajes de WhatsApp;
- buscar/reproducir en Spotify;
- crear alarmas y temporizadores;
- abrir rutas y lugares en mapas;
- controlar linterna, volumen y brillo;
- mostrar Wi‑Fi, Bluetooth, Internet, ubicación, NFC y otros paneles del sistema;
- consultar batería;
- vibrar el teléfono;
- compartir texto;
- controlar dispositivos locales mediante Home Assistant;
- recordar contexto local y patrones básicos de uso.

Android no permite a aplicaciones normales cambiar silenciosamente algunos ajustes del sistema; en esos casos EDDY abre el panel correspondiente para que el usuario confirme.

## Casa inteligente local

EDDY incluye integración local con Home Assistant mediante REST. La URL y token se guardan en el teléfono.

Ejemplo:

```text
EDDY, apagá la luz de la sala
```

## Voz

EDDY usa el motor TTS instalado en Android. Prioriza voces españolas masculinas cuando el motor expone información suficiente y da preferencia a Nicaragua/Centroamérica/Latinoamérica.

La voz exacta depende de las voces instaladas en el teléfono.

## Segundo plano y pantalla bloqueada

EDDY utiliza un servicio foreground de micrófono, `WAKE_LOCK`, burbuja flotante y una actividad segura para mostrarse sobre la pantalla bloqueada cuando Android lo permite.

El comportamiento exacto puede variar según fabricante, versión de Android y políticas de ahorro de batería.

## Tecnología Android

- Kotlin
- Jetpack Compose
- Material 3
- Android SDK 36
- minSdk 29
- JDK 17
- Gradle 8.13

## Backend

- Python
- Flask
- Gunicorn
- Requests
- BeautifulSoup
- Pytest

## Compilar y probar

```bash
git pull origin main
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
pytest -q backend
```

El APK queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions ejecuta pruebas del backend, pruebas Android, Lint y genera el APK de depuración.
