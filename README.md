# EDDY · Android 0.12.0

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
