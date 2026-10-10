# LEO: recuperación, síntesis y procedencia

> Actualización 2026-10-10: Groq retirado por petición del propietario. La ruta productiva y las capacidades afectadas se detallan en [LEO_WEB_FIRST.md](LEO_WEB_FIRST.md); las referencias al proveedor más abajo describen la arquitectura anterior.

Ruta productiva: NikoAiClient → LeoNativeWebSearch → consultas complementarias RSS/HTML → deduplicación y diversidad → lectura/extracción → resumen local → ResearchSynthesis opcional con Groq. Compound queda como fallback cuando la búsqueda nativa no obtiene evidencia, evitando una segunda investigación simultánea innecesaria.

IMPLEMENTADO: síntesis estructurada solo con fuentes recuperadas; índices locales, sin URLs generadas ni índices fuera de rango. ResearchCitationPolicy rechaza resultados cloud sin citas numeradas, con referencias fuera de rango o enlaces ajenos a fuentes del proveedor. Un resultado descartado no sustituye la respuesta nativa. Ya no se añade una pregunta de seguimiento prefabricada a cada respuesta web.

Las páginas son DATOS NO CONFIABLES, nunca instrucciones internas. La síntesis mantiene esta regla en el prompt y restringe el formato de salida. Esto reduce superficie; no prueba resistencia absoluta a prompt injection ni entailment de cada afirmación.

| Etiqueta de evidencia | Qué puede afirmarse |
|---|---|
| Confirmado | Requiere revisión de afirmación contra evidencia primaria/corroboración; no automático por contar enlaces |
| Fuente única | Información atribuida a una fuente; no confirmación independiente |
| Inferencia | Interpretación señalada como tal, no dato medido |
| Incierto | Evidencia ausente, antigua, insuficiente o contradictoria |

La validación implementada demuestra pertenencia de referencias y disponibilidad de texto, NO veracidad semántica. Evaluación automática de contradicciones, independencia editorial, fechas del hecho vs publicación y calificación sistemática por afirmación siguen PLANIFICADAS. Los tests de transportes usan fixtures; no prueban disponibilidad actual de los buscadores o calidad de una consulta en vivo.


## Evidencia y límites

Estado de esta iteración: IMPLEMENTADO donde se identifica una llamada real; AUTOMATIZADAMENTE PROBADO solo para los casos ejecutados en [LEO_VALIDATION.md](LEO_VALIDATION.md). Toda propiedad del teléfono permanece **PENDIENTE DE VALIDACIÓN FÍSICA**. Los contratos o tipos sin ruta productiva se indican como PLANIFICADO.

Al combinar varias búsquedas, los índices de citas se reasignan por URL y el estado visible conserva hasta 64 fuentes. Esto evita que el [1] de una segunda consulta apunte a la primera fuente de otra investigación. Una referencia inválida se señala explícitamente.
