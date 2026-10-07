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

La recuperación utiliza `LeoSemanticMemory → LeoSemanticEncoder` cuando el usuario prepara el modelo desde Ajustes. DistilUSE multilingüe cased v2, revisión fija `bfe45d0732ca50787611c0fe107ba278c7f3f889`, ejecuta WordPiece cased, Transformer INT8, mean pooling, Dense entrenado con tanh y normalización L2 (512 dimensiones). La implementación preserva acentos y mayúsculas. El ranking mezcla similitud entrenada (peso 0.60), coincidencias, recencia, confianza y frecuencia.

La descarga explícita de 137.9 MB verifica tamaño y SHA-256 de los tres archivos. Se conserva fuera del backup; no descarga código ejecutable ni pickle. Los recuerdos nunca se envían al proveedor del modelo. La instalación usa staging, cancelación cooperativa y activación por rename. No cambia esquema ni formatos persistidos. Sin modelo instalado, ante error de carga o en una llamada desde Main, se conserva el ranking anterior. Un lote completo usa el mismo espacio vectorial: nunca se comparan vectores entrenados con heurísticos.

`EddyTransformerEmbedder` se conserva únicamente como respaldo heurístico, no entrenado. El encoder entrenado mantiene un caché acotado de 512 entradas en RAM y libera la sesión nativa tras dos minutos de inactividad. El primer recorrido de hasta 180 candidatos puede ser costoso en un teléfono; no existe todavía un índice vectorial persistente ni un benchmark físico. No reentrenamos DistilUSE con las conversaciones del usuario.

Los pesos y la licencia Apache-2.0 provienen de [Sentence Transformers](https://huggingface.co/sentence-transformers/distiluse-base-multilingual-cased-v2). La arquitectura completa incluye Dense: no basta con promediar la salida ONNX. [Guía técnica y evidencia](LEO_SEMANTIC_DUPLEX.md).

Borrado: requiere confirmación específica; elimina también el estado conversacional y el historial nuevo de iniciativa. Cifrado de memoria, controles granulares de retención/exportación y consolidación semántica general: PLANIFICADO. Las credenciales y datos delicados requieren auditoría adicional de todas las capas, no solo del filtro de recuerdos.


## Evidencia y límites

Estado de esta iteración: IMPLEMENTADO donde se identifica una llamada real; AUTOMATIZADAMENTE PROBADO solo para los casos ejecutados en [LEO_VALIDATION.md](LEO_VALIDATION.md). Toda propiedad del teléfono permanece **PENDIENTE DE VALIDACIÓN FÍSICA**. Los contratos o tipos sin ruta productiva se indican como PLANIFICADO.
