# EDDY 0.6.0

## Flujo de trabajo

Las entregas se realizan mediante commits en GitHub. La integración continua
comprueba pruebas y Lint, sin generar ni publicar APK automáticamente.
La compilación manual indicada abajo se usa únicamente cuando se solicita.

Asistente Android por voz, con activación local «EDDY», comandos del teléfono,
memoria local y conversación directa con GroqCloud. ARM64, incluido Honor X6c.
No requiere un servidor propio para conversar.

## Instalar y usar

1. Cuando solicités una compilación, instalá el APK que se genere para esa versión.
2. Abrí EDDY y concedé el permiso de micrófono. La primera apertura copia el núcleo
   de voz incluido en el APK, sin necesitar Internet.
3. Esperá «Núcleo privado activo». Decí «EDDY, qué hora es» o «EDDY, encendé la linterna».
   También podés decir «EDDY», esperar «Ajá» y dar la orden.
4. Para conversar, abrí **GroqCloud**, guardá tu clave y usá **Guardar y probar**.
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
| Conversación amplia | Limitada; modelo generativo opcional si ya está instalado | GroqCloud con clave y cuota |
| Información actual | No | Groq Compound, con fuentes cuando la utiliza |

Abrir una app no garantiza que sus funciones funcionen sin red. Un teléfono sin voz
española necesita instalarla desde su motor TTS. No se descargan automáticamente
modelos generativos grandes, biometría ni voces adicionales. Se conservan los
modelos locales que el usuario ya tenga instalados.

La conversación remota usa `llama-3.3-70b-versatile` por defecto; la búsqueda usa
`groq/compound`. Las claves antiguas de Gemini no se reutilizan. Si antes la clave
de Groq estaba en Render, debe configurarse en el teléfono para la conexión directa.
Véase [configuración y validación de GroqCloud](docs/GROQ_CLOUD.md).

## Estabilidad

- AudioRecord permanece abierto; se descarta el audio durante respuestas y no se
  envía audio ambiental a GroqCloud. Una reserva breve conserva el inicio de la orden.
- Una orden a la vez, regreso a modo pasivo y liberación de recursos en su hilo.
- Las palabras de activación se guardan antes de crear el detector nativo. La CI
  prueba su arranque y decodificación con el modelo real, además de pruebas y Lint.
- Activación exclusivamente por «EDDY», sin botones Hablar/Pausar en la pantalla principal.
  El interruptor queda en Ajustes; detener la notificación persiste al reabrir la app.
- No se usa SpeechRecognizer por sesiones para la escucha permanente. Preparación,
  disponibilidad y errores del micrófono tienen estados separados. La recuperación
  espera el cierre del capturador anterior y usa reintentos espaciados.
- GroqCloud tiene 18 segundos de presupuesto total y hasta tres intentos. Claves
  inválidas, cuota agotada y respuestas bloqueadas no generan reintentos en cadena.
- La voz neuronal espera a que suenen las últimas palabras antes de cerrar audio.
- No se reutilizan respuestas antiguas como si fueran información actual.

La escucha continua requiere el núcleo local instalado; hasta que esté listo se
muestra la preparación o el error correspondiente. Llamadas, otras apps, ahorro de
batería y el interruptor de privacidad pueden interrumpir el micrófono. Falta validar
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
Las compilaciones manuales deben conservar la misma clave de firma. Una firma
distinta puede impedir actualizar un APK anterior. No desinstalés
sin respaldar los datos que necesités. `backend/` es legado y no interviene en GroqCloud.

## Prueba en Honor X6c

- Primera apertura en modo avión: preparar voz y pedir hora/linterna.
- Cinco minutos en silencio: sin ciclos continuos del micrófono.
- Orden seguida de EDDY sin pausa, y activación en dos pasos.
- Hablar sin llamarlo, también después de una respuesta: no debe ejecutar órdenes.
- Perder/restaurar Internet y comprobar que las funciones locales siguen disponibles.
- Pantalla bloqueada, ahorro de batería y otra app usando el micrófono.
- Respuesta larga: últimas palabras completas y regreso a modo pasivo.

Referencias: [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer),
[Groq Chat Completions](https://console.groq.com/docs/api-reference),
[búsqueda con Groq Compound](https://console.groq.com/docs/tool-use/built-in-tools/web-search).
