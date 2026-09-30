# Voz progresiva Android 0.12.1

## Recorrido

La transcripción local existente entrega una orden. Las acciones directas y memoria se resuelven igual que antes. Cuando el coordinador elige conversación en nube, Groq envía deltas SSE; el servicio muestra texto progresivo y agrupa frases en una cola. La primera frase se envía al TTS existente sin esperar el cierre del flujo. Los callbacks de reproducción retiran una frase a la vez. La respuesta final se conserva una sola vez en memoria y no se vuelve a pronunciar completa.

La interrupción limpia la cola y cancela el trabajo productor; el transporte cierra el canal y la conexión HTTP, incluso con una lectura bloqueada. Los mensajes parciales del proveedor solo usan `delta.content`, nunca campos de razonamiento ni salidas de herramientas. Se limita el tamaño de eventos, respuesta y cola de transporte.

Investigación con fuentes permanece sin streaming. No se cambian permisos, proveedor, credenciales, detección de fin de habla ni modelos ASR. No se inicia inferencia sobre transcripciones provisionales que podrían resultar equivocadas. La arquitectura interna de ChatGPT no se conoce ni se reproduce aquí.

## Pruebas reproducibles

```sh
bash scripts/test_voice_fidelity.sh
bash scripts/test_ai_core.sh
LEO_JSON_JAR=/ruta/json-20250517.jar bash scripts/test_streaming.sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

El script de streaming requiere el JAR `org.json:json:20250517` y las librerías Kotlin/JUnit de Gradle 8.13. Sus pruebas simulan fragmentos de red, finalización, red bloqueada, cancelación, error HTTP, respuesta parcial y vencimiento del plazo. No consumen una API key ni sustituyen una prueba de audio real. El workflow Android también incluye estas pruebas JUnit.

## Comprobación en teléfono pendiente

Con Groq configurado, pedir una explicación de varias frases y comprobar que EDDY empieza a hablar mientras el texto continúa. Interrumpir a mitad de frase y comprobar que no reaparece audio del turno anterior. Repetir sin red, con una caída a mitad de respuesta, sin clave, con órdenes locales y con una búsqueda que requiera fuentes. Comparar 20 turnos equivalentes y reportar mediana y percentil 95 de `Orden → primer texto` y `Orden → primer sonido`.

Esas mediciones arrancan cuando el servicio acepta la orden ya transcrita; la latencia ASR se muestra por separado. No se promete un número de milisegundos ni equivalencia de velocidad/calidad con ChatGPT. El TTS neural de respaldo puede introducir pausas de síntesis entre frases; verificar ambas voces en dispositivo.
