# LEO: investigación nativa sin Groq — 2026-10-10

## IMPLEMENTADO

Por petición del propietario, Android ya no contiene el cliente Groq ni el adaptador de planificación remota. No se cambió a otro proveedor. Las credenciales antiguas se eliminan al iniciar el servicio, abrir Ajustes o construir el cliente; se conservan nombres de preferencias, personalidad, estado del micrófono, archivo SQLite, modelos locales y embeddings.

Ruta de conocimiento: `ConversationCoordinator → researchReply → NikoAiClient → LeoNativeWebSearch → búsqueda pública → lectura de páginas → extracción local → respuesta y fuentes → memoria`. Preguntas informativas, explicaciones, recetas y ejemplos de programación investigan antes de usar conocimiento local no actualizado. Saludos, identidad, memoria personal, acciones y solicitudes explícitas sin Internet mantienen sus rutas locales. La opción de investigación automática sigue respetándose; una búsqueda explícita funciona aunque esté desactivada.

- Descubrimiento Bing RSS/DuckDuckGo y Google News para actualidad, con presupuesto y cancelación existentes.
- Consultas complementarias para procedimientos y código; preferencia heurística por documentación técnica primaria. Esa preferencia no certifica exactitud.
- Lectura de párrafos y listas. Los procedimientos conservan el orden de una sola guía; no mezclan instrucciones de recetas distintas. Los extractos incompletos se señalan.
- Ejemplos `pre` conservan saltos de línea, indentación y entidades HTML. No se ejecutan, no se presenta su compilación como verificada y se señala que deben comprobarse versiones/dependencias. No se corta a mitad un bloque grande para fingir código completo.
- Respuestas factuales breves con citas que remiten a URLs recuperadas. Si no hay evidencia útil, devuelve esa limitación; no fabrica una respuesta.
- La investigación recibe solo la consulta pública y su continuidad temática. No manda el historial completo, memoria personal ni el árbol de pantalla a un modelo remoto.
- Ajustes Android y companion web sin campos de clave Groq. El servidor web también elimina su endpoint Groq. El companion conserva su integración opcional Gemini preexistente; Android no la usa y no se añadió ningún sustituto remoto.

Los auxiliares del protocolo retirado viven solo en `src/test`, sin endpoint por defecto, para mantener la regresión histórica de parsing/streaming. No forman parte de la aplicación. No confundir esos tests con un proveedor habilitado.

## CAMBIO DE CAPACIDADES AL RETIRAR EL MODELO

La extracción web **no equivale a razonamiento generativo ni a un experto ilimitado en programación**. Devuelve información y ejemplos encontrados; no diseña ni verifica automáticamente cualquier programa. La reformulación abierta, traducción de cualquier fuente y depuración contextual avanzada necesitan un modelo adecuado y evaluación propia.

Se conservan las herramientas generadas por composición local, juegos y acciones determinísticas. La generación abierta de especificaciones y los planes ambiguos de varias pantallas que dependían de Groq ya no tienen ese compilador remoto: el planificador conserva el contrato y validación, pero sin un compilador instalado rechaza el paso en vez de simular éxito. Las acciones directas de Accessibility siguen disponibles. La descripción de pantalla usa el árbol observado localmente, sin fingir visión.

Las recetas no garantizan que una página tenga todos los ingredientes, alergias o precauciones; el mensaje remite al procedimiento completo. Los bloques de código son ejemplos de la página, no una solución generada y ejecutada para cada petición. Páginas con JavaScript, muros de acceso, CAPTCHA y cambios de buscador pueden impedir la recuperación.

## VALIDACIÓN

- `scripts/test_ai_core.sh`: routing local/web, cancelación, relevancia, lectura, orden de pasos, integridad de código, citas y fallos sin respuesta inventada. Casos con transporte inyectado son fixtures, no mediciones de Internet.
- Tests Android: migración idempotente de credenciales, conservación de preferencias/micrófono y fachada que no comparte contexto privado; suite completa en Actions del commit publicado.
- `scripts/probe_web_research.sh 'Cómo ordenar listas en Python' 'Cómo preparar arroz blanco'`: prueba HTTP real y reproducible; termina con error si no obtiene fuentes. Respeta el proxy explícito del host. Logs en `evidencias/leo-web-first-2026-10-10/`; un fallo se conserva, no se etiqueta como aprobado.
- `npm test` y `npm run lint`: companion web.

**PENDIENTE DE VALIDACIÓN FÍSICA:** consultas habladas en HONOR, interrupción durante investigación, voz leyendo respuestas con código, red móvil, offline y latencia audible. No se generó APK. No hay benchmark que pruebe pericia ilimitada ni superioridad sobre otros asistentes.

### Resultado del sondeo HTTP en este entorno

Las dos consultas del sondeo no recuperaron fuentes útiles, incluso tras configurar el proxy del host. La inspección HTTP encontró Bing RSS con 0 elementos y DuckDuckGo con un desafío CAPTCHA. No se intentó sortearlo. Esto es un **fallo de recuperación observado**, no una aprobación de búsqueda en Internet. La extracción y routing pasan con fixtures, pero el servicio depende de la disponibilidad de buscadores públicos y debe probarse también desde la red del teléfono. Los logs fallidos se conservan separados.
