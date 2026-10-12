# Preparación de LEO como producto

Iteración: 2026-10-12 UTC (11 de octubre en Nicaragua). Esta página describe evidencias técnicas; no certifica que LEO esté listo para venta general.

## IMPLEMENTADO

### Instalación y control del usuario

El conocimiento offline es opcional y no comienza a descargarse en instalaciones nuevas sin elección explícita en Ajustes. La decisión autoriza reanudar la descarga; «Detener y no reanudar automáticamente» la revoca. No elimina el conocimiento ya instalado ni modifica los módulos de voz. Las instalaciones existentes se reconocen con el mismo directorio, formato y marcador.

Actividad, servicio y Ajustes comparten una exclusión de instalación. La transferencia valida el rango al reanudar. Un archivo completo que no supera tamaño/hash se retira para evitar un bucle de reintentos contra el mismo archivo corrupto. Descarga, extracción y cálculo de hash comprueban cancelación; el tiempo de salida de una lectura bloqueada depende de la pila HTTP y sus timeouts.

La activación conserva el directorio anterior hasta publicar el nuevo. Ante un fallo de renombrado intenta restaurar la versión anterior. Un arranque posterior del instalador puede recuperar un renombrado interrumpido. No es una actualización de código Android ni modifica memoria personal.

### Interfaz fiel al producto

Ajustes ya no ofrece descargar Qwen como conversación activa: ese runtime no está conectado. Los archivos y catálogo históricos no se borran. La pantalla identifica MicroGPT, aprendizaje del clasificador, paquete offline, memoria semántica y síntesis web extractiva. Incluye descargar/comprobar/detener el paquete opcional y explica qué consultas salen del teléfono y la dependencia del proveedor de reconocimiento Android.

### Investigación más resistente

La excepción de un proveedor se aísla para conservar respuestas de otros; la cancelación del usuario se propaga. La investigación tiene un límite de coroutine de 22 segundos (20 de presupuesto de red y 2 de margen para síntesis), sujeto a que las llamadas bloqueantes respondan a interrupción/timeouts: no es una medición de latencia física. Los fallos distinguen límite de consultas, acceso rechazado, timeout y conexión. No se eluden bloqueos ni CAPTCHAs.

Los enlaces encontrados sin texto suficiente conservan sus referencias, pero no se marcan como una respuesta investigada aprendible. Se conserva el funcionamiento de lectura directa, citas, alertas de discrepancias y herramientas versionadas de la iteración anterior.

## AUTOMATIZADAMENTE PROBADO

`scripts/test_ai_core.sh` ejecuta regresiones del código de producción de conversación, investigación, clasificador, integridad de descarga y activación/recuperación de directorios. Evidencia en `evidencias/leo-product-readiness-2026-10-12/core.log`.

Robolectric comprueba que la descarga opcional requiere elección persistida, puede revocarse y no crea archivos de descarga por defecto. La CI del commit de entrega ejecuta la suite Android completa, componentes nativos y lint sin generar APK; consultar su resultado, no inferirlo por existir los tests.

Las pruebas de directorios simulan archivos pequeños y fallos de renombrado. No equivalen a instalar 500 MB en un teléfono ni prueban rendimiento o resistencia a cualquier corte de energía.

## PENDIENTE DE VALIDACIÓN FÍSICA: aceptación de un piloto

Registrar versión de LEO, modelo del teléfono, versión de Android y evidencias de cada caso. Usar las herramientas existentes en LEO_VALIDATION.md y conservar resultados sin completar valores inventados.

| Caso | Resultado requerido antes de prometerlo a un comprador |
| --- | --- |
| Primera instalación | Inicio sin cierres, permisos comprensibles, sin descarga del paquete opcional hasta elegirla |
| Descarga opcional | Inicio, cancelación, pérdida de conexión, reanudación y verificación en Wi-Fi y datos móviles |
| Actualización | Mantener preferencias, recuerdos, correcciones y herramientas anteriores |
| Investigación | Preguntas nuevas de cocina, código y actualidad con fuentes pertinentes; contrastar respuesta con las páginas |
| Fallo web | Mensaje honesto y recuperación al restablecer conexión; sin una respuesta anterior presentada como nueva |
| Voz | Wake word, interrupción, pantalla apagada, llamada, música, Bluetooth y ruido en el dispositivo objetivo |
| Recursos | Medir consumo real de batería, temperatura, RAM y tiempos; no usar umbrales inventados como resultados |
| Datos | Confirmar borrado de memoria, aprendizaje desactivado y comportamiento de las consultas externas |
| Herramientas | Crear, añadir un componente permitido, repetir sin duplicar y recuperar la versión anterior |

## BLOQUEADORES PARA VENTA GENERAL

- La disponibilidad de los buscadores públicos sigue sin demostrarse de forma sostenida en redes reales.
- La respuesta web es extractiva; no ofrece todavía razonamiento profundo general ni programación sin límites.
- El sistema modifica herramientas declarativas, no su APK. La generación, evaluación y distribución de cambios nativos sigue pendiente.
- Los resultados físicos anteriores siguen pendientes, no aprobados por una compilación o test JVM.

Un piloto acompañado debe describir únicamente las capacidades implementadas y verificadas. No se asigna un porcentaje de «listo» ni una comparación con otros asistentes sin evaluación comparable.

## Prueba HTTP de esta iteración

`live-web.log` registra una lectura real HTTP 200 de la documentación de Python, con extracción y cita. La pregunta abierta de arroz volvió a terminar sin evidencia suficiente (Bing respondió sin resultados útiles y DuckDuckGo agotó el tiempo). El script salió con error para esa consulta. Esta prueba verifica el nuevo mensaje de fallo; no aprueba la disponibilidad general de búsqueda.
