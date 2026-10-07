# Memoria semántica, dúplex e interfaz — 2026-10-07

## IMPLEMENTADO

- Ajustes → **PREPARAR MEMORIA SEMÁNTICA** instala DistilUSE multilingüe INT8 (137.9 MB en disco). Primera instalación requiere Internet; después la inferencia funciona sin red. El estado informa si usa el modelo o el respaldo. No hay entrenamiento privado en el teléfono.
- `NikoMemory` inyecta el encoder en `NikoLongTermMemory`: ruta de recuperación real, compatible con el archivo SQLite existente. No basta con añadir un archivo llamado “embeddings”.
- Se preserva `libonnxruntime.so` de Sherpa 1.13.7 (runtime 1.27.1); de ONNX Runtime Android 1.22 solo se extraen `classes.jar` y `libonnxruntime4j_jni.so`. La API C es retrocompatible. No se usa `pickFirst` para decidir qué runtime cargar. La regresión CI comparte el runtime nativo preparado para Sherpa.
- Reconocedor Android principal activo durante TTS/razonamiento, interrupción en parcial, final con orden, control de generación y rechazo textual de eco. Motor nativo avanzado conservado.
- Interfaz principal: fondo marfil, escenario verde oscuro, superficies planas, tipografía más grande, iconos coherentes, menor decoración y sin onda animada en reposo. Herramientas, fuentes, respuesta completa, accesos y ajustes siguen accesibles. No sustituye la UI por una imagen.

## AUTOMATIZADAMENTE PROBADO

`evidencias/leo-semantic-duplex-2026-10-07/semantic-jvm.log` registra inferencia real Kotlin/ONNX, no valores fabricados: seis consultas españolas, cada una con paráfrasis y distractor; dimensiones/norma, copia de caché, entrada vacía, integridad y cinco pruebas del tokenizer. Son siete tests JUnit, no una evaluación general de comprensión.

Reproducción Linux:

```sh
python3 scripts/prepare_semantic_test.py --destination /tmp/leo-semantic
export LEO_EMBEDDING_MODELS=/tmp/leo-semantic
export LEO_JSON_JAR=/ruta/json-20250517.jar
bash scripts/test_semantic_embeddings.sh
bash scripts/test_voice_fidelity.sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

CI prepara modelo real y JNI antes de los tests Android. Sin `LEO_EMBEDDING_MODELS`, la prueba neuronal declara **skipped**, nunca un pase ficticio. Las pruebas de generación/interrupción usan callbacks Robolectric; no simulan resultados físicos ni sustituyen la implementación productiva. El workflow conserva reportes JUnit y lint. No genera APK.

## PENDIENTE DE VALIDACIÓN FÍSICA

En HONOR NIC-LX3, con una instalación de esta revisión:

1. Preparar memoria en Ajustes; desconectar Internet; guardar y consultar recuerdos con paráfrasis. Registrar primera consulta y consultas repetidas, memoria RAM y temperatura.
2. Solicitar una respuesta larga. Interrumpir con “LEO”, “LEO pará”, “no, eso no”, y “LEO, mejor buscá otra cosa”; comprobar silencio, orden posterior y ausencia de texto anterior.
3. Repetir con TTS que diga “LEO”, “para” y una frase de parada. Registrar eco rechazado, falsas interrupciones y paradas omitidas. No atribuir identificación de hablante al filtro textual.
4. Repetir con altavoz, auriculares, Bluetooth, música, llamada, pantalla apagada, ruido y distancias anotadas. Grabar desde un equipo externo para medir audio audible; los callbacks no sirven como sustituto.
5. Revisar interfaz con fuente grande, teclado, pantalla pequeña y modo horizontal: legibilidad, targets táctiles, respuesta larga y fuentes.
6. Conservar captura externa y `python3 scripts/collect_leo_device_evidence.py --help` para usar el colector ADB existente. No asignar aprobado sin la evidencia de cada ejecución.

## PLANIFICADO / LIMITACIONES

- Índice persistente de vectores y evaluación con corpus español más amplio. La ventana de candidatos sigue siendo 180; no promete recuperar cualquier recuerdo histórico.
- Cancelación acústica con referencia de audio propia: Android SpeechRecognizer no expone PCM para implementarla aquí. El dúplex depende del proveedor y tiene pausas al renovar sesiones.
- Capturas reales de la nueva UI y pruebas de autonomía/batería: requieren dispositivo o emulador; no se han inventado renders ni mediciones.

Fuentes técnicas: [modelo y módulos](https://huggingface.co/sentence-transformers/distiluse-base-multilingual-cased-v2), [ONNX Java](https://onnxruntime.ai/docs/get-started/with-java.html), [API SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer).
