# LEO: ejecución de planes y evidencia por paso

IMPLEMENTADO: LeoTaskExecutor envuelve la ruta de múltiples comandos ya validados por los planificadores existentes. Guarda hasta 20 planes de máximo 8 pasos en la sesión. Cada paso mantiene índice, estado y texto devuelto. La cancelación se propaga y no ejecuta pasos restantes. Los fallos por excepción detienen el plan.

Estados declarados: PLANNING, RUNNING, WAITING_USER, WAITING_TOOL, VERIFYING, COMPLETED, FAILED, CANCELLED. La ruta integrada usa PLANNING → WAITING_TOOL → VERIFYING y FAILED/CANCELLED cuando corresponde. No se marca COMPLETED solo porque una API acepta la solicitud. Un error comunicado como texto por un ejecutor existente requiere todavía evaluación específica del resultado.

Una cancelación después de despachar una acción no la deshace. No se repiten automáticamente pagos, mensajes ni otros efectos externos. El executor no genera instrucciones arbitrarias, código ni coordenadas.

PLANIFICADO: journal durable de tareas, pausa/reanudación segura tras reinicio, verificador por tipo de herramienta, planificación jerárquica de objetivos abiertos y decisiones WAITING_USER. Los estados futuros están declarados pero no se presentan como capacidades terminadas. “Continuá con el viaje” puede usar historial conversacional; no recupera aún un plan durable verificable.


## Evidencia y límites

Estado de esta iteración: IMPLEMENTADO donde se identifica una llamada real; AUTOMATIZADAMENTE PROBADO solo para los casos ejecutados en [LEO_VALIDATION.md](LEO_VALIDATION.md). Toda propiedad del teléfono permanece **PENDIENTE DE VALIDACIÓN FÍSICA**. Los contratos o tipos sin ruta productiva se indican como PLANIFICADO.
