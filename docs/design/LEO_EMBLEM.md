# Identidad visual LEO — 2026-10-12

Se sustituye el robot 3D por la imagen `Eddy.webp` proporcionada por el usuario. Las copias Android (`drawable-nodpi/leo_emblem.webp`) y web (`public/leo_emblem.webp`) conservan exactamente los bytes de la referencia, sin redibujo ni recorte. Se muestran con ajuste proporcional. El nombre público sigue siendo LEO.

Aplicado a la pantalla principal, Música (usa NikoHero), burbuja, icono de aplicación, bienvenida web y favicon. El icono de notificación es una adaptación vectorial monocroma porque Android usa la silueta alfa de ese recurso.

Las órdenes de saludo, salto, baile y giro siguen moviendo el emblema completo en 2D. No deforman su cara ni dependen de modelos 3D. Los gestos terminan, se cancelan al escuchar/pensar, al desactivar o pasar a segundo plano, y respetan movimiento reducido. No hay bucle de renderizado en reposo. Se conservan los indicadores de estado existentes.

Se retiran las dos copias GLB, los pósteres, el renderizador SceneView, el componente Three.js y las dependencias exclusivas. `RobotMotionBus` y el director de gestos conservan nombres internos para compatibilidad con los comandos. La documentación anterior del robot permanece como registro histórico.

## Validación

Comprobar identidad binaria de ambas copias respecto al archivo suministrado, lint/compilación web, regresiones existentes y workflow Android con pruebas y Lint. Los resultados efectivos están asociados al commit en GitHub Actions; no confundir compilación con validación física.

**PENDIENTE DE VALIDACIÓN FÍSICA:** pantalla principal, Música, burbuja, icono de launcher en HONOR, notificaciones, tamaño de pantalla, gesto interrumpido por escucha y actualización conservando datos. Sin nuevas mediciones de rendimiento físico. No se genera APK en esta entrega.

Validación local ejecutada: `npm run lint`, 36 pruebas de `npm test` y `npm run build` aprobados. SHA-256 idéntico de la referencia y ambas copias: `ecf5bddbb0b2619babece2f0e2992345fcae59ab6fd9f45e746a86510b2a762b`.

## Vista Android a pantalla completa

LEO ocupa el contenedor completo sobre fondo blanco, sin la tarjeta oscura, el límite de 220 dp ni márgenes internos del emblema. La proporción de la imagen se conserva: no se estira ni recorta para llenar una pantalla alargada. Ajustes, estado y acceso a herramientas quedan superpuestos de forma compacta. «Conversación y controles» abre un panel desplazable con respuestas completas, fuentes, acciones rápidas y controles de voz existentes. Cerrar el panel devuelve la vista completa. Música conserva su disposición previa.

El aviso persistente de conocimiento offline listo/opcional deja de tapar el inicio; sus controles siguen en Ajustes. Descargas, verificación, instalación y errores siguen mostrando su aviso. Revisión visual en teléfono: PENDIENTE DE VALIDACIÓN FÍSICA.
