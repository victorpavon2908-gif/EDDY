# Aprendizaje continuo de LEO — 2026-10-10

## IMPLEMENTADO

El servicio mantiene un ciclo de interacción, resultado y feedback, sin Groq ni entrenamiento remoto:

1. Conserva hasta 128 pares recientes de pregunta/respuesta entregada, inicialmente `UNASSESSED`. Se excluyen secretos reconocidos por la política existente. Una respuesta generada no se convierte en verdad por haberla producido LEO.
2. «Eso me sirvió» o «esa respuesta es correcta» registra utilidad declarada por el usuario. «Eso está mal», «esa respuesta está mal», «eso es incorrecto» o «no me sirvió» rechaza la última respuesta entregada dentro de quince minutos. Un «sí» aislado, silencio o instrucciones incluidas dentro de una página no son feedback.
3. El rechazo retira la respuesta del almacén documental y guarda una huella del texto (hasta 128 rechazos). Si la investigación devuelve ese mismo texto, LEO informa del rechazo en vez de repetirlo. Esto no detecta todas las paráfrasis del mismo error ni borra el historial conversacional como si nunca hubiera ocurrido.
4. La enseñanza explícita y las correcciones de órdenes siguen utilizando los sistemas existentes. Las respuestas de investigación conservan fuentes y caducidad; dos dominios ya no se interpretan como verificación de una afirmación, tampoco al leer registros antiguos.
5. `LeoAdaptiveTrainer` clona el clasificador 256→16→4, entrena el candidato con la intención reconocida/corrección y compara ocho casos fijos de regresión, dos por clase. Rechaza candidatos que reduzcan aciertos de alguna clase. Solo guarda el candidato aceptado, con el checkpoint y copia de respaldo existentes; no altera la versión del formato.
6. «Qué aprendiste», «qué has aprendido», «cómo va tu aprendizaje» o «mostrame tu aprendizaje» informa conteos del registro reciente y de actualizaciones aceptadas/rechazadas. No son puntuaciones de inteligencia ni verdad.

La opción de aprendizaje adaptativo controla estas escrituras. La memoria documental se guarda desde el servicio bajo el mismo mutex del borrado y con comprobación de generación de turno/aprendizaje; la fachada HTTP no escribe memoria por su cuenta. La escritura del diario solo corresponde al turno que todavía es actual. Se mantienen identificadores, SQLite, modelos de embeddings y motores de voz.

## AUTOENTRENAMIENTO: ALCANCE REAL

Sí se actualizan pesos de un **clasificador de intenciones**, fuera del hilo de audio. No se reentrena DistilUSE ni MicroGPT con cada página, ni se convierte Internet en un corpus fiable automáticamente. El registro de feedback es memoria; por sí solo no es entrenamiento neuronal. Los ocho casos detectan algunas regresiones, no garantizan mejora general y pueden ser insuficientes frente a sobreajuste. No se afirma que cada pregunta mejore obligatoriamente al asistente.

La documentación web puede quedar anticuada o ser incorrecta. Una respuesta nueva tras un rechazo vuelve a estar sin evaluar; la valoración de utilidad del usuario tampoco constituye corroboración independiente. El aprendizaje no autoriza acciones Android adicionales.

## AUTOPROGRAMACIÓN: OBSERVACIONES, NO EJECUCIÓN FINGIDA

`NikoCodeAgent.registerImprovementObservation` registra una capacidad solicitada y el resultado observado, con `implemented=false` y `testsExecuted=false`. Deduplica propuestas pendientes de la misma capacidad. Antes se entregaba la respuesta conversacional como si fuera código candidato; ahora queda explícito que es una observación pendiente.

La composición de herramientas declarativas existentes sigue disponible. **No está implementado** un programador general que genere, compile, pruebe e instale una nueva versión nativa de LEO desde el teléfono. El diario de evolución preexistente no es un compilador, evaluador ni mecanismo de instalación. No se activan automáticamente scripts recuperados ni propuestas de código. Un futuro compilador necesita tests reales, comparación con la versión anterior, firma/instalación Android y recuperación; no se marca READY o ACTIVATED por haber observado un problema.

Borrar memoria elimina diario, contadores, rechazos, conocimiento, entrenamiento y también las observaciones/propuestas de evolución, para no conservar un historial personal oculto. Las preferencias generales continúan fuera de ese borrado.

## VALIDACIÓN

`evidencias/leo-learning-2026-10-10/core.log`: 58 tests JVM. Incluye candidato que actualiza pesos sin mutar el modelo activo, exclusión de secretos y rechazo de reetiquetado perjudicial repetido. El log registra aciertos reales antes/después; no representa un benchmark general.

La suite Android añade persistencia de feedback entre instancias, caducidad, no confirmación por silencio, rechazo documental, no readmisión del mismo texto, diversidad de fuentes sin falsa verificación, límite de historial, borrado y propuestas que permanecen PROPOSED. El workflow del SHA publicado determina su resultado integrado.

**PENDIENTE DE VALIDACIÓN FÍSICA:** sesión larga en HONOR, respuesta verbal a feedback, interrupción durante aprendizaje, consumo de CPU/RAM/batería y utilidad longitudinal. No hay mediciones nuevas de teléfono ni evidencia de inteligencia ilimitada.
