# Voz: ruta vigente y streaming retirado

Actualizado el 2026-10-12. Esta página reemplaza las instrucciones de streaming Groq de 0.12.1, que ya no corresponden al producto actual.

## IMPLEMENTADO

`NikoAssistantService` utiliza `LeoPlatformVoiceEngine` como reconocedor principal y `NikoLocalVoiceEngine` como respaldo. Las respuestas aprobadas por el control de turno se envían a `NikoTextToSpeech`, basado en el TTS de Android. El bus `LeoRealtimeTurnBus` y la cancelación de turno se conservan.

La instancia nullable `progressiveSpeech` nunca recibía una cola y la propiedad lazy `neuralTts` nunca se consultaba para hablar. Se eliminaron esas rutas y sus pruebas exclusivas. También se retiraron las implementaciones Groq que solo sobrevivían en el árbol de tests. Esto no implementa síntesis incremental: no hay un productor de deltas conectado al TTS actual.

El catálogo deja de descargar el paquete Piper que ese sintetizador inactivo habría consumido. No borra modelos previamente descargados ni modifica preferencias, identificadores o bases de datos. KWS, VAD, denoiser, Canary, Whisper e identificación del hablante se conservan.

## Pruebas reproducibles

```sh
bash scripts/test_voice_fidelity.sh
bash scripts/test_ai_core.sh
LEO_JSON_JAR=/ruta/json.jar bash scripts/test_agent.sh
./gradlew :app:externalNativeBuildDebug :app:testDebugUnitTest :app:lintDebug
```

Los scripts JVM usan las librerías Kotlin/JUnit de Gradle 8.13. Las pruebas comprueban políticas, cancelación, control de turnos y procesamiento de audio; no prueban un micrófono físico. Los resultados de esta limpieza se registran en [LEO_CODE_CLEANUP.md](LEO_CODE_CLEANUP.md).

## PENDIENTE DE VALIDACIÓN FÍSICA

Reproducir una respuesta, interrumpir con «LEO, pará» y verificar que el audio anterior no reaparece. Repetir con pantalla apagada, ruido, Bluetooth, llamadas y música. Probar instalación nueva y actualización con modelos antiguos ya descargados. Verificar la selección y disponibilidad offline de la voz Android instalada.

Conservar evidencia de ASR, primer texto y primer sonido usando los diagnósticos existentes. No se atribuyen mediciones nuevas de latencia, batería o precisión a esta limpieza.

## PLANIFICADO

Una futura voz incremental necesitará un productor real, límites de cola, cancelación de transporte, callbacks asociados al turno y pruebas de integración. No se conserva una implementación desconectada como si proporcionara esa capacidad.
