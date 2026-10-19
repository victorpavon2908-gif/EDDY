# Emoción y aprendizaje personal — 2026-10-19

## Implementado

El modelo local existente sigue siendo un Naive Bayes pequeño ajustado a vocabulario
propio de veinte categorías. Se refuerza la atribución: solo una emoción declarada
al comienzo de la primera cláusula del usuario aporta características. Menciones
sobre amigos, películas o trabajo no se atribuyen al hablante. Preguntas, citas,
negaciones y combinaciones ambiguas conservan abstención. Una declaración emocional
seguida de una tarea puede modular el tono sin sustituir esa tarea.

La señal sigue conectada al trato cuidadoso del avatar y a `SpeechProsody`. Las
órdenes explícitas de actuación conservan sus ocho expresiones. No se rediseñan ojos,
boca ni escucha. No se persiste la predicción emocional como hecho del usuario.

`LeoPersonalMemory` amplía las cuatro categorías existentes a diez: comida, bebida,
música, lugar, pasatiempo, deporte, libro, película, juego y lenguaje de programación.
Acepta declaraciones explícitas, algunas variantes de «preferido» y «recordá que»;
«ahora mi ... es ...» reemplaza el valor actual. Las preferencias se consultan y
olvidan por voz, y se incluyen como datos actuales en el contexto local. No se envía
esta base de preferencias al proveedor remoto. Se reutiliza `personal_preferences`
con claves nuevas, sin cambiar esquema, versión ni identificadores.

Ejemplos:
- «Recordá que mi pasatiempo favorito es dibujar».
- «Ahora mi pasatiempo favorito es cocinar».
- «¿Cuál es mi pasatiempo favorito?».
- «Olvidá mi pasatiempo favorito».
- «Mi lenguaje de programación preferido es Kotlin».
- «No hagas bromas sobre mí» / «Permito bromas sobre mí».
- «¿Cuál es mi preferencia de humor?» / «Olvida mi preferencia de humor».

El límite de humor se guarda localmente, se vuelve a leer antes de la respuesta
personal y bloquea las bromas y el sarcasmo explícitos del diálogo local. El avatar
no representa burla cuando se rechaza esa actuación. No es un filtro universal de
humor sobre las respuestas libres de cualquier modelo externo.

## Evidencia reproducible

`bash scripts/test_ai_core.sh` incluye pruebas de las veinte etiquetas explícitas,
atribución, negación, preguntas, tarea posterior, actualización de preferencias,
valores anteriores, límites de humor y eliminación. Son casos de regresión escritos
para el proyecto, no un benchmark independiente de precisión emocional.

`NikoMemoryArchiveTest.expandedPreferencesAndHumorBoundarySurviveDatabaseReopen`
verifica con SQLite/Robolectric persistencia y reemplazo tras reabrir la base.
`LeoFaceMoodTest.refusedTeasingDoesNotAnimateMockery` verifica la selección de estado.
La animación y prosodia reales siguen PENDIENTES DE VALIDACIÓN FÍSICA.

## Modelo preentrenado: revisado, no instalado

Se revisó la ficha primaria de
https://huggingface.co/Horizon-Labs/multilingual-emotions-small : declara Apache-2.0,
español, 28 etiquetas y ONNX cuantizado de 268 MB. Es un candidato para evaluación;
no se incorporó, descargó ni ejecutó en LEO. Requiere tokenizer compatible, límites
de RAM/CPU, evaluación de negaciones/atribución y revisión completa de artefactos y
licencias antes de distribución. Sus métricas publicadas no son métricas de LEO.

## Pendiente

Inferencia preentrenada contextual local, sarcasmo implícito, emociones mixtas y
validación con un conjunto independiente de español nicaragüense. La memoria ampliada
no es extracción universal de cualquier gusto ni aprendizaje de pesos con cada turno;
relaciones, proyectos y eventos necesitan contratos propios. El historial original
conserva su política existente: olvidar una preferencia no borra toda la conversación.
