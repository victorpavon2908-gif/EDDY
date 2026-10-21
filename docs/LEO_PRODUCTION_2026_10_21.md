# LEO: puntos 2, 3, 5 y 7

## IMPLEMENTADO

- Descargas: WorkManager 2.10.1 mantiene un trabajo único persistente, autorizado desde Ajustes, con ID de catálogo, red y espacio como restricciones. Notificación dataSync con cancelación; progreso observable al reabrir Ajustes. La descarga parcial, SHA256 completo y prueba JNI previa a activar reutilizan el instalador existente. Reintenta errores de transporte hasta tres veces con espera exponencial. Cada intento dura como máximo 45 minutos. No se inicia descarga por defecto.
- Android puede aplazar trabajos por cuotas, ahorro o restricciones. Forzar detención impide reanudación hasta volver a abrir la app; no se promete ejecución ininterrumpida. Una falta de RAM o validación fallida exige reintentar, conservando el archivo. Cancelar conserva el parcial sin autorizar reanudación automática.
- Respuestas: contexto nativo de 4096 tokens, hasta 6000 caracteres de contexto local y preguntas de 4000 caracteres. Salida de 512 tokens habitual o 1024 para detalle/código/continuación/investigación. Tiempo máximo nativo de 180 segundos, cancelable; no es una latencia medida ni una promesa. El límite de entrada se sigue comprobando con el tokenizador real; entradas que no caben vuelven al fallback. No se habla una respuesta truncada como si estuviera completa.
- Investigación: cada solicitud de transporte tiene un máximo de seis segundos dentro del presupuesto global existente. Las páginas leídas tienen preferencia frente a snippets. Una respuesta solo basada en snippets no se marca webUsed ni se aprende como investigación verificada. Si hay modelo local activo y no se autoriza síntesis remota, se permite síntesis local de hasta tres extractos acotados; debe devolver JSON con citas textuales presentes en las páginas recuperadas. JSON/citas inválidos conservan la respuesta extractiva. Las fuentes se consideran datos no confiables. Un solo dominio se indica explícitamente, sin declararlo corroboración independiente.
- Documentación: se corrigen las afirmaciones obsoletas de runtime Qwen desconectado, catálogo único y presupuesto combinado de 5 GB. Los límites personales de memoria no cambian.

## PRUEBAS AUTOMATIZADAS

Las nuevas pruebas ejercitan restricciones/datos del trabajo, rechazo de ID desconocido sin descargar, límites del prompt, presupuestos de respuesta, inyección de delimitadores, fuentes inválidas y snippets no aprendibles. La CI real prueba ambos modelos, cuatro preguntas, referencia a un proyecto dado en contexto y cancelación. Consultar resultados del SHA publicado; esta página no convierte pruebas pendientes en aprobadas. Las pruebas de WorkManager no equivalen a reiniciar un teléfono.

## PENDIENTE DE VALIDACIÓN FÍSICA

Descarga de 4,28 GB con pantalla apagada, cierre de Ajustes, reinicio, interrupción de red, cancelación desde notificación y cuotas Android 16. RAM/temperatura/latencia con contexto 4096 y voz activa. No hay resultados físicos inventados.

## LÍMITES RESTANTES

No se ha demostrado disponibilidad sostenida de los buscadores ni resistencia general a prompt injection; comprobar pertenencia literal de una cita no demuestra que respalde lógicamente una afirmación. No hay contexto infinito, streaming de tokens de Qwen ni garantía de que todos los formatos JSON generados sean válidos. Firma de producción y validación física quedan fuera de esta iteración por decisión del usuario.
