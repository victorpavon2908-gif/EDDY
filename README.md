# EDDY 0.6.0

Asistente Android por voz, con activación local «EDDY», comandos del teléfono,
memoria local y conversación directa con Gemini. ARM64, incluido Honor X6c.
No requiere un servidor propio para conversar.

## Instalar y usar

1. Instalá el APK `EDDY-v0.6.0-offline-debug` generado por GitHub Actions.
2. Abrí EDDY y concedé el permiso de micrófono. La primera apertura copia el núcleo
   de voz incluido en el APK, sin necesitar Internet.
3. Esperá «Núcleo privado activo». Decí «EDDY, qué hora es» o «EDDY, encendé la linterna».
   También podés decir «EDDY», esperar «Ajá» y dar la orden.
4. Para conversar, abrí **Gemini directo**, guardá tu clave y usá **Guardar y probar**.
5. Para escuchar con la pantalla apagada, permití funcionar en segundo plano en
   los ajustes de batería de Android/Honor.

Después de cada respuesta vuelve a esperar su nombre. El indicador de micrófono
permanece visible durante la escucha local: es normal. Podés detenerla desde la
notificación. No hay autenticación segura del hablante: cualquiera puede decir EDDY.

## Con y sin Internet

| Función | Sin Internet | Con Internet |
| --- | --- | --- |
| Activación y transcripción española | Sí, núcleo incluido | Igual |
| Hora, cálculos, linterna, volumen, alarmas, abrir apps | Sí; según permisos | Igual |
| Voz | Voz española de Android o neuronal ya instalada | Igual |
| Conversación amplia | Limitada; modelo generativo opcional si ya está instalado | Gemini con clave y cuota |
| Información actual | No | Búsqueda de Gemini, con fuentes cuando la utiliza |

Abrir una app no garantiza que sus funciones funcionen sin red. Un teléfono sin voz
española necesita instalarla desde su motor TTS. No se descargan automáticamente
modelos generativos grandes, biometría ni voces adicionales. Se conservan los
modelos locales que el usuario ya tenga instalados.

## Estabilidad

- AudioRecord permanece abierto; se descarta el audio durante respuestas y no se
  envía audio ambiental a Gemini. Una reserva breve conserva el inicio de la orden.
- Una orden a la vez, regreso a modo pasivo y liberación de recursos en su hilo.
- El reconocedor compatible espera el resultado final, sin cancelaciones cada
  14 segundos ni al cambiar la pantalla. Recupera sesiones realmente atascadas.
- Gemini tiene 18 segundos de presupuesto total y hasta tres intentos. Claves
  inválidas, cuota agotada y respuestas bloqueadas no generan reintentos en cadena.
- La voz neuronal espera a que suenen las últimas palabras antes de cerrar audio.
- No se reutilizan respuestas antiguas como si fueran información actual.

La escucha continua corresponde al núcleo local. El reconocedor compatible de
Android puede cerrar sesiones por su cuenta. Llamadas, otras apps, ahorro de batería
 y el interruptor de privacidad pueden interrumpir el micrófono. Falta validar
el comportamiento de audio en un teléfono real; no se garantiza reconocimiento perfecto.

## Compilar

JDK 17, SDK 36, Gradle 8.13:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
python scripts/bundle_voice_models.py
./gradlew :app:assembleDebug
pytest -q backend
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. El script usa el catálogo Kotlin,
valida tamaños y adjunta SHA-256 para comprobar la copia en el teléfono. Los modelos
no se guardan en Git. El APK pesa más porque incluye la voz offline.
GitHub Actions conserva la firma de depuración con su caché existente. Si se pierde
esa caché, una firma distinta puede impedir actualizar un APK anterior. No desinstalés
sin respaldar los datos que necesités. `backend/` es legado y no interviene en Gemini.

## Prueba en Honor X6c

- Primera apertura en modo avión: preparar voz y pedir hora/linterna.
- Cinco minutos en silencio: sin ciclos continuos del micrófono.
- Orden seguida de EDDY sin pausa, y activación en dos pasos.
- Hablar sin llamarlo, también después de una respuesta: no debe ejecutar órdenes.
- Perder/restaurar Internet y comprobar que las funciones locales siguen disponibles.
- Pantalla bloqueada, ahorro de batería y otra app usando el micrófono.
- Respuesta larga: últimas palabras completas y regreso a modo pasivo.

Referencias: [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer),
[Gemini generateContent](https://ai.google.dev/api/generate-content),
[búsqueda con Gemini](https://ai.google.dev/gemini-api/docs/google-search).
