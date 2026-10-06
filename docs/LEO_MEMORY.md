# LEO: memoria, revisiones y compatibilidad

IMPLEMENTADO: SQLite NikoMemoryArchive conserva nombre y versión 2. No se renombra ni recrea la base. Se conservan SharedPreferences, importación legacy y registros existentes. La tabla semantic_memory ya admite kinds textuales; PREFERENCE usa esa compatibilidad.

| Tipo | Ruta activa |
|---|---|
| CORE | Datos explícitos como nombre, ciudad, trabajo y estudios |
| EPISODIC | Frases importantes y hechos del usuario; expiración existente |
| PREFERENCE | Gustos, preferencias y consumo declarado; revisión explícita |
| PROCEDURAL | Comandos cuyo ejecutor devuelve éxito; no demuestra efecto físico |
| SEMANTIC | NikoKnowledgeStore guarda conocimiento de investigación; no unificado todavía con Kind.SEMANTIC |
| PROJECT | Tipo preparado, extracción/consolidación estructurada PLANIFICADA |
| RELATIONSHIP | Tipo preparado; extracción PLANIFICADA, sin inferir relaciones |

MemoryRevision solo trata declaraciones de primera persona soportadas. “Me gusta el café” → “No me gusta café” o “Ya no tomo café” retira la afirmación anterior del contexto factual. “Vivo en Managua” → “Vivo en León” retira el episodio anterior y actualiza el hecho estable. No deduce que dejar de tomar algo implique odiarlo. No es un detector general de contradicciones.

Los recuerdos superados se marcan expirados, y la poda normal puede retirarlos después. Las notas se conservan en la tabla pero se excluyen mediante marcadores aditivos en metadata. El historial conversacional conserva la secuencia original, incluyendo correcciones. La extracción escanea como máximo 500 recuerdos activos; corpus mucho mayores necesitan índice por clave/tema y consolidación adicional.

La recuperación combina rasgos vectoriales determinísticos, tokens, trigramas, recencia, confianza y frecuencia. **EddyTransformerEmbedder NO contiene pesos entrenados para semántica abierta.** No se presenta como sustituto de un encoder lingüístico entrenado. Se mantienen sus resultados numéricos al precalcular proyecciones; se evita que un consumidor modifique el caché compartido.

Borrado: requiere confirmación específica; elimina también el estado conversacional y el historial nuevo de iniciativa. Cifrado de memoria, controles granulares de retención/exportación y encoder semántico entrenado: PLANIFICADO. Las credenciales y datos delicados requieren auditoría adicional de todas las capas, no solo del filtro de recuerdos.


## Evidencia y límites

Estado de esta iteración: IMPLEMENTADO donde se identifica una llamada real; AUTOMATIZADAMENTE PROBADO solo para los casos ejecutados en [LEO_VALIDATION.md](LEO_VALIDATION.md). Toda propiedad del teléfono permanece **PENDIENTE DE VALIDACIÓN FÍSICA**. Los contratos o tipos sin ruta productiva se indican como PLANIFICADO.
