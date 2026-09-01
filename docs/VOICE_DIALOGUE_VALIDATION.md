# Escucha, búsquedas y aprendizaje local

## Comportamiento

- EDDY inicia cada petición únicamente después de detectar acústicamente su nombre. Se puede decir «EDDY, qué hora es» o «EDDY», esperar «Ajá» y dar la orden. Después de la respuesta vuelve a esperar su nombre.
- La pantalla principal conserva Configuración; se retiraron los botones Hablar/Pausar y la acción interna de activación manual. La escucha se habilita o deshabilita en Ajustes y también se puede detener desde la notificación. Esa elección persiste al reabrir la app.
- Solo el núcleo acústico local mantiene el micrófono. Se eliminó el bucle de `SpeechRecognizer` que mostraba «El reconocimiento compatible no respondió» y podía competir por el dispositivo de audio. Abrir la app o cambiar la pantalla no inicia una orden ni reinicia un motor sano.
- Durante la primera preparación se muestra «Preparando escucha»; hasta que AudioRecord y los modelos estén listos no se anuncia «Decí EDDY». Un fallo elimina el estado «Te escucho» y muestra el motivo.
- Si Android silencia la captura por privacidad o por otra aplicación, se muestra el motivo sin reiniciar AudioRecord en bucle. Al liberarse vuelve a exigir EDDY.
- La recuperación espera a que el capturador anterior libere audio y recursos nativos. Reintenta a los 5, 15, 60 y luego 300 segundos; una captura estable durante un minuto reinicia ese intervalo. Una inicialización fallida puede reinstalar únicamente el modelo afectado una vez por servicio. La transcripción, la respuesta y el estado quedan visibles si falla la síntesis de voz.
- «Búscame en internet…», «consultá…», «podés buscar…» y preguntas de actualidad solicitan búsqueda web en Groq Compound. Solo se muestran fuentes verificadas por la respuesta de la API; una solicitud sin fuentes se reconoce como no verificada.
- Sin clave de GroqCloud, una búsqueda explícita abre el navegador y explica que hace falta configurar GroqCloud para leer una respuesta con fuentes. La investigación automática no abre el navegador. Los errores de clave, cuota, conexión y tiempo de espera son visibles.
- GroqCloud recibe el diálogo como turnos `user`/`assistant`, separados de las notas y del tono acústico. La pregunta actual no se duplica. Se conservan las preferencias aunque el historial crezca.
- La conversación usa Llama 3.3 70B de GroqCloud por defecto. Las búsquedas usan Groq Compound y solo habilitan su herramienta web. Las respuestas personales aprendidas no reemplazan órdenes reales como borrar memoria.
- El análisis acústico existente sigue siendo una estimación del tono, no un diagnóstico emocional. Las palabras explícitas del usuario tienen prioridad.

## Enseñarle sin conexión

Llamá a EDDY antes de cada frase:

1. «Recordá que prefiero respuestas cortas». Guarda la nota en el teléfono y la incorpora al contexto de conversación.
2. «Me llamo Manuel», seguido de «cómo me llamo». El dato personal se recupera localmente.
3. «Cuando te pregunte mi bebida, respondé café sin azúcar», seguido de «mi bebida». Aprende una respuesta personal exacta. La respuesta aprendida se pronuncia; no se interpreta como una acción del teléfono.
4. «Qué te enseñé» permite revisar lo aprendido; «borrá tu memoria» elimina también notas y respuestas personales.

Las respuestas personales se recuperan desde memoria; no modifican los pesos de GroqCloud ni de los modelos acústicos. Además, una red pequeña aprende localmente a clasificar órdenes reconocidas. Véase [aprendizaje local y autonomía](AUTONOMY_AND_LOCAL_LEARNING.md). Tampoco se reutilizan automáticamente respuestas web antiguas como si fueran actuales.

## Alcance sin Internet

Las órdenes del teléfono, cálculos y memoria personal funcionan localmente. El reconocimiento continuo propio necesita los modelos acústicos instalados; si faltan o no pueden iniciarse, la pantalla indica que la escucha no está disponible en vez de simularla con sesiones de Android. La conversación generativa local requiere preparar el modelo opcional en Ajustes. Su presupuesto real es de 1.280 tokens entre entrada y salida; la entrada se mide con el tokenizador y se reduce a 960 como máximo para dejar espacio a la respuesta.

## Regresión de inicio del detector

El error `Invalid KeywordSpotterConfig: failed to create native KeywordSpotter` se
reproducía porque `keywordsFile` estaba vacío. Sherpa 1.13.6 valida ese archivo en
el constructor; entregar palabras a `createStream` después no evita el fallo.
La configuración ahora escribe `voice-config/eddy-keywords.txt` antes de construir
el detector y usa esa misma lista al crear el stream. No hace falta cambiar ni
volver a descargar los pesos instalados por este arreglo.

La CI prepara las bibliotecas JNI oficiales de Linux y el modelo de activación del
catálogo, y ejecuta `EddyKeywordNativeTest` con las clases Kotlin del AAR Android:
reproduce el error anterior y comprueba que la configuración corregida carga el
modelo, decodifica silencio, no activa EDDY con ese silencio, reinicia el stream y
permite liberar y crear de nuevo el detector. No crea APK. Si se ejecutan pruebas
locales sin `EDDY_NATIVE_KWS_MODELS`, las pruebas nativas se omiten; en CI la
preparación es obligatoria. Esto no sustituye probar el micrófono ni la precisión
con voces reales en el teléfono.

Referencia: [validación de KeywordSpotterConfig en Sherpa 1.13.6](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.6/sherpa-onnx/csrc/keyword-spotter.cc).

## Pronunciación de EDDY

Se mantienen las cinco variantes originales y se añaden cuatro aproximaciones de
la «d» suave entre vocales del español, con acentuaciones alternativas. Comparten
la etiqueta EDDY; no hace falta decir un nombre distinto. Se conserva el micrófono
continuo y la búsqueda de palabras local, sin transcribir conversaciones ambientales.
El cambio actualiza el archivo de palabras al iniciar; no descarga otros pesos.

La regresión usa las clases del AAR Android y JNI real con 19 muestras sintéticas
en bloques de 512 muestras a 16 kHz. Frente a 07997e6, reconoce 3 de 5 llamadas
(antes 2), conservando las dos anteriores. No aumenta las falsas activaciones en
las 14 frases negativas: siguen siendo 3. El texto «Eddy.» sintetizado mejora;
«Edi.» y «Oye Eddy.» de ese sintetizador aún fallan. «El edificio es alto»,
«Le di la comida» y «Ayer pedí ayuda» siguen provocando activaciones incorrectas.

No se presenta esto como entrenamiento del modelo ni como reconocimiento perfecto:
el modelo acústico original es chino/inglés y se adaptan sus palabras fonéticas.
Este corpus pequeño no representa el acento del usuario. Se necesita comprobar la
pronunciación natural y los falsos disparos en el teléfono. El corpus, su procedencia
y la comparación reproducible están en [los recursos de prueba](../app/src/test/resources/voice/wake/README.md).

## Verificación en un teléfono

### Voz de respuesta estable

- La voz neuronal o la de Android se elige en la primera respuesta de la sesión de
  escucha. Preparar una voz adicional no cambia de hablante a mitad de conversación;
  se incorpora al detener y volver a habilitar la escucha desde Ajustes.
- Android conserva el motor y el nombre de la voz elegida. Si esa voz ya no está
  instalada, se elige otra voz española disponible sin conexión en un orden estable.
  Si falta una voz local, Ajustes lo indica y la respuesta sigue visible.
- Si falla la síntesis neuronal antes de emitir audio, se intenta la voz del teléfono
  y se mantiene durante esa sesión. Si ya había comenzado a sonar, no se repite la
  respuesta completa con otro hablante; el texto permanece en pantalla y las próximas
  respuestas usan la alternativa. Ajustes indica el cambio.
- Pedir hablar más lento o más rápido modifica el ritmo, conservando el tono base.
  La selección ya no confunde `female` con `male` ni `woman` con `man`.

Las pruebas de selección y sesión cubren persistencia de la elección ante cambios de
catálogo, orden de enumeración, voces de red y fallos de la voz neuronal. La persistencia
real del motor, su sonido y el cambio de salida de audio se verifican en el teléfono.

### Comprobaciones físicas

La CI ejecuta pruebas unitarias y Lint sin empaquetar APK. No sustituye las siguientes comprobaciones físicas:

1. Esperar «Decí EDDY». Decir «EDDY, qué hora es». Verificar transcripción, respuesta y retorno a espera. Repetir llamándolo primero y esperando «Ajá».
2. Decir una orden sin activarlo: no debe responder. Decir «EDDY, prendé la linterna»: debe ejecutar una vez.
3. Activarlo sin hablar, esperar 31 segundos y dar una orden sin llamarlo: debe ignorarla.
4. Repetir con pantalla bloqueada, altavoz, Bluetooth y tras recibir una llamada. Observar el estado del micrófono y posibles restricciones del fabricante.
5. Con modelos instalados, activar modo avión y comprobar hora, cálculo, nombre y respuesta personal aprendida. Sin modelo generativo local no se promete conversación abierta.
6. Con GroqCloud configurado, pedir noticias actuales y comprobar fuentes. Probar también sin conexión y con una clave inválida; debe explicar el error y volver a permitir una orden.
7. Durante la preparación de modelos, comprobar que no se anuncia «Te escucho». Sin modelos ni red debe explicar el problema; al restaurar la red debe prepararlos y pasar a espera de EDDY. Deshabilitar la escucha en Ajustes, reabrir la app y comprobar que sigue deshabilitada.
8. Comprobar que una interrupción del micrófono sale de «Te escucho», recupera la escucha sin dos capturadores simultáneos y vuelve a exigir el nombre.
9. Desactivar temporalmente la voz española del sistema: la respuesta debe permanecer visible y no bloquear la siguiente activación.
10. Con una voz preparada, pedir varias respuestas y alternar modo avión. Confirmar
    que conserva el hablante y el tono. Reiniciar la escucha y comprobar que Android
    conserva la voz elegida si sigue instalada. Revisar la indicación de voz en Ajustes.
11. Probar «Eddy» con la pronunciación habitual, sin exagerar la «d», en silencio y
    con ruido moderado. Comparar llamadas aisladas y seguidas de una orden; comprobar
    que hablar de otras cosas no activa el asistente. Anotar qué pronunciaciones fallan.

Los cambios de código no actualizan una instalación existente hasta que se prepare e instale una versión posterior, cuando el usuario lo solicite. En esta revisión solo se entregan commits.
