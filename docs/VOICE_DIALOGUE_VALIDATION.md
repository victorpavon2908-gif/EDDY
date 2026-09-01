# Escucha, búsquedas y aprendizaje local

## Comportamiento

- **Hablar** activa una orden sin exigir pronunciar EDDY. **Pausar** detiene el servicio. Después de cada respuesta hay que llamar a EDDY otra vez o tocar Hablar.
- Mientras se instalan los modelos acústicos, puede funcionar el reconocimiento compatible del teléfono. El cambio al núcleo local espera a que termine la interacción actual. No se reinicia periódicamente un micrófono local sano.
- El estado identifica el motor de escucha; la transcripción y la respuesta quedan visibles, incluso si falla la síntesis de voz. Un fallo de transcripción local activa la alternativa compatible.
- «Búscame en internet…», «consultá…», «podés buscar…» y preguntas de actualidad solicitan Google Search en Gemini. Solo se muestran fuentes verificadas por la respuesta de la API; una solicitud sin fuentes se reconoce como no verificada.
- Sin clave de Gemini, una búsqueda explícita abre el navegador y explica que hace falta configurar Gemini para leer una respuesta con fuentes. La investigación automática no abre el navegador. Los errores de clave, cuota, conexión y tiempo de espera son visibles.
- Gemini recibe el diálogo como turnos `user`/`model`, separados de las notas y del tono acústico. La pregunta actual no se duplica. Se conservan las preferencias aunque el historial crezca.
- Gemini 3.7/3.6 Flash usan esfuerzo de razonamiento `low` para conversación por voz, según su API documentada. Otros modelos descubiertos conservan su configuración compatible. Las respuestas personales aprendidas no reemplazan órdenes reales como borrar memoria.
- El análisis acústico existente sigue siendo una estimación del tono, no un diagnóstico emocional. Las palabras explícitas del usuario tienen prioridad.

## Enseñarle sin conexión

Llamá a EDDY antes de cada frase o tocá Hablar:

1. «Recordá que prefiero respuestas cortas». Guarda la nota en el teléfono y la incorpora al contexto de conversación.
2. «Me llamo Manuel», seguido de «cómo me llamo». El dato personal se recupera localmente.
3. «Cuando te pregunte mi bebida, respondé café sin azúcar», seguido de «mi bebida». Aprende una respuesta personal exacta. La respuesta aprendida se pronuncia; no se interpreta como una acción del teléfono.
4. «Qué te enseñé» permite revisar lo aprendido; «borrá tu memoria» elimina también notas y respuestas personales.

Las respuestas personales se recuperan desde memoria; no modifican los pesos de Gemini ni de los modelos acústicos. Además, una red pequeña aprende localmente a clasificar órdenes reconocidas. Véase [aprendizaje local y autonomía](AUTONOMY_AND_LOCAL_LEARNING.md). Tampoco se reutilizan automáticamente respuestas web antiguas como si fueran actuales.

## Alcance sin Internet

Las órdenes del teléfono, cálculos y memoria personal funcionan localmente. El reconocimiento continuo propio necesita los modelos acústicos instalados; el modo compatible depende del proveedor y los idiomas disponibles en Android. La conversación generativa local requiere preparar el modelo opcional en Ajustes. Su presupuesto real es de 1.280 tokens entre entrada y salida; la entrada se mide con el tokenizador y se reduce a 960 como máximo para dejar espacio a la respuesta.

## Verificación en un teléfono

La CI ejecuta pruebas unitarias y Lint sin empaquetar APK. No sustituye las siguientes comprobaciones físicas:

1. Con el asistente activo, tocar Hablar y decir «qué hora es». Verificar transcripción, respuesta y retorno a espera.
2. Decir una orden sin activarlo: no debe responder. Decir «EDDY, prendé la linterna»: debe ejecutar una vez.
3. Activarlo sin hablar, esperar 31 segundos y dar una orden sin llamarlo: debe ignorarla.
4. Repetir con pantalla bloqueada, altavoz, Bluetooth y tras recibir una llamada. Observar el estado del micrófono y posibles restricciones del fabricante.
5. Con modelos instalados, activar modo avión y comprobar hora, cálculo, nombre y respuesta personal aprendida. Sin modelo generativo local no se promete conversación abierta.
6. Con Gemini configurado, pedir noticias actuales y comprobar fuentes. Probar también sin conexión y con una clave inválida; debe explicar el error y volver a permitir una orden.
7. Durante la preparación de modelos, usar Hablar. La instalación no debe cortar la orden en curso.
8. Desactivar temporalmente la voz española del sistema: la respuesta debe permanecer visible y no bloquear la siguiente activación.

Los cambios de código no actualizan una instalación existente hasta que se prepare e instale una versión posterior, cuando el usuario lo solicite. En esta revisión solo se entregan commits.
