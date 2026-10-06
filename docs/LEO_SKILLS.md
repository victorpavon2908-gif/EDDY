# LEO: contrato de capacidades ejecutables

IMPLEMENTADO: LeoSkill declara descriptor (nombre, descripción, intents, parámetros, riesgo, permisos, cancelabilidad), disponibilidad, aceptación de AssistantCommand y execute. LeoSkillResult conserva mensaje, éxito informado por ejecutor y verificación separada (por defecto false).

LeoSkillRegistry usa registro local explícito, rechaza ids duplicados y selección ambigua; no carga código del modelo. Las acciones sensibles o irreversibles no ejecutan a través del registro sin un adaptador específico de confirmación. Borrado de memoria tiene su protocolo separado y acotado.

Adaptadores activos desde NikoAssistantService: memory, generated_tool, ui_automation, phone. Las declaraciones de phone son conservadoras USER_VISIBLE. Disponibilidad significa implementación instalada; permisos y existencia de aplicaciones todavía se comprueban en ActionExecutor, no en discover. Los parámetros son comandos Kotlin tipados, no JSON ejecutable arbitrario.

La búsqueda, conversación y cálculo inmediato conservan sus rutas existentes; SearchSkill/ResearchSkill/CalculatorSkill individuales, permisos dinámicos en discovery y esquemas por parámetro detallados quedan PLANIFICADOS. NikoSkillEngine sigue conservando su registro declarativo legacy para programación/herramientas; no se borra ni se confunde con el nuevo registro ejecutable.

Polimorfismo conservado: GeneratedToolSpec valida componentes/acciones permitidos; motores existentes renderizan juegos/herramientas. Crear una pantalla no demuestra GPS con navegación, backend de producción, física de un juego comercial ni cualquier app arbitraria. Debe declararse la capacidad real del componente seleccionado.


## Evidencia y límites

Estado de esta iteración: IMPLEMENTADO donde se identifica una llamada real; AUTOMATIZADAMENTE PROBADO solo para los casos ejecutados en [LEO_VALIDATION.md](LEO_VALIDATION.md). Toda propiedad del teléfono permanece **PENDIENTE DE VALIDACIÓN FÍSICA**. Los contratos o tipos sin ruta productiva se indican como PLANIFICADO.
