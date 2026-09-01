# GroqCloud en NIKO

## Conexión directa

NIKO usa `POST https://api.groq.com/openai/v1/chat/completions`, con la clave en
`Authorization: Bearer …`. No consulta Gemini ni el backend legado de Render.
La pantalla de Ajustes guarda la clave Groq únicamente en el teléfono, en una
preferencia distinta de las credenciales antiguas. No hay claves incrustadas en Git.

Si la instalación anterior usaba Groq mediante Render, su clave estaba en el servidor:
NIKO no puede obtenerla automáticamente. Al instalar esta revisión, abrir Ajustes,
guardar la clave de GroqCloud y pulsar **Guardar y probar**. La prueba de conexión
comprueba conversación; una búsqueda aparte comprueba el acceso a Compound.

## Modelos y búsquedas

- Conversación: `llama-3.3-70b-versatile`, configurable en Ajustes. Los turnos anteriores
  se envían como `user` y `assistant`, con memoria y personalidad en el contexto.
- Investigación: `groq/compound`, con únicamente `web_search` habilitada. Si el modelo
  no está disponible, puede probar `groq/compound-mini`; no sustituye búsquedas por
  modelos que carecen de esa herramienta.
- La solicitud de conversación desactiva herramientas. La ruta de investigación
  respeta la configuración de búsqueda automática y las solicitudes explícitas.
- Solo los resultados de búsqueda devueltos en `executed_tools[].search_results`
  aportan fuentes. Un enlace escrito por el generador no basta para afirmar que buscó.
  Si Compound no devuelve metadatos de fuentes, la búsqueda se presenta como no verificada.

Hay un presupuesto compartido de 18 segundos y un máximo de tres intentos en conversación
o dos en investigación. No se reintenta una clave inválida, cuota agotada, respuesta
bloqueada ni solicitud inválida. Un modelo retirado sí puede activar una alternativa.
Cancelar el turno interrumpe la espera y desconecta la petición HTTP. Las redirecciones
HTTP están desactivadas para mantener la credencial en el destino previsto.

## Funciones locales

Se conservan la activación por nombre, transcripción, voz, personalidad, aprendizaje
de intenciones y memoria local. «Sin Internet» evita llamadas a Groq. La conversación
abierta sin conexión requiere haber preparado Qwen; GroqCloud necesita conexión y
una cuenta con clave, cuota y acceso a los modelos correspondientes.

## Validación

Las pruebas verifican el formato del historial, las fuentes, la separación de claves,
los errores, los reintentos, el límite de espera y la cancelación. GitHub ejecuta la
suite Android y Lint sin generar APK. La búsqueda real y la voz deben probarse después
en el teléfono con la clave del usuario; la suite usa respuestas simuladas de la API.

Referencias: [API de Groq](https://console.groq.com/docs/api-reference),
[herramientas de Compound](https://console.groq.com/docs/compound/built-in-tools),
[fuentes de búsqueda](https://console.groq.com/docs/tool-use/built-in-tools/web-search).
