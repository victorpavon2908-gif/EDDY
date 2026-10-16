# LEO Neural Core — integración incremental (2026-10-16)

## Auditoría de las rutas actuales

La aplicación Kotlin usa `NikoAssistantService` para voz y ejecución. `LeoAgentRuntime`
posee `TurnController`, conversación y confirmaciones. El reconocedor principal y el
local tienen rutas separadas; no se sustituyen. El avatar Canvas conserva ojos y boca.
`ConversationCoordinator` decide conversación o investigación, no es un LLM.

El antiguo MicroGPT es un decoder de 104.797 parámetros con contexto de 40 tokens;
no puede convertirse en razonamiento general cambiándole el nombre. Su checkpoint,
SHA, nombres de recursos, entrenador y formatos se conservan por compatibilidad.
El nombre público es LEO Neural Core; el decoder pasa a ser uno de sus recursos.
La enciclopedia descargable es SQLite, no pesos neuronales. Los embeddings y el
clasificador adaptativo existentes son sistemas distintos y se mantienen.

Memoria: `NikoMemory` combina preferencias antiguas, archivo SQLite, memoria semántica
y embeddings. La auditoría encontró que «mi comida favorita» no tenía un campo propio.
Programación: `NikoCodeAgent` registraba propuestas, sin generar ni compilar proyectos.
Actualizaciones: se verificaban tamaño y SHA, pero no una consulta real del candidato.
Investigación: la búsqueda nativa recupera/extracta fuentes; no equivale a síntesis
razonada de un modelo grande. Proactividad, skills, automatización y herramientas
polimórficas siguen sus rutas existentes, con sus mismas limitaciones.

## IMPLEMENTADO

- `LeoNeuralCore`: ejecutado desde la conversación real, rutas prioritarias de memoria,
  expresiones y proyectos, más razonamiento/retrieval; comparte el controlador de turno.
  Verifica generación antes y después; propaga cancelación. No lanza jobs huérfanos.
- `LeoPersonalMemory`: preferencias explícitas de comida/bebida/música/lugar, consulta,
  edición por declaración, olvido individual y «mostrá mis preferencias». Corrección
  pizza → lasaña solo si pizza coincide con la preferencia actual. SQLite versión 3
  agrega tabla sin modificar identificadores ni borrar tablas anteriores. Borrar toda
  la memoria incluye la tabla nueva. Las declaraciones estructuradas no se duplican
  como hechos activos en memoria semántica. El historial conserva lo realmente dicho.
- `LeoEmotionEngine`: actuación y límites de humor existentes. `LeoEmotionClassifier`
  es un piloto Naive Bayes local de 20 clases, ajustado a un vocabulario propio reducido.
  **No es un modelo emocional preentrenado ni comprensión contextual general**. Se
  abstiene ante negación/preguntas/tercera persona/ambigüedad. La señal efímera modula
  suavemente voz/avatar; no se guarda como hecho del usuario. No diagnostica emociones.
- `LeoReasoningEngine`: cálculo determinista comprobable y coordinación de conocimiento
  local, búsqueda y fallback existentes. No se atribuye razonamiento general al decoder.
- `LeoCodeIntelligence`: genera dos archivos funcionales de una calculadora Python y sus
  pruebas. Se muestra el código y se usa una narración breve separada. No ejecuta código
  en Android ni modifica repositorios. Otros proyectos siguen las rutas previas.
- `LeoEvolutionEngine`: valida SQLite y búsqueda FTS del candidato antes del cambio de
  directorio, después de SHA/tamaño. El botón «ACTUALIZAR CEREBRO DE LEO» consulta cambios;
  informa las revisiones por hash. Un fallo de validación deja la instalación previa.

## Seguridad y límites

Memorias nuevas locales; no se añadieron llamadas remotas ni claves/proveedores pagos.
SHA prueba integridad contra el manifiesto servido por HTTPS, **no una firma editorial**.
El rollback por fallo de activación existente se conserva. No hay todavía historial
seleccionable de versiones, firma de modelos, ni evaluación general de nuevos LLM.
El historial anterior no es un almacén de secretos: no se afirma que sus entradas estén
redactadas. El reconocimiento de preferencias es deliberadamente estrecho, no extracción
universal. Los valores nuevos se normalizan; exportación completa y edición visual pendientes.

## AUTOMATIZADAMENTE PROBADO

`bash scripts/test_ai_core.sh`: 87 pruebas JVM pasaron localmente, incluyendo 11 nuevas
pruebas del núcleo/memoria. Una de ellas escribe el proyecto generado y ejecuta
`python3 -m unittest -v`: 3 pruebas Python aprobadas. Esta ejecución ocurre en el entorno
de desarrollo, nunca se representa como una ejecución en el teléfono.

Pruebas Android agregadas: persistencia tras reapertura, migración v2→v3 conservando
notas, candidato SQLite/FTS válido y rechazo de candidato vacío. Su resultado se determina
por el workflow Android; no se presume aprobado por existir los archivos.

La tasa local es 87/87 = 100% de esta batería, **no 100/100 de inteligencia**. No se asignan
notas generales a los cinco cerebros: faltan benchmarks representativos y modelos de
mayor capacidad. Tampoco se mide precisión emocional con el vocabulario de entrenamiento.

## PENDIENTE DE VALIDACIÓN FÍSICA

Interrupción durante TTS, recuperación del micrófono, sincronización y aspecto del avatar,
Bluetooth, pantalla bloqueada, llamadas/música, latencia, RAM, batería y temperatura.
Usar `scripts/collect_leo_device_evidence.py`; no hay mediciones nuevas de dispositivo.

## PLANIFICADO / NO COMPLETO

Modelo emocional contextual con licencia y benchmark externo; generación amplia de
código con proveedor opcional y consentimiento; inferencia general local/remota por
capacidad; síntesis profunda con verificación; memoria de todos los dominios solicitados
con exportación/edición; gestor firmado de modelos con evaluación y rollback seleccionable.
No hay entrenamiento continuo del decoder ni automodificación autónoma del APK.
Esta entrega es una migración funcional inicial, no el cumplimiento total de los cinco
subsistemas avanzados solicitados. Las mejoras de voz previas se conservan.

## Continuación: proveedor de inferencia opcional

Implementado un cliente configurable con consentimiento, Keystore, pruebas de contrato,
selección desde el núcleo y cancelación. Ver [inferencias y límites](LEO_OPTIONAL_INFERENCE.md).
La generación amplia requiere configurar un servidor real; no se afirma que haya un
modelo general nuevo dentro del APK ni se certifica su calidad con fixtures HTTP.
