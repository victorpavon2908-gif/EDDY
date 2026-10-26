# Revisión de búsqueda — 2026-10-26

## Cambios

- Web: Bing RSS, DuckDuckGo HTML, Google News y Wikipedia se consultan concurrentemente. Gemini sigue siendo opcional, con modelo configurable mediante `GEMINI_MODEL` y la clave existente `GEMINI_API_KEY`.
- Un plazo compartido de 10 segundos cancela proveedores pendientes. El cliente tiene un límite de 15 segundos. Las respuestas externas se limitan a 1 MB.
- Consultas inválidas o de más de 500 caracteres devuelven HTTP 400 antes de acceder a proveedores.
- Los fallos parciales conservan resultados disponibles. Sin resultados, se informa el problema con cero fuentes: un enlace a Google ya no cuenta como evidencia.
- La síntesis de búsqueda de Gemini utiliza los pasajes vinculados por `groundingSupports` y reasigna las referencias a las fuentes efectivamente mostradas. El texto sin respaldo no se presenta como una respuesta fundamentada. Referencia: https://ai.google.dev/gemini-api/docs/google-search
- Sin síntesis respaldada, se muestran títulos y extractos claramente identificados como contenido del buscador, sin afirmar lectura completa ni verificación independiente.
- Se eliminan duplicados por URL normalizada; se rechazan enlaces que no sean HTTP(S) y enlaces con credenciales. Las noticias priorizan la edición de Nicaragua.
- La ventana de búsqueda cancela búsquedas y sugerencias anteriores, descarta respuestas atrasadas, muestra errores y permite reintentar. Los indicadores ya no afirman conexión o verificación de Google sin evidencia.
- La conversación cancela peticiones anteriores al recibir una nueva orden, incluida «pará», y limita búsqueda y chat a 15 segundos por petición.
- «Busca en internet…» elimina correctamente el prefijo; «busca tutoriales de YouTube» sigue siendo una búsqueda, no una apertura de aplicación.
- Android: C++ y C# conservan su identidad durante el filtrado de relevancia y la planificación. Los diagnósticos de un proveedor fallido no reemplazan una respuesta parcial que ya contiene fuentes; `webUsed` sigue siendo falso cuando no se pudieron leer páginas.
- `npm run lint` ahora comprueba también los tipos del servidor.

## Validación ejecutada

- `npm test`: 51 pruebas aprobadas, incluyendo proveedores en paralelo, cancelación, límite temporal, caídas completas/parciales, tamaño de respuestas, RSS, DuckDuckGo, deduplicación y correspondencia de citas.
- `npm run lint`: aprobado para interfaz y servidor.
- `npm run build`: aprobado.
- `python -m unittest discover -s tests -p 'test_*.py' -v`: 10 pruebas aprobadas.
- `bash scripts/test_ai_core.sh`: 111 pruebas JVM aprobadas.
- `npm run measure`: ejecutado; las métricas físicas siguen pendientes, sin inventar mediciones.
- Prueba HTTP del servidor compilado: cinco tipos de consulta inválida devuelven 400. Una búsqueda real de «energía solar» devolvió HTTP 200 y ocho fuentes de DuckDuckGo en 6420 ms, sin clave Gemini.
- Prueba nativa real: se descubrieron seis fuentes sobre energía solar y se entregó un resumen de extractos. Las lecturas de páginas agotaron el tiempo; la prueba exigente `probe_web_research.sh` terminó con error porque `webUsed=false`. No certifica lectura completa ni funcionamiento en un teléfono.

## Pendiente y límites

- Probar interacción, voz, cancelación y navegación visualmente en navegador y dispositivo Android reales.
- Probar Gemini con una clave de despliegue válida. Las pruebas de citas usan metadatos controlados; no se invocó un modelo con credenciales.
- Los proveedores públicos pueden bloquear consultas o cambiar HTML/RSS. Los plazos y alternativas reducen el impacto, pero no garantizan disponibilidad.
- Esta revisión no representa una auditoría integral del proyecto ni una medición comparativa de precisión. No se ha desplegado un servidor ni generado un APK.
