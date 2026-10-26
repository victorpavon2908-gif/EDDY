# EDDY / LEO: ajustes y preparación móvil — 26 de octubre de 2026

## Alcance entregado

- Proyecto exclusivamente Android: retirados React, Vite, Express, configuración Gemini web, archivos de despliegue Render, dependencias npm y pruebas de la aplicación web.
- Se conserva la búsqueda pública de Android, sus modelos, voz, memoria y herramientas.
- Ajustes con navegación directa a siete categorías y acceso a la guía completa sin conexión.
- Guía con búsqueda y texto seleccionable desde Inicio y Ajustes; copia publicada y copia empaquetada verificadas por una prueba.
- Las descargas acústicas muestran errores recuperables y preservan la cancelación. Detener la enciclopedia espera el cierre de la tarea antes de permitir otra descarga.
- Abrir Ajustes no inicia el servicio de voz cuando la escucha está desactivada o falta permiso.
- El borrado informa errores de almacenamiento; la eliminación fallida del archivo de memoria ya no se ignora.
- La búsqueda distingue enlaces sin texto, extractos y páginas leídas. Corregida la regresión que fallaba en el CI anterior.
- Python y la guía se comprueban en la automatización Android. El script de pruebas del núcleo usa el conjunto real de fuentes Android, evitando su antigua lista incompleta de dependencias.

## Validación local ejecutada

- 53 pruebas JVM de agente, memoria, citas y políticas: aprobadas.
- 77 pruebas JVM de voz y controles: aprobadas.
- 27 pruebas JVM de búsqueda nativa: aprobadas, incluyendo el caso que fallaba en el CI anterior.
- 13 pruebas Python: aprobadas, incluyendo integridad de la guía y ausencia de puntos de entrada web.
- Integridad de los recursos MicroGPT: aprobada.
- Revisión de espacios y sintaxis del script de pruebas: aprobada.

La compilación Android local no pudo resolver el plugin AGP 8.13.2 en este entorno. La integración completa, pruebas Android y Lint se ejecutan en GitHub Actions sobre el commit publicado; su resultado debe consultarse por SHA. Las pruebas JVM portátiles son un subconjunto y no se deben sumar al total de CI.

## Condiciones para producción

La entrega de código no equivale a certificar el comportamiento físico. Antes de distribuir:

1. Exigir que pruebas Android y Lint del commit de lanzamiento terminen correctamente.
2. Revisar Ajustes y Guía en teléfono: pantalla pequeña, letra grande, rotación, teclado, navegación por categorías y lector de pantalla.
3. Probar permiso de micrófono rechazado y concedido, escucha pausada, reinicio, registro vocal, reconocimiento con ruido y Bluetooth.
4. Completar la descarga real de enciclopedia y modelos; probar corte de red, cancelación, espacio insuficiente y consulta sin conexión.
5. Comprobar búsquedas reales con fuentes, sitios bloqueados y pérdida de red; no confundir encontrar una URL con leer su contenido.
6. Verificar cámara, video, audio, música y notas en un dispositivo; revisar conservación de datos al actualizar.
7. Auditar el borrado con el asistente activo: este cambio detecta errores de archivos, pero no certifica que todos los módulos en memoria de sesión se reinicien o que todo aprendizaje persistente se elimine.
8. Usar la identidad de firma existente y una versión de lanzamiento consistente. Este trabajo no genera APK ni publica una versión de tienda.

La interfaz actual es Kotlin/Compose y parte de la lógica sigue en Kotlin. Los scripts de entrenamiento/preparación usan Python; la migración total del cerebro a Python no está completada.

Los registros históricos pueden mencionar el producto web retirado. Se conservan como evidencia, no como funcionalidad ni instrucciones de despliegue vigentes. La utilidad de métricas `scripts/calcular_metricas.mjs` es una herramienta de consola independiente para mediciones móviles, sin servidor ni dependencias npm.
