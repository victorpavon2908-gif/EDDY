# LEO 0.14.0 · Conversación y presentación

## Cambios

Diseño Android con fondo perla, verde oscuro y acentos cálidos. Controles y texto pequeños ampliados; robot central adaptable al espacio. La tarjeta mantiene su resumen compacto y permite abrir la respuesta completa, seleccionar/copiar texto y abrir todas sus fuentes.

Los mensajes de contexto priorizan la última petición, conectan respuestas breves con la propuesta anterior, evitan interrogatorios y no prometen percepción o recuerdos inexistentes. La personalidad ingeniosa añade humor contextual sin imitar diálogos de personajes.

La síntesis de búsqueda acepta una respuesta breve sin detalles redundantes. Solo acepta índices de fuentes enteros y válidos, elimina afirmaciones duplicadas y descarta una supuesta pregunta que sea una afirmación. El prompt trata los resultados como datos no confiables y pide distinguir fechas de publicación y de los hechos. Estas medidas no garantizan la veracidad semántica de cada respuesta; dependen del modelo y las fuentes existentes.

## Conversación espontánea

Desactivada por defecto. Activar en Ajustes → Conversación espontánea o decir «activa conversación espontánea». Para apagarla: «desactiva conversación espontánea».

Solo inicia invitaciones con la pantalla principal de LEO en primer plano, escucha habilitada, voz preparada y sin otra herramienta, captura, tarea, conversación, música, llamada o modo silencioso detectados. Espera al menos 45 segundos continuos de disponibilidad; tras una entrada del usuario añade 90 segundos de espera. Máximo dos invitaciones sin contestación, separadas por al menos cinco minutos. Detener por voz suprime nuevas invitaciones hasta otra interacción.

Las invitaciones iniciales son tres frases locales rotativas; las respuestas posteriores usan el contexto y los proveedores configurados. No existe vigilancia ambiental nueva ni un modelo de personaje autónomo. Con la opción activa, tras una respuesta se abre una ventana de hasta 12 segundos para continuar sin repetir LEO. Se cancela al entrar una nueva orden o interrumpir. Se conservan los motores acústicos y la palabra de activación.

## Validación

Verificación local: 36 pruebas web, 5 pruebas Python, TypeScript, compilación web y validación de los assets MicroGPT correctos. Se añadieron regresiones Android de iniciativa y referencias de búsqueda; su ejecución y Lint corresponden al workflow de Android del commit publicado.

El JS principal web pasa de 827,35 kB (222,54 gzip) a 242,43 kB (72,55 gzip). El robot queda en un fragmento de 583,79 kB (150,29 gzip). Se difiere la descarga del 3D; no se reduce ese coste total ni se acredita una mejora de latencia Android.

Revisión por subsistemas y pruebas automatizadas no equivalen a ausencia de todos los fallos. Quedan pendientes en teléfono: presentación con tamaño de fuente grande, permisos, ventana de continuación, interrupción real de voz, cambio de app, pantalla bloqueada, llamadas, música y autonomía. No hay mediciones nuevas de batería, FPS o latencia de micrófono. No se genera APK en esta revisión.
