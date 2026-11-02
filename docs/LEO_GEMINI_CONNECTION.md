# Conexión personal de Gemini en LEO

Revisado el 2 de noviembre de 2026.

La sección «Inteligencia con Gemini» de Ajustes abre la página oficial de claves de AI Studio en el navegador. El usuario inicia sesión con Google allí y copia su propia clave una vez. La app no obtiene una clave a partir de un ID token de Google ni lee contraseñas, cookies o pantallas de otras apps.

La conexión usa la API oficial compatible con chat/completions, no la interfaz de chat de gemini.google.com. El modelo propuesto es `gemini-3.8-flash`, editable en Avanzado. La documentación consultada ofrece nivel gratuito para este modelo, sujeto a elegibilidad y cuotas. No se comprueba el estado de facturación desde LEO ni se promete costo cero si el proyecto tiene facturación. No se incorporan claves al repositorio.

La activación prueba una respuesta antes de persistir. Los errores HTTP se convierten en diagnósticos locales; no se muestran cuerpos de error del proveedor ni cabeceras. La credencial se cifra con Android Keystore. La preferencia explícita de proveedor principal permite priorizarlo para conversación; las rutas de memoria personal, órdenes del teléfono, búsqueda actual y peticiones «solo local» conservan sus restricciones. El historial y los extractos web tienen permisos separados. La opción de historial comparte hasta tres intercambios; los extractos web requieren citas válidas.

Gemini 3 recibe esfuerzo de razonamiento bajo para conversación, mínimo para Super Leo y medio para Investigador. Se reserva presupuesto de tokens para el texto final porque Google cuenta también tokens de pensamiento. Esto configura el esfuerzo, no demuestra una mejora de latencia o calidad en un dispositivo.

## Inicio de sesión integrado pendiente

Para la experiencia de una app pública sin copiar claves, la ruta oficial de Android es Firebase AI Logic con App Check. Hace falta un proyecto Firebase del propietario de LEO, registrar `com.eddy.assistant`, configuración Android y atestación de la app. Google Sign-In puede identificar al usuario, pero no le entrega a la app su clave personal de AI Studio ni garantiza una cuota gratuita independiente por persona. Si se usa el proyecto de LEO, sus cuotas y eventual facturación corresponden a ese proyecto. El repositorio no incluye esa configuración; no se añadió un botón de inicio de sesión que simule autorizar Gemini.

OAuth también está documentado para Gemini, con configuración de proyecto, clientes y autorizaciones; no equivale al acceso básico de perfil de Google. No se solicitó acceso administrativo a proyectos de usuarios para extraer o crear sus claves.

## Evidencia y límites

Las pruebas de transporte usan respuestas simuladas para éxito, errores, cancelación y conservación del proveedor anterior. Las pruebas de Ajustes comprueban entrada visible, enlace oficial y activación bloqueada sin credencial. La conexión real de una cuenta se confirma en el teléfono con su clave; no se afirmó una prueba en vivo sin disponer de ella.

## Fuentes oficiales

- https://ai.google.dev/gemini-api/docs/api-key
- https://ai.google.dev/gemini-api/docs/pricing
- https://ai.google.dev/gemini-api/docs/billing
- https://ai.google.dev/gemini-api/docs/openai
- https://ai.google.dev/gemini-api/docs/oauth
- https://developer.android.com/ai/gemini/developer-api
- https://firebase.google.com/docs/ai-logic/pricing
