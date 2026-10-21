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
usa dos hilos, CPU, mmap, contexto de 4.096 tokens y máximo de 512 tokens por defecto y hasta 1.024 tokens para explicaciones, código y síntesis de salida.
Se libera el modelo al finalizar cada solicitud; evita RAM retenida, a costa de carga
por turno. No hay streaming de voz ni garantía de latencia en este módulo.

Hay una sola generación nativa simultánea. La cancelación usa identificadores
monótonos, comprobación durante carga/evaluación y descarte en el cliente. Los límites
son 180 segundos nativos y 95 segundos en el cliente. TurnController mantiene su
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
El catálogo autoriza dos modelos, seleccionables y conservables por separado; no se implementa actualización automática de pesos. La actualización
SQLite de conocimiento continúa siendo independiente.

La carpeta `leo_brain/generative` cuenta dentro del límite combinado de 10 GB,
incluidos parciales. No se borran recuerdos para hacer espacio. Se conserva un mínimo
de 750 MB libres. Los límites previos de memoria adaptativa siguen existiendo, pero
el total combinado puede impedir crecimiento cuando el modelo general ocupa espacio.

## Privacidad y validación

Los mensajes/contexto de esta ruta no salen del teléfono. El modelo puede usar hasta
6.000 caracteres recientes del contexto local. No modifica sus pesos al aprender
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

## Desactivación, cambios y eliminación

Cada activación/desactivación cambia una revisión local. El cliente comprueba esa
revisión antes de enviar la petición y antes de aceptar la respuesta; desactivar y
reactivar no permite reaparecer una respuesta de la configuración anterior.
`ELIMINAR DESCARGA DEL MODELO` pide confirmación, desactiva la generación y elimina
solamente el GGUF autorizado, su parcial y su marcador. No elimina memoria personal,
voz ni archivos de otros modelos. La eliminación comparte el bloqueo de instalación.
Las pruebas Robolectric cubren cambio de revisión, conservación de recuerdos y cuota
combinada; no sustituyen la validación física del servicio separado.


## Catálogo ampliado (2026-10-21)

IMPLEMENTADO: selector de Qwen3 1.7B Q8_0 (1.834.426.016 bytes) y Qwen3 4B Q8_0 (4.280.404.704 bytes), ambos oficiales Apache-2.0. El segundo es el recurso cercano a 5 GB solicitado; no contiene relleno y no fue entrenado por este proyecto. Fuente: https://huggingface.co/Qwen/Qwen3-4B-GGUF . Revisión y SHA256 están fijados en LeoLocalModelCatalog.

La selección solo se activa después de verificar SHA256 completo y obtener realmente 4 en la prueba aritmética dentro del proceso JNI. Las instalaciones anteriores sin model_id siguen usando 1.7B. Ambos archivos pueden conservarse; para regresar se selecciona el pequeño y se pulsa actualizar/validar. Eliminar un modelo inactivo no desactiva el actual. El ID viaja por IPC y se resuelve exclusivamente contra el catálogo autorizado, nunca contra rutas del modelo.

El modelo 4B exige conservadoramente 8 GB físicos y 5 GB disponibles, además de 64 bits. Son límites de admisión de ingeniería, no mediciones de rendimiento. Si no cumple, se conserva el fallback existente. No garantiza viabilidad en HONOR NIC-LX3. PENDIENTE DE VALIDACIÓN FÍSICA: RAM, latencia, temperatura, voz simultánea y estabilidad Android con ambos modelos.

El techo de almacenamiento combinado aumenta a 10 GB para permitir conservar ambos modelos (6,11 GB), sin cambiar los límites personales de 4,5 GB adaptativos y 500 MB de conocimiento. El límite es compartido: la suma de todos los máximos no está garantizada simultáneamente. No se eliminan memorias para hacer espacio; se rechaza la descarga que no cabe y se reservan 750 MB libres.

La CI ejecuta scripts/validate_leo_local_model.sh compact y large en trabajos separados: descarga real, SHA256, JNI real, cuatro preguntas y cancelación. Sus resultados son pruebas pequeñas en host, no una puntuación general de inteligencia ni evidencia de teléfono.

## Google Gemini opcional

IMPLEMENTADO: preset editable del endpoint oficial compatible con OpenAI, Gemini 3.8 Flash, clave obligatoria para el host Google, cifrado Android Keystore y permisos separados para pregunta, historial y extractos web. Documentación: https://ai.google.dev/gemini-api/docs/openai . Se conserva el botón PROBAR CONEXIÓN que exige respuesta real. La clave no se incluye en Git ni APK. Elegir el preset no concede permisos ni activa llamadas.

PENDIENTE: activación y prueba real de Gemini, por falta de una clave de API. La conexión de Google Drive no proporciona una credencial de AI Studio ni administra facturación. No se contrató un servicio, no se hizo una llamada de modelo remoto y no se ha medido su calidad. El servidor/modelo puede cambiarse sin recompilar.
