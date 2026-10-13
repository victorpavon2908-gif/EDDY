# Identidad visual LEO — 2026-10-12

Se sustituye el robot 3D por la imagen `Eddy.webp` proporcionada por el usuario. Las copias Android (`drawable-nodpi/leo_emblem.webp`) y web (`public/leo_emblem.webp`) conservan exactamente los bytes de la referencia, sin redibujo ni recorte en esos archivos. Desde la incorporación de expresiones, el personaje principal Android se dibuja con Canvas según esa silueta; los iconos y la web conservan la imagen estática. El nombre público sigue siendo LEO.

Aplicado a la pantalla principal, Música (usa NikoHero), burbuja, icono de aplicación, bienvenida web y favicon. El icono de notificación es una adaptación vectorial monocroma porque Android usa la silueta alfa de ese recurso.

Las órdenes de saludo, salto, baile y giro siguen moviendo el emblema completo en 2D. No dependen de modelos 3D; la cara Android tiene animación independiente. Los gestos terminan, se cancelan al escuchar/pensar, al desactivar o pasar a segundo plano, y respetan movimiento reducido. En reposo Android solo actualiza el parpadeo; el movimiento continuo se limita al habla, la risa y los gestos. Se conservan los indicadores de estado existentes.

Se retiran las dos copias GLB, los pósteres, el renderizador SceneView, el componente Three.js y las dependencias exclusivas. `RobotMotionBus` y el director de gestos conservan nombres internos para compatibilidad con los comandos. La documentación anterior del robot permanece como registro histórico.

## Validación

Comprobar identidad binaria de ambas copias respecto al archivo suministrado, lint/compilación web, regresiones existentes y workflow Android con pruebas y Lint. Los resultados efectivos están asociados al commit en GitHub Actions; no confundir compilación con validación física.

**PENDIENTE DE VALIDACIÓN FÍSICA:** pantalla principal, Música, burbuja, icono de launcher en HONOR, notificaciones, tamaño de pantalla, gesto interrumpido por escucha y actualización conservando datos. Sin nuevas mediciones de rendimiento físico. No se genera APK en esta entrega.

Validación local ejecutada: `npm run lint`, 36 pruebas de `npm test` y `npm run build` aprobados. SHA-256 idéntico de la referencia y ambas copias: `ecf5bddbb0b2619babece2f0e2992345fcae59ab6fd9f45e746a86510b2a762b`.

## Vista Android a pantalla completa

LEO ocupa el contenedor completo sobre fondo blanco, sin la tarjeta oscura, el límite de 220 dp ni márgenes internos del emblema. La proporción de la imagen se conserva: no se estira ni recorta para llenar una pantalla alargada. Ajustes, estado y acceso a herramientas quedan superpuestos de forma compacta. «Conversación y controles» abre un panel desplazable con respuestas completas, fuentes, acciones rápidas y controles de voz existentes. Cerrar el panel devuelve la vista completa. Música conserva su disposición previa.

El aviso persistente de conocimiento offline listo/opcional deja de tapar el inicio; sus controles siguen en Ajustes. Descargas, verificación, instalación y errores siguen mostrando su aviso. Revisión visual en teléfono: PENDIENTE DE VALIDACIÓN FÍSICA.

## Expresiones Android — 2026-10-13

IMPLEMENTADO: `LeoAnimatedFace` mantiene la silueta negra sobre blanco a pantalla completa, con ojos huecos, cejas y boca independientes. Parpadea; los ojos abiertos conservan siempre el arco superior y la base horizontal recta de la referencia, con el mismo tamaño, sin puntos ni pupilas interiores, por indicación del usuario. Mueve la boca durante el estado real SPEAKING y deja ese movimiento al salir del estado. Es un ritmo visual, **no sincronización por fonemas ni amplitud de audio**. No cambia el reconocedor ni el TTS.

`LeoFaceMood` selecciona sonrisa, risa, mueca sarcástica, sorpresa o expresión seria a partir de órdenes explícitas y señales limitadas al principio de la respuesta. Ejemplos: «LEO, reíte», «sé sarcástico», «sorprendete». No reconoce emociones generales ni añade risa audible. Evita la mueca al detectar términos sensibles o pedidos de no bromear. Estas reglas son conservadoras, no una garantía de comprensión de todos los contextos.

Las expresiones expiran tras ocho segundos sin cambio de texto, se suprimen al escuchar/pensar y los bucles se cancelan al desactivar o dejar RESUMED. Se respeta la desactivación de animadores del sistema al iniciar cada efecto. La personalidad conversacional existente se conserva. La web y los iconos siguen usando la imagen estática.

PRUEBAS AUTOMATIZADAS: `LeoFaceMoodTest` cubre órdenes con tildes, identidad completa, sensibilidad, rechazo de bromas, citas y señales de respuesta. Los resultados de compilación, pruebas y Lint se consultan en GitHub Actions del commit; no certifican el aspecto físico.

**PENDIENTE DE VALIDACIÓN FÍSICA:** comprobar parpadeo y cada expresión, boca durante TTS, cierre al interrumpir, escucha, bloqueo/desbloqueo, regreso desde segundo plano y animaciones del sistema desactivadas. Verificar el parecido con la referencia en HONOR y distintos tamaños. Sin mediciones nuevas de FPS, batería o latencia. No se genera APK.
