# LEO: voz, propiedad de turnos y límites físicos

IMPLEMENTADO: token de turno en CoroutineContext; validación al publicar texto, devolver investigación, despachar herramientas y guardar/abrir una herramienta generada. El bus invalida la generación antes de encolar la cancelación en Main. Productores antiguos no pueden publicar aunque ignoren la cancelación del Job. Una nueva orden válida entregada por el motor puede reemplazar el trabajo activo.

Se conservan KWS, ASR, VAD, pre-roll, fidelidad de audio, selección de voz y perfiles. Android TTS mantiene su propio prefijo de utterance y época de notificaciones. GroqHttpClient conserva desconexión/cancelación de red y backpressure del streaming.

**No hay full-duplex verificado en el reconocedor principal de Android:** pausa durante procesamiento/habla. El motor nativo sí contiene barge-in por KWS, pero su detección física no está probada aquí. La nueva protección de turnos resuelve coordinación una vez recibido el evento, no inventa detección de voz donde el motor no escucha.

Métricas existentes: recordWake, recordTranscript, recordResponseStarted, recordResponseText, recordSpeechRequested y recordSpeechStarted. El evento Android onStart de TTS es un callback del motor, NO medición acústica de sonido audible. El tiempo request→first text tampoco equivale al primer token interno de razonamiento.

PENDIENTE DE VALIDACIÓN FÍSICA: wake→speech, fin de habla→ASR, primer audio audible, interrupción, pantalla apagada, audio focus, llamadas, Bluetooth, música, ruido, distancia, consumo/temperatura. Instrumentación para hitos faltantes debe hacerse en la ruta concreta del motor y contrastarse con grabación externa. No se asignan valores a hitos que no se observan.


## Evidencia y límites

Estado de esta iteración: IMPLEMENTADO donde se identifica una llamada real; AUTOMATIZADAMENTE PROBADO solo para los casos ejecutados en [LEO_VALIDATION.md](LEO_VALIDATION.md). Toda propiedad del teléfono permanece **PENDIENTE DE VALIDACIÓN FÍSICA**. Los contratos o tipos sin ruta productiva se indican como PLANIFICADO.
