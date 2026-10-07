# LEO: núcleo cognitivo y estado de trabajo

IMPLEMENTADO: `LeoAgentRuntime` es instanciado por NikoAssistantService, no es una demo separada. Coordina TurnController, ConversationManager y confirmación de borrado de memoria. LeoSkillRegistry y LeoTaskExecutor son utilizados por las rutas de acciones.

ConversationState incluye tema, entidades explícitas de consulta, intención, id de tarea/turno, texto del usuario, pregunta pendiente, resultado reciente, fuentes, última herramienta, detalle, tono y estado de interacción. Tamaños acotados. Tono natural fijo; extracción de entidades conservadora, no una ontología.

La continuidad de investigación conserva solamente la consulta pública ya utilizada, con caducidad de 15 minutos. “¿Y Microsoft qué dijo?” puede heredar “OpenAI hoy”. Una petición independiente no hereda ese tema. La memoria personal completa nunca se concatena a consultas de búsqueda. La expansión es heurística; ambigüedad compleja todavía puede requerir aclaración.

Las solicitudes de simplificación usan el resultado anterior en el contexto y evitan el MicroGPT de respuesta genérica. No se convierten referencias como “sí” en autorización general de herramientas. La confirmación de memoria es exacta, de un solo uso, caduca a los 60 segundos y se revoca al cambiar de tema o interrumpir.

PLANIFICADO: planificador abierto de objetivos, resolución semántica robusta entre múltiples proyectos, consolidación de contexto tras reiniciar el proceso. El historial SQLite existente es durable; ConversationState de esta iteración vive durante la sesión del servicio. No se afirma que un viaje pueda organizarse autónomamente de extremo a extremo.


## Evidencia y límites

Estado de esta iteración: IMPLEMENTADO donde se identifica una llamada real; AUTOMATIZADAMENTE PROBADO solo para los casos ejecutados en [LEO_VALIDATION.md](LEO_VALIDATION.md). Toda propiedad del teléfono permanece **PENDIENTE DE VALIDACIÓN FÍSICA**. Los contratos o tipos sin ruta productiva se indican como PLANIFICADO.


Actualización: LeoVisionContext conecta peticiones explícitas de pantalla con observaciones reales de Accessibility, con caducidad. Sin entrada accesible no afirma visión. Offline describe etiquetas; Groq puede interpretar el texto observado, sin herramientas web. Cámara, imágenes y OCR de documentos siguen pendientes. Memoria y observaciones cloud se adjuntan como datos de usuario no confiables, nunca dentro del rol system.
