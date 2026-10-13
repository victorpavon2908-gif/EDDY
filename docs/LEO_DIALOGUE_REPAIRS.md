# Conversación y preferencias — 2026-10-13

IMPLEMENTADO: la ruta del servicio consulta `LeoDialoguePolicy` antes de acciones y búsquedas para aclarar pedidos incompletos, errores sin adjuntar y referencias a acciones ambiguas. Una aclaración no autoriza ejecutar una acción. Las solicitudes claras conservan el enrutamiento existente. Las preguntas de identidad/capacidades se resuelven localmente y mencionan límites y permisos.

El fallback ya no lee mensajes internos sobre la disponibilidad del modelo: reconoce que no interpretó el pedido y pide una reformulación. Esto no sustituye los avisos específicos de fallos reales de investigación o permisos.

La memoria conserva una preferencia explícita de respuesta corta/detallada, reemplazable y borrable junto con los recuerdos existentes. La preferencia breve selecciona hasta tres oraciones para narrar investigaciones largas e indica que el resto está en pantalla. No recorta el texto almacenado ni sus fuentes ni confirmaciones de acciones. Pedir detalle conserva el texto completo; no genera información adicional artificialmente.

Las interacciones siguen pasando por la memoria y el aprendizaje supervisado existente. No se etiquetan todas las frases como hechos verdaderos, ni se reentrena MicroGPT con cada pregunta. El clasificador sigue usando sus controles de regresión. Esta entrega no añade un modelo general de razonamiento: la comprensión abierta, la síntesis profunda y las referencias complejas siguen limitadas por los motores locales y la investigación disponible. Añadir instrucciones a un prompt que nadie ejecuta no resolvería esa limitación.

PRUEBAS: `LeoDialoguePolicyTest` verifica cinco grupos: aclaración, referencias ambiguas, preferencias explícitas, narración breve y capacidades. Se integra en `scripts/test_ai_core.sh` y las pruebas unitarias Android. Consultar la ejecución del commit para resultados.

Paquete offline de la entrega anterior: workflow 38447884164 aprobado y publicado; 36 850 documentos/fragmentos, 496 570 368 bytes instalados y 205 970 926 bytes comprimidos. Son resultados del constructor SQLite, no métricas de inteligencia ni entrenamiento neuronal. El botón está en Ajustes → Conocimiento offline → Descargar / actualizar cerebro.

PENDIENTE DE VALIDACIÓN FÍSICA: charla continua, preferencias tras reiniciar, borrado de memoria, narración completa/breve, boca al hablar y barge-in en HONOR. No se modificó el reconocedor ni se midió nueva latencia.
