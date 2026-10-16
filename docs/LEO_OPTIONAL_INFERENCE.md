# Inferencia opcional para LEO — 2026-10-16

## Implementado

El núcleo puede solicitar generación real a un servidor configurado por el propietario.
`LeoInferenceClient` usa el contrato JSON de `POST /chat/completions`: mensajes system/user,
modelo indicado, respuesta de texto no streaming. Es un cliente de inferencia, **no un
modelo incluido ni una suscripción**. No requiere Groq ni agrega proveedores obligatorios.

Referencia primaria de interoperabilidad: https://github.com/ollama/ollama/blob/main/docs/api/openai-compatibility.mdx
No se descargaron modelos externos ni se asumieron sus licencias. El propietario debe
instalar/contratar un modelo apropiado en su servidor; LEO no promete cobertura de todos
los lenguajes ni verificación de código generado.

## Uso real

En Ajustes, «MODELO DE RAZONAMIENTO Y CÓDIGO»: ingresar la URL HTTPS completa, nombre
exacto del modelo, clave si corresponde y autorización de envío. Guardar y usar
«PROBAR CONEXIÓN»: envía una pregunta inocua y muestra la respuesta real recibida, o
informa que no hubo respuesta válida. No se simula conectividad.

Se envía **solo la pregunta actual**. Puede contener datos personales o código; esto
se informa antes de habilitarlo. No se adjuntan recuerdos, historial, pantalla ni
credenciales ajenas. La clave se cifra con AES-GCM en Android Keystore. Backups de la
app ya están deshabilitados. Cambiar la URL exige nueva autorización en el formulario.
Guardar con clave vacía elimina la clave anterior; hay control para desactivar y borrar.

## Rutas y seguridad

- Aritmética, memoria, acciones y expresiones mantienen sus rutas locales.
- Pedidos de código reconocidos pueden consultar el modelo configurado; si falla, se
  conserva el fallback existente. La calculadora Python local continúa disponible.
- Las solicitudes explícitas de web y actualidad siguen usando fuentes recuperadas.
- La conversación conserva la política local/web existente; el proveedor se consulta
  como alternativa de generación cuando corresponde. «Sin Internet» impide inferencia.
- Sin configuración/consentimiento no hay llamadas al proveedor.
- Solo HTTPS; no redirecciones con credenciales. No se permite ejecutar tool_calls ni
  function_call. El código recibido es una propuesta visible, nunca se ejecuta.
- La salida no se registra como investigación verificada ni inventa fuentes adjuntas.
- Máximo 16.000 caracteres de entrada, 512 KiB de transporte, 48.000 caracteres de salida;
  respuestas truncadas/inválidas se rechazan. Dos workers y cola acotada.
- Cancelación desconecta HTTP; TurnController vuelve a validar la generación antes de
  publicar. Cerrar el servicio cierra también el cliente. Fallos no muestran cuerpos de
  error que podrían contener credenciales.

## Pruebas y límites de evidencia

`LeoInferenceProtocolTest` comprueba límites, consentimiento de red por instrucción,
mensajes enviados y rechazo de respuestas truncadas/no textuales. `LeoInferenceClientTest`
usa fixtures de transporte para HTTP, errores y cancelación: **no son pruebas de un LLM**.
`LeoNeuralCoreTest` añade selección de inferencia para código, exclusión offline y
conservación de investigación para actualidad. Los tests completos se ejecutan en el
workflow Android existente.

No se proporcionó un endpoint/modelo/credencial de producción en esta sesión. Calidad de
razonamiento, compilación del código generado por un LLM y latencia real de ese proveedor:
**PENDIENTES DE VALIDACIÓN CON PROVEEDOR CONFIGURADO**. Keystore y comportamiento de red
durante llamadas, Bluetooth o pantalla bloqueada: **PENDIENTE DE VALIDACIÓN FÍSICA**.
No se afirma entrenamiento adicional del decoder ni automodificación del APK.

La síntesis remota de investigación, el modelo emocional contextual avanzado y la
actualización firmada/rollback seleccionable de modelos siguen siendo trabajo pendiente.
Esta incorporación resuelve el acceso configurable a inferencia; no convierte por sí
sola a todos los subsistemas en capacidades verificadas de nivel profesional.
