# Integración de investigación y memoria — 2026-10-16

## Rutas implementadas

`LeoNativeWebSearch` ahora devuelve pasajes tipados: índice de fuente, texto extraído y
si se leyó una página. No se reconstruyen identidades de fuentes analizando texto web:
una página que escriba «FUENTE 2» no puede introducir una referencia nueva.

`NikoAssistantService.researchReply` conecta búsqueda → pasajes → inferencia opcional →
`LeoResearchSynthesis` → estado conversacional → UI/TTS. El usuario debe habilitar por
separado el envío de extractos en Ajustes. Configuraciones anteriores no quedan autorizadas
a enviar más datos automáticamente. El modelo recibe consulta y extractos, sin memorias.

La síntesis requiere salida estructurada, con hasta seis afirmaciones, referencias y citas
textuales. Se comprueba que cada índice pertenezca a las páginas recuperadas y que cada cita
exista en el extracto enviado. Solo el renderer de LEO agrega números de referencia. Se
rechazan URLs/números de cita introducidos manualmente en las afirmaciones del modelo.
Las categorías son información reportada, inferencias e información incierta; no se declara
«confirmado» un hecho solo porque el modelo repita una fuente.

Los extractos se incluyen como datos no confiables en el mensaje del usuario, nunca como
instrucciones del sistema. El proveedor no tiene herramientas para ejecutar contenido web.
Los resúmenes de resultados de búsqueda sin página leída no habilitan esta síntesis.

Ante JSON inválido, cita inexistente, error del servidor o falta de autorización, se conserva
la respuesta extractiva anterior. La cancelación se propaga: no produce un fallback obsoleto.
Los turnos se comprueban antes/después de la inferencia. El aprendizaje automático de la
investigación recibe la respuesta recuperada original, no las conclusiones nuevas del modelo.
El contexto conversacional sí conserva la respuesta finalmente mostrada.

## Límite importante de la validación

Una cita existente no demuestra que la paráfrasis sea lógicamente correcta. Este control
verifica procedencia y coincidencia textual; no es un verificador de verdad ni reemplaza
contrastar fuentes primarias. Los tests de contrato no califican la capacidad de un modelo.
No se configuró un proveedor real ni se midió la calidad de síntesis en esta sesión.

La respuesta se genera sin streaming. Se mantienen los límites y cancelación del cliente,
y se agrega un límite solicitado de 4096 tokens de salida. La compatibilidad de ese parámetro
debe verificarse con el proveedor elegido; un rechazo conserva la búsqueda local.

## Memoria personal conectada

- Las correcciones se aplican a la preferencia coincidente de comida, bebida, música o lugar.
- Si el mismo valor aparece en varios tipos, se solicita aclaración antes de cambiarlo.
- «Ya no me gusta X» retira la preferencia coincidente sin exigir un reemplazo.
- Se conservan mayúsculas y tildes en nuevos valores; los anteriores siguen siendo legibles.
- Las sugerencias sencillas de comida/bebida usan la preferencia actual local. Si falta, se
  pregunta por gustos y disponibilidad, sin inventarlos ni consultar el proveedor.
- No se cambió el formato SQLite ni se enviaron preferencias a servicios externos.

## Evidencia reproducible

Pruebas JVM del núcleo (`scripts/test_ai_core.sh`) y tests `LeoResearchSynthesisTest`,
`LeoInferenceProtocolTest`, `LeoInferenceSettingsTest`, `NikoMemoryArchiveTest` en el workflow
Android. Casos: citas falsas, referencias fuera del conjunto, texto web que intenta inventar
fuentes, snippets, cancelación, fallo de generación, consentimiento separado y correcciones.

PENDIENTE DE VALIDACIÓN CON PROVEEDOR: síntesis generativa real, calidad y latencia.
PENDIENTE DE VALIDACIÓN FÍSICA: voz/micrófono/avatar, batería, memoria y temperatura.
Continúan pendientes el modelo emocional contextual avanzado y la actualización firmada de
modelos con versiones seleccionables. Esta integración no representa un «100/100» global.
