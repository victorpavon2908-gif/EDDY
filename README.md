# EDDY · Android 0.13.0

Aplicación móvil nativa para Android 12 o superior, escrita en Kotlin y Jetpack Compose.
Conserva el identificador `com.eddy.assistant` y las bases SQLite existentes; el asistente de voz mantiene su nombre y palabra de activación **LEO**.

## Abrir y validar

Abrir la raíz en Android Studio, usar JDK 17 e instalar Android SDK 36. No configurar rutas personales de Java en archivos versionados.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Los commits ejecutan pruebas Android y Lint; no generan APK. Las descargas iniciales de modelos necesitan conexión y espacio disponible.

## Funciones

- Activación y reconocimiento local de voz en español, voz sintetizada y modo de servicio en primer plano.
- Memoria local, acciones del teléfono, búsqueda y configuración de proveedores de IA.
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

## Voz progresiva 0.12.1

La conversación por Groq recibe texto por streaming y lo reproduce por frases mientras llega el resto. Usa la clave y el modelo ya configurados, sin nuevos proveedores ni envío del audio. La escucha/transcripción y la síntesis siguen siendo las locales existentes; no es una copia del modelo de voz de ChatGPT ni audio-a-audio nativo.

- Cola ordenada de frases, cancelación de red y audio al interrumpir y sin repetir la respuesta completa al finalizar.
- Actualización visual en memoria, limitada a una cada 80 ms; solo se guarda el resultado final.
- Si la conexión falla tras comenzar, se informa la interrupción sin iniciar otra respuesta encima. Antes del primer texto se mantienen las alternativas de modelo autorizadas por el cliente.
- Búsqueda con fuentes y órdenes del teléfono conservan sus rutas; la investigación no se lee parcialmente antes de validar las fuentes.
- Diagnóstico: **Orden → primer texto** y **Orden → primer sonido**. Estos tiempos empiezan tras aceptar la transcripción, no incluyen ASR. No hay valores medidos en teléfono todavía.

Implementación basada en el [protocolo público de streaming de Groq](https://console.groq.com/docs/text-chat). Ver [pruebas y límites](docs/VOICE_STREAMING.md).

## Usabilidad y voz 0.12.2

- Cada herramienta conserva su estado al volver al asistente y regresar, mediante un contenedor de estado por pantalla.
- Las notas se guardan automáticamente tras una pausa breve de escritura, al salir de Notas y al pasar a segundo plano; muestran el estado de guardado. El guardado usa preferencias locales con escritura asíncrona, no sincronización en nube.
- El conversor acepta coma decimal y muestra error para entradas inválidas en lugar de convertirlas silenciosamente a cero.
- Los bloques grandes recibidos por streaming se dividen antes de la síntesis; cada frase se limita a 240 caracteres y se evita cortar pares Unicode de emojis.

La revisión anterior `eae91c6` aprobó pruebas Android y Lint en GitHub (ejecución 36722239966). Cada nueva revisión debe validar su propio workflow. La fluidez y los cambios de pantalla aún requieren verificación visual y auditiva en dispositivo.

## Transformaciones multimedia 0.13.0

Botón **Transformarme** y órdenes como «LEO, conviértete en una cámara» abren herramientas dentro de EDDY: cámara, video con audio, grabadora de audio y música, junto a la calculadora, cronómetro y demás utilidades existentes.

- Cámara frontal/trasera, captura y exportación de archivos privados.
- Grabación con permisos, pausa exclusiva del micrófono del asistente y liberación al terminar.
- Reproductor de archivos locales, búsqueda de muestras Apple/iTunes y enlaces a YouTube/Spotify.
- Modo fiesta con baile y frases ocasionales de LEO; interruptor para desactivarlo.
- Órdenes locales de transformación antes de la búsqueda web.

Ver [funcionamiento, límites y comprobaciones en teléfono](docs/POLYMORPHIC_TOOLS.md). La música de los resultados es una vista previa; las reacciones son programadas. Las nuevas funciones todavía requieren validación física.
