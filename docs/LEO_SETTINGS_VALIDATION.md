# Configuración de LEO 0.14.1: revisión y evidencia

Revisión del 3 de noviembre de 2026. La compilación de entrega se identifica como 0.14.1 (código 41) en Configuración. Esta página distingue regresiones automatizadas, peticiones HTTP reales y aceptación física pendiente.

## Correcciones concretas

- Gemini: la prueba real detectó HTTP 400 cuando Flash 3.8 recibe `minimal`. El perfil de cada modelo está separado; Flash 3.8 usa `low` en Normal/Super y Flash-Lite 3.1 permite `minimal`. Flash-Lite 3.1 es la propuesta rápida. Google usa REST nativo `generateContent` y cabecera `x-goog-api-key`; se conservan los endpoints anteriores de preferencias al actualizar. Los proveedores externos siguen usando chat/completions.
- El cliente limita trabajadores y cola; una lectura o cierre bloqueado no retiene la cancelación del llamador. Las respuestas HTTP 502/503/504 permiten un reintento si quedan al menos cinco segundos dentro del plazo original. No se reintenta un rechazo por clave, permisos o cuota, ni se cambia de clave para eludir límites. No se usan cachés de respuestas del cliente.
- La prueba de conexión exige «LEO conectado» y una respuesta terminada, sin llamadas a herramientas. Una prueba fallida conserva el proveedor anterior. Los errores muestran una clasificación local y, cuando existe, el código HTTP; nunca el cuerpo del error del proveedor.
- La clave se puede conservar al cambiar permisos/modelo del mismo endpoint. El cambio a otro endpoint no la reutiliza. La interfaz muestra el modelo realmente guardado como activo. La prueba de cifrado verifica lectura posterior, ausencia de texto plano en preferencias y fallo al utilizar otra clave de cifrado. La prueba usa una clave ficticia y AES real; no certifica un Keystore físico.
- El botón de búsqueda prueba el buscador de producción y solo anuncia lectura completada con una fuente y un pasaje leído. Puede mostrar diagnóstico por host. Enlaces directos reintentan una vez una falla de conexión; una respuesta HTTP rechazada no se repite.
- «Reiniciar voz» tenía una ruta de encendido que no reiniciaba un motor ya activo. Ahora hay una acción explícita de reinicio: invalida callbacks antiguos, cancela preparación previa, espera el cierre y vuelve a iniciar. La aceptación del micrófono físico sigue pendiente.
- «Consultar sensores» no usa valores de ejemplo. Espera brevemente eventos reales y muestra datos ausentes como no disponibles. La batería respeta nivel/escala reales.
- El restablecimiento de burbuja comprueba el guardado y distingue solicitud enviada de burbuja apagada o servicio inaccesible. No afirma un movimiento visual sin comprobarlo.

## Cobertura de las pruebas

| Control | Comprobación automatizada | Comprobación física pendiente |
| --- | --- | --- |
| Modos | Persistencia, cambios por voz reflejados en UI y presupuestos por perfil | Expresión y voz percibidas en cada teléfono |
| Internet | Transporte, límites, fuentes pertinentes, citas y reintento; UI de falla, diagnóstico y nueva prueba exitosa | Repetir en Wi-Fi y datos del usuario |
| Gemini | Contrato nativo, perfiles admitidos, prueba antes de activar, preservación del proveedor anterior, cambio de permisos sin reingresar clave, borrado y cifrado | Keystore y conectividad del teléfono |
| Voz y propietario | Políticas, recuperación, cancelación y persistencia de perfil | Hablar, reiniciar, interrupciones, ruido y Bluetooth |
| Modelos/enciclopedia | Integridad, reanudación, activación/recuperación, validación del índice y revocación | Descargas completas y rendimiento en el teléfono |
| Memoria semántica | Integridad y regresiones del modelo entrenado en CI | Instalación y consulta de notas del usuario |
| Privacidad | Consentimientos separados, historial acotado, aislamiento de investigación y borrado de memoria | Confirmar las opciones escogidas en la instalación del usuario |
| Sensores | Escala de batería y rechazo de datos faltantes/inválidos | Lecturas de hardware disponibles en el teléfono |
| Burbuja/accesibilidad | Rutas explícitas y manejo de fallos; permisos administrados por Android | Conceder permisos y verificar comportamiento visual |

## Peticiones reales y límites

Se utilizó temporalmente una credencial aportada por el usuario. No se incluyó en archivos de código, pruebas, documentación, URL, repositorio ni artefactos. Google respondió HTTP 200 al listado de modelos y generó correctamente una respuesta aritmética con Flash-Lite 3.1. También se observaron HTTP 503 y timeouts durante la sesión: no se consideran éxitos ni garantizan disponibilidad sostenida.

El cliente de producción con REST nativo obtuvo la respuesta de comprobación correcta en Normal y, en otra ejecución, en Super Leo. En la última ejecución Normal respondió correctamente en aproximadamente 5,6 segundos y la continuidad recuperó el nombre de referencia de un intercambio anterior; Super Leo agotó el plazo. Otras ejecuciones incluyeron timeouts de historial y HTTP 503. Estos resultados intermitentes se conservan como tales; no se certifica disponibilidad sostenida. La conversión de roles se comprueba además con fixtures. No se promete un tiempo fijo ni que todos los servicios externos respondan siempre.

La consulta real de energía solar y la lectura directa de su página de Wikipedia devolvieron texto y fuente tras el ajuste de reintento. Una lectura directa anterior agotó el tiempo y se registró como fallo. Las pruebas se ejecutaron desde el entorno de desarrollo, no desde la red del teléfono.

El resultado de la CI del commit de entrega es la evidencia de compilación Android, regresiones, componentes nativos y Lint. No se construye ni publica APK automáticamente.
