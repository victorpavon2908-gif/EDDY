# Generación local de conocimiento y código

## Implementación

La ruta `NikoAssistantService → LeoNeuralCore → NikoLocalLlm` conserva la memoria
personal y las acciones anteriores. Después de recuperar conocimiento aprendido,
puede consultar el modelo general activado por el usuario. Ante errores, ausencia,
interrupción, límite de contexto o salida incompleta vuelve a las rutas existentes.
Las consultas de actualidad siguen pasando por investigación web. La salida del modelo
es texto: no se interpreta como comandos de Accessibility ni se ejecuta como código.

Se integra **Qwen3-1.7B-Q8_0**, publicado por Qwen, preentrenado y ajustado por su
proveedor, no entrenado por LEO. Archivo de 1.834.426.016 bytes; licencia Apache 2.0.
Repositorio: https://huggingface.co/Qwen/Qwen3-1.7B-GGUF
Revisión: `90862c4b9d2787eaed51d12237eafdfe7c5f6077`.
SHA-256: `061b54daade076b5d3362dac252678d17da8c68f07560be70818cace6590cb1a`.
La licencia completa se conserva en assets/licenses.

El runtime C/C++ es llama.cpp (MIT), revisión
`a7a98e0fffed794396b3fbad4dcdbbc184963645`. CMake verifica el digest del archivo
fuente. No descarga código durante la ejecución Android. El puente JNI se incluye
solo para arm64-v8a y x86_64; las otras ABI conservan las funciones anteriores.

## Aislamiento y límites

`LeoLocalInferenceService` usa el proceso privado `:leo_inference`, no exportado.
La aplicación secundaria omite inicialización de voz, estado global y diagnóstico
principal. Un crash nativo se transforma en desconexión del cliente. La generación
usa dos hilos, CPU, mmap, contexto de 2.048 tokens y máximo de 384 tokens de salida.
Se libera el modelo al finalizar cada solicitud; evita RAM retenida, a costa de carga
por turno. No hay streaming de voz ni garantía de latencia en este módulo.

Hay una sola generación nativa simultánea. La cancelación usa identificadores
monótonos, comprobación durante carga/evaluación y descarte en el cliente. Los límites
son 90 segundos nativos y 95 segundos en el cliente. TurnController mantiene su
comprobación posterior. Respuestas truncadas se rechazan. No se publican pensamientos
internos ni tokens de control. No se puede garantizar que el modelo siempre siga
instrucciones o produzca datos correctos.

## Descarga y activación

En Ajustes, `ACTUALIZAR CEREBRO DE LEO` muestra versión, tamaño y confirmación de
consumo de datos. No descarga en segundo plano sin permiso. Requiere 64 bits y al
menos 5 GB de RAM física y 2,25 GB disponibles; es un filtro preventivo, no una medición de rendimiento.
La descarga HTTPS puede reanudarse; valida Content-Range y tamaño exacto, SHA-256
completo y cambio de nombre atómico antes de probar la inferencia. El catálogo fijo
está incluido en el APK firmado; no se acepta un manifiesto remoto arbitrario.
Esto no constituye una firma independiente del editor del modelo.

Solo se activa después de que el teléfono responda correctamente una prueba básica
`2 + 2`. Esa prueba NO certifica razonamiento general. Se conserva el cerebro previo;
`VOLVER AL CEREBRO ANTERIOR` desactiva la generación y conserva los archivos.
Solo hay una versión autorizada actualmente; no se implementa un historial de varios
modelos generales descargables ni actualización automática de pesos. La actualización
SQLite de conocimiento continúa siendo independiente.

La carpeta `leo_brain/generative` cuenta dentro del límite existente de 5 GB,
incluidos parciales. No se borran recuerdos para hacer espacio. Se conserva un mínimo
de 750 MB libres. Los límites previos de memoria adaptativa siguen existiendo, pero
el total combinado puede impedir crecimiento cuando el modelo general ocupa espacio.

## Privacidad y validación

Los mensajes/contexto de esta ruta no salen del teléfono. El modelo puede usar hasta
2.000 caracteres recientes del contexto local. No modifica sus pesos al aprender
una preferencia: esos datos siguen siendo memoria persistente del sistema existente.

- AUTOMATIZADAMENTE PROBADO localmente: siete contratos de prompt, integridad,
  reanudación y cancelación de verificación.
- CI `LEO real local model validation`: compila el mismo JNI, descarga y verifica los
  pesos oficiales y ejecuta cuatro comprobaciones acotadas de respuestas más
  cancelación previa/durante generación. Sus logs son evidencia del host, no del teléfono.
- CI Android: compila Kotlin, ejecuta pruebas/Lint y verifica enlace nativo por ABI.
- PENDIENTE DE VALIDACIÓN FÍSICA: carga en HONOR NIC-LX3, RAM máxima, temperatura,
  batería, latencia, audio simultáneo, interrupción con micrófono y muerte del proceso.
- PLANIFICADO: benchmark independiente amplio en español, evaluación especializada
  de código/emociones, afinado con GPU, actualizaciones con firma propia e historial
  de versiones. No se afirma calidad comercial ni superioridad sobre otros asistentes.

## Evidencia real obtenida

La ejecución de CI 39480980169 descargó los pesos oficiales, verificó su hash y
completó cuatro respuestas conocidas (4, Managua, 63, `2 + 3`) y cancelación
previa/durante generación. Resultado registrado en
`evidencias/leo-local-model-2026-10-20/host-results.json`. No se traduce en un
puntaje global de inteligencia. La memoria disponible y el rendimiento en Android
siguen requiriendo medición física.
