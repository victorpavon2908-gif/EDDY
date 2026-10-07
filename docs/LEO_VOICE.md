# LEO: voz, propiedad de turnos y límites físicos

IMPLEMENTADO: token de turno en CoroutineContext; validación al publicar texto, devolver investigación, despachar herramientas y guardar/abrir una herramienta generada. El bus invalida la generación antes de encolar la cancelación en Main. Productores antiguos no pueden publicar aunque ignoren la cancelación del Job. Una nueva orden válida entregada por el motor puede reemplazar el trabajo activo.

Se conservan KWS, ASR, VAD, pre-roll, fidelidad de audio, selección de voz y perfiles. Android TTS mantiene su propio prefijo de utterance y época de notificaciones. GroqHttpClient conserva desconexión/cancelación de red y backpressure del streaming.

El reconocedor principal `LeoPlatformVoiceEngine` ahora **mantiene sesiones de escucha durante pensamiento y TTS**. Los parciales válidos “LEO”, “LEO pará”, controles de parada o “no, eso no” interrumpen el productor y el reproductor mediante `LeoRealtimeTurnBus.interruptTurn()`. Un wake solo conserva la ventana de la próxima orden; el resultado final conserva una orden en la misma frase.

Cada sesión posee un listener y generación propios. Los callbacks tardíos, duplicados o posteriores a `stop()` no pueden publicar una orden. Al finalizar una reproducción no interrumpida se cancela su sesión de reconocimiento antes de abrir la siguiente. El TTS publica una referencia textual con propietario y una cola de 800 ms; las coincidencias completas por palabras se rechazan como posible eco. Esto NO es cancelación acústica de eco: una frase igual a lo que LEO acaba de decir puede ser suprimida incluso si la dijo el usuario; eco transcrito de forma distinta puede escapar. La interrupción libre sin wake solo acepta controles acotados.

**PENDIENTE DE VALIDACIÓN FÍSICA:** dúplex acústico real en HONOR NIC-LX3, altavoz/Bluetooth, retardo de parciales, falsos positivos y continuidad entre sesiones. La API Android no garantiza reconocimiento continuo ni simultaneidad efectiva en todos los proveedores. No se modifica el motor nativo avanzado ni su KWS. Las pruebas de callbacks no demuestran que un proveedor OEM entregue audio mientras reproduce TTS.

Métricas existentes: recordWake, recordTranscript, recordResponseStarted, recordResponseText, recordSpeechRequested y recordSpeechStarted. El evento Android onStart de TTS es un callback del motor, NO medición acústica de sonido audible. El tiempo request→first text tampoco equivale al primer token interno de razonamiento.

PENDIENTE DE VALIDACIÓN FÍSICA: wake→speech, fin de habla→ASR, primer audio audible, interrupción, pantalla apagada, audio focus, llamadas, Bluetooth, música, ruido, distancia, consumo/temperatura. Instrumentación para hitos faltantes debe hacerse en la ruta concreta del motor y contrastarse con grabación externa. No se asignan valores a hitos que no se observan.


## Evidencia y límites

Estado de esta iteración: IMPLEMENTADO donde se identifica una llamada real; AUTOMATIZADAMENTE PROBADO solo para los casos ejecutados en [LEO_VALIDATION.md](LEO_VALIDATION.md). Toda propiedad del teléfono permanece **PENDIENTE DE VALIDACIÓN FÍSICA**. Los contratos o tipos sin ruta productiva se indican como PLANIFICADO.
