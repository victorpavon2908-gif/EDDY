# Aprendizaje local y autonomía de EDDY

## Qué cambia

EDDY prioriza por defecto la conversación local cuando el modelo está preparado. Conserva el reconocimiento por activación: hay que llamarlo o tocar Hablar para iniciar cada orden. Las nuevas funciones no conceden permisos, instalan la aplicación ni activan un micrófono desde GitHub.

En Ajustes se puede elegir una personalidad cercana, directa o irónica; preparar el modelo de conversación y la voz española; y activar o desactivar la prioridad local, la verificación automática y el aprendizaje. La personalidad irónica cuestiona ideas con humor breve y propone una solución, sin humillar y con tono serio ante malestar o peligro. Es una instrucción para el generador, no consciencia ni libre albedrío.

## Una red nueva que sí aprende

`OnlineIntentNetwork` empieza con pesos aleatorios y entrena en el teléfono mediante gradiente descendente. Es una red de 256 entradas, 16 unidades ocultas y cuatro salidas: búsqueda, acción, memoria y conversación. Usa características de palabras, pares de palabras y fragmentos de caracteres.

Las etiquetas proceden de órdenes reconocidas por reglas y enseñanzas explícitas. No aprende como verdad sus propias predicciones ni todas las respuestas generadas. Cada orden etiquetada actualiza los pesos y repasa ejemplos anteriores, con hasta 16 ejemplos por categoría. Esto reduce el olvido, pero no garantiza retención perfecta. Las frases sin etiqueta se conservan en el historial, sin actualizar ciegamente la red.

Una predicción solo puede recomendar la ruta de búsqueda y debe superar mínimos de ejemplos, confianza, margen y similitud. Nunca ejecuta acciones del teléfono. Las órdenes reales y las respuestas personales explícitas conservan prioridad.

Los pesos y ejemplos se guardan en almacenamiento privado con versión, comprobación CRC y una copia anterior recuperable. Un archivo dañado no reinicia silenciosamente lo aprendido. Desactivar aprendizaje detiene entrenamiento y uso de predicciones; «borrá tu memoria» elimina también los pesos y su copia. No hay entrenamiento de un modelo de lenguaje desde cero ni ajuste de los pesos de Qwen, Gemini o los modelos de voz.

## Memoria duradera y límites de contexto

SQLite conserva los turnos, notas y respuestas personales. Migrar guarda una copia literal de las preferencias anteriores e importa los registros legibles una sola vez. Un fragmento antiguo dañado no bloquea el resto de la memoria. El borrado explícito elimina también esa copia y evita reimportarla.

El archivo no elimina turnos por superar 140 ni enseñanzas por superar 40. Las correcciones de una misma pregunta reemplazan su respuesta activa; los turnos originales permanecen archivados. La conversación usa una ventana reciente y las notas más recientes: conservar el archivo no equivale a que el modelo recuerde automáticamente cada detalle antiguo. Las respuestas enseñadas mediante «cuando te pregunte…, respondé…» sí se consultan por clave exacta en todo el archivo.

La base crece con el uso y necesita espacio libre; borrar datos de Android o desinstalar elimina la memoria local. No hay sincronización de esta memoria a una cuenta externa.

## Conversación local y búsqueda

El modelo de lenguaje sigue siendo Qwen preentrenado. El [modelo exportado utilizado](https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct) tiene capacidad de 1.280 tokens, incluyendo entrada y salida. El código anterior pedía 3.072. Ahora se mide cada entrada con el tokenizador del motor, se reduce el contexto y se reservan al menos 320 tokens para responder. Los errores de preparación, inicio e inferencia se explican cuando tampoco hay una respuesta alternativa.

Con verificación automática activada, las preguntas de actualidad y las búsquedas aprendidas fiables solicitan fuentes directamente. Una respuesta local que expresa incertidumbre puede derivarse a Gemini si la pregunta permite investigación. Las preguntas personales y las negativas explícitas a buscar se excluyen por reglas. Decir «sin Internet» fuerza la ruta local y evita la llamada a Gemini. Estas reglas no detectan todas las alucinaciones de un modelo.

La investigación usa [Google Search de Gemini](https://ai.google.dev/gemini-api/docs/generate-content/google-search), necesita conexión y clave configurada. Las instrucciones piden contrastar fuentes y reconocer discrepancias. La interfaz muestra las fuentes devueltas; varios enlaces no prueban por sí solos independencia. Si no se puede confirmar diversidad de dominios, se indica. Una búsqueda exigida sin fuentes no se presenta como verificada. Las páginas se tratan como datos, no como instrucciones.

No se inicia un rastreador permanente ni se sortean permisos. Cada turno activado hace como máximo una operación del coordinador hacia la nube, sujeta al límite y los reintentos del cliente Gemini. Esa operación puede usar la herramienta de búsqueda. Si falla, se muestra el motivo o una respuesta local disponible; la búsqueda automática nunca abre otra aplicación para simular éxito.

## Voz y emoción

El análisis acústico existente sigue aportando una estimación de tono, no una lectura fiable de emociones o subtexto. No se añadió escucha multicanal permanente. La voz ajusta moderadamente velocidad y, en el sintetizador Android, altura según peticiones explícitas y expresiones de malestar. La voz neuronal local permite velocidad; no se le atribuye control de altura inexistente. Las respuestas largas se cortan por frases o palabras para evitar cortes arbitrarios. Las negaciones simples como «no estoy triste» no activan el ajuste de tristeza.

## Validación

La suite cubre aprendizaje y repaso de categorías, recuperación de pesos, archivo SQLite y migración, retención más allá de las ventanas antiguas, borrado, elección local/nube, respeto de «sin Internet», presupuesto del prompt y fragmentación de voz. GitHub ejecuta pruebas Android y Lint sin empaquetar APK.

Quedan para una instalación posterior las pruebas físicas de micrófono, altavoz, Bluetooth, consumo, calidad y latencia de Qwen y voz, y búsquedas con la clave del usuario. Los commits no actualizan el teléfono por sí solos.
