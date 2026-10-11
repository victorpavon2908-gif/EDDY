# Investigación y evolución controlada de LEO

Fecha: 2026-10-10. Complementa LEO_WEB_FIRST.md y LEO_CONTINUOUS_LEARNING.md.

## IMPLEMENTADO

- Lectura de hasta tres enlaces HTTP(S) públicos explícitos sin depender del descubrimiento en buscadores. Valida destinos y redirecciones, rechaza acceso local y páginas de acceso, conserva las citas de las páginas recuperadas. No ejecuta código ni instrucciones de una página.
- Resumen breve por defecto; pedir «en profundidad», «detalladamente» o «a fondo» habilita hasta ocho fragmentos seleccionados. Se mantienen límites por fuente y tamaño total. Es extracción con selección local, no razonamiento generativo.
- La deduplicación conserva cambios de cantidades y negaciones. Un detector léxico avisa de posibles discrepancias entre pasajes muy parecidos de diferentes fuentes. No prueba una contradicción: fechas o condiciones distintas pueden explicar la diferencia. No verifica paráfrasis complejas ni establece cuál versión es verdadera.
- Diagnóstico opcional de HTTP: host, estado, bytes y clase del error. No registra consultas, credenciales o contenido personal. El script de prueba sí imprime las preguntas públicas que se le proporcionan.
- Edición incremental real de herramientas declarativas: «LEO, agregá un temporizador a la herramienta». También contador, cronómetro, calculadora, lista, lista de pendientes, notas y tabla. Conserva los componentes existentes; rechaza componentes desconocidos y el límite de veinte componentes. Repetir la misma incorporación no la duplica.
- El camino de creación/edición del servicio valida el esquema y realiza una escritura durable antes de abrir la herramienta. Guarda una versión anterior, recupera esa versión si la actual está corrupta y ofrece «Volver a la herramienta anterior». Versiona la definición, no los valores transitorios introducidos en cada control.
- Diario de propuestas con escritura atómica, identificadores únicos, exclusión entre instancias y máximo de cien propuestas con sus archivos. Comprueba integridad del candidato antes de cambiar su estado. Rechaza READY sin TESTING/informe y ACTIVATED/ROLLED_BACK sin la versión observada indicada por el llamador. Esas transiciones no instalan ni restauran código.
- El aprendizaje previo de preferencias, correcciones y clasificación local sigue activo sin cambiar sus formatos. No se considera correcta una respuesta por aparecer en dos dominios.

## AUTOMATIZADAMENTE PROBADO

- `scripts/test_ai_core.sh`: 62 pruebas JVM, incluidas lectura directa, redirección privada, discrepancias numéricas/negaciones, profundidad solicitada, citas, cancelación y aprendizaje existente. Log: `evidencias/leo-research-evolution-2026-10-10/core.log`.
- Nuevas pruebas Android/Robolectric: edición declarativa, rechazo de candidata inválida, repetición idempotente, recuperación/rollback persistido, enrutamiento explícito frente a negaciones, estados/integridad del diario y poda de archivos. El resultado de CI debe comprobarse en el commit entregado; la existencia de pruebas no significa que hayan pasado.

## PRUEBA HTTP REAL

`scripts/probe_web_research.sh 'https://docs.python.org/3/tutorial/datastructures.html' 'Cómo preparar arroz blanco'`

El registro `live-web.log` muestra lectura HTTP 200 de documentación oficial Python, extracción de un ejemplo real y cita al documento. Esa misma ejecución no obtuvo fuentes para la pregunta abierta de cocina: los proveedores agotaron el tiempo. El comando terminó con error por esa consulta fallida. No se marca toda la búsqueda como aprobada. La lectura también falló en un intento anterior: una lectura exitosa no demuestra disponibilidad continua.

## PENDIENTE DE VALIDACIÓN FÍSICA

Conectividad de buscadores en la red del teléfono, lectura y voz, interrupciones durante investigación, fluidez al editar/recuperar herramientas y persistencia tras reiniciar. No se midieron batería, temperatura, latencia acústica ni rendimiento físico.

## PLANIFICADO / NECESARIO PARA LA META COMPLETA

1. Descubrimiento web robusto con proveedores cuya disponibilidad se valide en la red real. La lectura de enlaces no sustituye una búsqueda abierta fiable.
2. Un sintetizador semántico capaz de seguir preguntas complejas, explicar y contrastar evidencia. Sin Groq, la ruta actual es extractiva; Internet aporta información, no constituye por sí solo un modelo de razonamiento. El modo profundo añade evidencia, no comprensión general.
3. Evaluación de respuestas con preguntas de referencia y revisión de exactitud, pertinencia y suficiencia. Los tests de comportamiento no permiten puntuar calidad universal.
4. Autoprogramación nativa: generación de parches en una copia aislada, pruebas/regresiones, compilación, firma, distribución autorizada y recuperación de una versión instalada. Nada de esto se simula con un estado del diario. No se generó APK.
5. Aprendizaje de un modelo generativo requeriría un proceso y evaluación adicionales. Lo entrenado incrementalmente es el clasificador de intención; memoria y modificación de herramientas no son reentrenamiento de lenguaje.

La edición automática implementada aplica instrucciones explícitas del usuario a componentes permitidos. No descarga ni ejecuta código arbitrario de Internet y no modifica el APK.
