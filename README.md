# LEO 0.10.2

Búsqueda web nativa y preferencia de voz. Requiere Android 12 o posterior.
La búsqueda explícita no requiere Groq. La conversación generativa puede usar
GroqCloud opcionalmente. El identificador Android heredado se conserva para actualizar
las instalaciones anteriores sin perder datos ni permisos.

## Corrección de búsqueda y voces

- «Leo, buscame información sobre Rubén Darío en 1916» consulta la web directamente.
  Conserva el tema y las fechas, aunque esté desactivada la investigación automática.
- Bing y DuckDuckGo aportan resultados generales; las consultas de actualidad usan
  también Google News. Se filtran por el tema real, antes y después de leer páginas.
  Inicios de sesión (incluidos Outlook/Microsoft), formularios y páginas ajenas al tema
  no forman parte de la respuesta. Los errores no se sustituyen por respuestas inventadas.
- Hay un presupuesto de red de 20 segundos y lecturas concurrentes. Los extractos y
  enlaces no implican corroboración independiente ni lectura completa de todas las páginas.
- Se atenúa audio débil, se desactiva la ganancia automática adicional y se solicita
  orientación del micrófono hacia el usuario cuando Android y el equipo lo admiten.
- En **Ajustes → Mi voz → Registrar mi voz**, estando solo, decí cuatro frases distintas
  de 2 a 6 segundos. Esperá la confirmación entre frases y mantené Ajustes abierto.
  Si falta el modelo, usá «Preparar reconocimiento de mi voz». Al terminar se activa
  «Priorizar mi voz»; desde ahí también podés desactivarlo, registrarte otra vez o borrarlo.
- El registro no ejecuta las frases como órdenes. Guarda solo un vector numérico local;
  el audio no se guarda ni se envía. No se reutiliza el perfil anterior aprendido
  automáticamente, ni se aprende la voz de otras personas durante el uso normal.
- Con el perfil activo, se comprueba la voz antes de ejecutar órdenes, seguimientos e
  interrupciones. Una voz no verificable se rechaza; el registro vence a los tres minutos.
  Los resultados provisionales de transcripción se ocultan hasta verificar la voz.

**Límite:** esto prioriza la voz registrada y reduce interferencias, pero no separa
perfectamente dos voces hablando a la vez. CAMPPlus identifica voces; GTCRN reduce
ruido, no extrae una persona de una mezcla. Los umbrales necesitan prueba real en el
Honor X6c: una frase corta o dicha muy bajo puede rechazarse. No es autenticación segura.

Pruebas y casos de aceptación: [validación de búsqueda y voz](docs/LEO_SEARCH_VOICE_VALIDATION.md).


## Uso y desarrollo

Abrí LEO, completá la preparación inicial y concedé permiso de micrófono. Decí
«Leo» seguido de la petición. Para escuchar con la pantalla apagada, permití su
funcionamiento en segundo plano en los ajustes de batería. Android puede interrumpir
el micrófono por llamadas, otra aplicación, privacidad o ahorro de batería.

Sin conexión quedan disponibles la voz instalada y las acciones locales del teléfono;
la búsqueda necesita Internet. Para conversación remota configurá tu clave en Ajustes.
El LLM MediaPipe en proceso está desactivado por estabilidad en Android 12+.

Las entregas son commits. CI ejecuta pruebas y Lint. No genera APK automáticamente.
JDK 17, SDK 36 y Gradle 8.13:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
pytest -q backend
```

Solo ante solicitud expresa de APK:

```sh
python scripts/bundle_voice_models.py
./gradlew :app:assembleDebug
```

Los modelos no se guardan en Git. Conservá la misma clave de firma al actualizar;
no desinstalés sin respaldar los datos que necesités. `backend/` es legado y no
interviene en la búsqueda nativa ni en la conexión directa a GroqCloud.

Referencias adicionales: [GroqCloud](docs/GROQ_CLOUD.md),
[validación de voz](docs/VOICE_DIALOGUE_VALIDATION.md).
