# Guía de EDDY / LEO para nuevos usuarios — Android

Guía de uso desde la primera apertura. Describe las funciones presentes en el código; su funcionamiento concreto depende de permisos, modelo instalado, conexión y compatibilidad del teléfono.

## 1. Primer inicio
1. Abrí LEO en un teléfono con Android 12 o posterior, conexión estable y espacio libre. La primera apertura descarga y verifica los módulos necesarios; esperá a que finalice. Si falla, usá Reintentar. Concedé **micrófono** para conversar y la detección de la palabra **«LEO»**. Sin permiso, la escucha no funciona.
2. La guía completa está en **Inicio → Guía** y **Configuración → Explorar la guía**, incluso sin conexión. Podés buscar por palabra y copiar el texto. Entrá al icono de **Ajustes** (arriba a la derecha) y activá **Escuchar la palabra «LEO»**. El botón de reinicio de micrófono puede ayudar si no te oye.
3. **Mi voz** permite registrar tu voz y activar el modo de propietario; el comportamiento varía con el entorno y nunca reemplaza la seguridad de Android.
4. **Pantalla / accesibilidad** y **burbuja flotante** necesitan permisos separados y opcionales. Revisá lo que concedés.
5. Volvé al inicio. Decí «LEO» y formulá una pregunta normal. Tras responder puede mantenerse una ventana de conversación breve si está activada.
6. Usá **Conversación y controles** para leer la respuesta completa y sus enlaces, o **Herramientas** para cambiar de modo sin hablar.

## Ajustes: dónde encontrar cada opción
Los cambios de Android se guardan al instante. Tocá una categoría para abrir o cerrar sus controles. Cerrar una sección conserva su estado y no interrumpe sus tareas mientras permanezcas en Ajustes.
- **Inteligencia con Gemini**: conexión guiada con Google AI Studio, prueba antes de activar y elección del modelo principal.
- **Voz y conversación**: encender o pausar escucha, conversación espontánea, reinicio y diagnóstico.
- **Mi voz**: instalar el identificador, registrar cuatro frases y limitar las respuestas a tu voz. No es un bloqueo de seguridad.
- **Precisión de voz**: instalar o comprobar el segundo reconocedor local.
- **Internet y fuentes**: búsqueda automática, prioridad local y botón **Probar búsqueda**. La categoría aparece abierta.
- **Estilo y aprendizaje**: personalidad y aprendizaje de tus correcciones.
- **Permisos y teléfono**: burbuja, accesibilidad y lectura de sensores. Android administra estos permisos; concedelos solo para las funciones que uses.
- **Modelos y descargas**: modelo local, enciclopedia y memoria semántica. Revisá el estado de cada uno; descargar uno no instala los demás. Mantené Ajustes abierto durante las descargas que se ejecutan en esa pantalla.
- **Privacidad**: explicación de consultas web y borrado de memoria con confirmación. No hay botón de deshacer.

## 2. Hablar y conversar
- «LEO, ¿qué podés hacer?», «LEO, explicame qué es la energía eólica», «LEO, ayudame con matemáticas».
- **Iniciar conversaciones**: permite intervenciones breves del asistente. Es opcional en Ajustes.
- **Personalidad**: elegí el estilo disponible en Ajustes.
- **Preferir respuestas locales**: intenta resolver con capacidades locales. No equivale a disponer de todos los conocimientos del mundo.

## Modos de LEO
Desde el botón **Modos** del inicio o **Configuración → Modos de LEO** podés cambiar de perfil. También funciona por voz, con la escucha habilitada:

| Decí | Aspecto | Qué cambia |
| --- | --- | --- |
| «Leo, activa modo Super Leo» | Rojo, ojos más firmes y boca seria | Respuestas breves, voz ágil, una consulta temática, hasta tres fuentes y menor presupuesto de generación. Devuelve evidencia sin esperar la reescritura opcional de un modelo. |
| «Leo, activa modo investigador» | Azul y expresión concentrada | Más tiempo de búsqueda, hasta seis fuentes y explicaciones amplias. |
| «Leo, activa modo creativo» | Morado y expresión habitual | Propone ideas y ejemplos cuando usás un modelo configurado; la voz es más expresiva. |
| «Leo, activa modo normal» | Aspecto original | Recupera el equilibrio habitual. También podés decir «desactiva modo Super Leo». |

La selección se conserva al cerrar la app. Cambiar de modo no activa descargas, modelos remotos, micrófono ni permisos. Super Leo reduce trabajo y espera; la rapidez final depende del teléfono, la red y los modelos instalados. Las consultas actuales siguen necesitando fuentes; el modo creativo no convierte una idea en un hecho. Los cambios afectan las respuestas siguientes. Una indicación explícita como «habla más lento» tiene prioridad sobre la velocidad del modo.

## 3. Internet: búsquedas, noticias y fuentes
- Activá **Buscar en Internet** en **Configuración → Internet y fuentes**. **Probar búsqueda** realiza una consulta real y muestra si encontró fuentes y pudo leer páginas; no cambia tus preferencias.
- Si un proveedor tarda demasiado, se cancela su petición sin esperar a una conexión bloqueada y se conservan los resultados de otros. Un fallo de red sigue pudiendo impedir la consulta.
- Después de **Probar búsqueda**, tocá **Ver diagnóstico de la prueba**: distingue contenido recibido, acceso rechazado, límite de consultas, error de conexión segura y tiempo agotado. Recibir contenido no demuestra que sea pertinente ni verdadero. Si falla, compará la prueba en Wi-Fi y datos móviles; el diagnóstico ayuda a identificar qué proveedores responden en esa red.
- Las respuestas con páginas leídas usan esas páginas como evidencia. Si solo hay extractos del buscador, LEO lo indica y no los trata como páginas leídas.
- Para forzar la búsqueda: «LEO, buscá en internet las últimas noticias de Nicaragua»; «LEO, investigá la energía solar»; «LEO, averiguá el precio actual de Bitcoin»; «LEO, consultá esta página https://...».
- Abrí **Conversación y controles → Leer respuesta completa** para consultar fuentes y enlaces.
- El buscador prueba varios proveedores públicos, clasifica coincidencias y extrae texto de las páginas cuando responden. Un artículo que no abrió **no está verificado**: puede mostrar extractos y enlaces, identificados como tales.
- Las consultas de clima distinguen condiciones actuales y pronósticos de mañana o pasado mañana cuando el proveedor los incluye; muestran la fecha y probabilidades disponibles. Una consulta sobre duración o temperatura del procesador no se trata como clima.
- Requiere conexión. Algunos sitios impiden lectura automatizada, pueden bloquearse o tardar. Reintentá con otra consulta o pasá un enlace público. No hay garantía de acceso a cualquier sitio ni de veracidad de todo resultado.
- Se consultan páginas públicas: evitá enviar contraseñas, mensajes privados u otros datos sensibles al buscador.

- Si hay páginas leídas en español, se prefieren para las consultas generales en español. El código, los enlaces explícitos y las peticiones en inglés conservan sus fuentes originales.

## 4. Enciclopedia offline
- En **Configuración → Modelos y descargas → Enciclopedia**, elegí descargar el paquete opcional de cerca de 500 MB. Necesitás conexión inicial y espacio libre para descargar y verificar.
- Las etapas incluyen descargar, verificar integridad, instalar y activar. Al terminar debe indicar **instalado**.
- Este índice de Wikipedia y lecciones locales sirve para preguntas de conocimiento general **sin Internet**; no conoce automáticamente noticias y precios actuales.
- Si hay error, comprobá la versión instalada, Wi‑Fi, espacio, y usá el botón de comprobar/actualizar. No borres datos personales ni reinstales sin antes guardar lo que necesités.

Un archivo de modelo encontrado al abrir configuración se verifica antes de recuperarlo y permanece desactivado si esa era tu elección. La activación exige memoria física y memoria disponible suficientes. La instalación también exige una respuesta de prueba correcta antes de anunciar que el modelo está activado. Si falla o se agota el tiempo, el archivo verificado se conserva para reintentar y se informa el problema.

## 5. Herramientas y transformaciones
Entrá a **Herramientas** o pedí «LEO, conviértete en...». Para regresar, usá Atrás o «LEO, volvé al inicio».
- **Calculadora**: operaciones y funciones matemáticas soportadas; «convertite en calculadora».
- **Conversor**: unidades como distancia, masa, temperatura, volumen, datos, tiempo y velocidad.
- **Reloj**: consulta la hora en la pantalla de la herramienta.
- **Cronómetro**: medición de tiempo transcurrido.
- **Temporizador**: cuenta regresiva visual; no garantiza notificación con la app cerrada.
- **Notas**: escribí notas; guardado local automático con indicadores.
- **Cámara**: fotos con cámara frontal/trasera; requiere permiso.
- **Video**: grabación local MP4, requiere permisos; detiene la escucha de LEO mientras se graba.
- **Grabadora de audio**: graba audio local y detiene temporalmente el micrófono de LEO.
- **Música**: reproduce archivos locales, consulta muestras de Apple/iTunes y abre enlaces de YouTube/Spotify. Las muestras no son canciones completas. Modo fiesta con animación opcional.
- **Diagnóstico de voz**: pruebas de reconocimiento y escucha.
- **Herramientas generadas**: funcionalidad limitada a plantillas y operaciones permitidas; no es un generador ilimitado de apps instalables.
Los archivos multimedia son privados de la app; exportalos antes de desinstalar.

## 6. Memoria, aprendizaje y privacidad
- **Aprendizaje continuo**: puede conservar preferencias/correcciones según los módulos activos; no quiere decir entrenamiento universal ni infalible.
- La **memoria personal** puede guardarse en el teléfono. Usá la opción de borrar memoria solo cuando realmente quieras eliminarla; no borra necesariamente modelos, paquetes de voz ni otros permisos.
- El **modelo local opcional** (si aparece en Ajustes) necesita instalación y recursos de memoria; el cerebro de consulta SQLite, MicroGPT y otros modelos son componentes distintos.

## 7. Si algo no funciona
- **No oye «LEO»**: activá escucha, concedé micrófono, revisá estado del servicio y reiniciá escucha desde Ajustes.
- **No busca en internet**: verificá conexión, activá investigación web, formulá «LEO, buscá en internet...», abrí la respuesta completa y revisá fuentes o mensaje de error.
- **No descarga la enciclopedia**: verificá permisos/red/almacenamiento, reintentá desde Ajustes y comprobá que la instalación marque «lista».
- **No graba foto/audio/video**: concedé permisos correspondientes y asegurate de que otra app no esté utilizando cámara/micrófono.
- **Respuesta desactualizada**: solicitá búsqueda web explícita; las respuestas offline no son datos en tiempo real.

## 8. Límites y validación
Los módulos se identificaron en el código fuente del proyecto. La descarga completa de ~500 MB, los buscadores y los módulos multimedia requieren pruebas en Android real para dar por confirmado su funcionamiento en cada dispositivo. LEO no garantiza infalibilidad, autonomía irrestricta ni acceso a contenido privado de terceros.

Referencia: README.md, docs/POLYMORPHIC_TOOLS.md, docs/SEARCH_RELIABILITY.md y clases Android en el repositorio EDDY.

### Borrar memoria desde configuración o por voz
Ambas rutas borran recuerdos, historial, acciones y clasificador aprendidos, registro de evolución y estado personal. Si el asistente está activo, se interrumpe su respuesta anterior y se vacía el contexto de sesión. Los modelos descargados, los modos y las preferencias de conexión se conservan. Si una parte del borrado falla, se informa para poder reintentar.

### Recuperación y conservación de datos
La voz del teléfono intenta recuperarse automáticamente ante errores o falta de respuesta inicial, con reintentos limitados. No vuelve a reproducir la respuesta anterior. Si sigue indisponible, revisá la voz española instalada y volvé a intentarlo.

Los documentos, reglas y episodios del núcleo personal se guardan para la siguiente sesión. Si el archivo no puede leerse, se conserva para recuperación y se informa el fallo. El borrado de memoria elimina también ese estado.

La recuperación de una instalación offline incompleta comprueba tamaño, hash de la publicación e índice de búsqueda antes de activarla. Los tiempos de búsqueda y generación comparten el plazo de cada petición; Super Leo utiliza límites menores. Estos límites no son mediciones de velocidad en tu teléfono.

## Conectar Gemini con tu cuenta
1. Abrí **Configuración → Inteligencia con Gemini** y tocá **ABRIR GOOGLE AI STUDIO**. Entrá con tu cuenta en la página oficial de Google y creá o elegí una clave de Gemini. LEO no lee tu contraseña ni copia claves desde otras aplicaciones.
2. Copiá la clave, regresá a LEO y pegala en el campo indicado. Autorizá enviar preguntas y hacer una prueba.
3. Elegí si Gemini será el modelo principal de conversación. Esta elección tiene prioridad sobre «Preferir respuestas locales». Las órdenes del teléfono y las funciones personales siguen su ruta habitual; «solo local» evita el envío al modelo.
4. Para continuidad, podés autorizar por separado hasta tres intercambios recientes. El permiso de resumir extractos web también es independiente; las citas deben superar la validación de LEO.
5. Elegí **Rápido · Gemini Flash-Lite** para conversación ágil o **Más capacidad · Gemini Flash**. Tocá **COMPROBAR Y ACTIVAR**. Si falla la prueba, no se reemplaza la conexión anterior. Una clave inválida, falta de acceso al modelo o cuota agotada muestran un mensaje para reintentar.

La clave queda cifrada con Android Keystore. En una conexión guardada podés dejar el campo vacío para conservarla al cambiar permisos o modelo; solo se reutiliza con el mismo endpoint. **PROBAR CONEXIÓN GUARDADA** comprueba la conexión existente sin volver a escribirla. **DESCONECTAR Y BORRAR CLAVE** elimina la credencial del teléfono; podés revocarla además en AI Studio.

Google ofrece un nivel gratuito limitado para proyectos y modelos elegibles; usar la app Gemini gratis no equivale a una API ilimitada. Comprobá **Ver mi uso** y **Cuotas y precios**. Si el proyecto tiene facturación, las llamadas pueden costar dinero. En el nivel gratuito, Google puede usar preguntas y respuestas para mejorar sus productos. LEO no activa facturación.

El inicio de sesión dentro de LEO sin pegar claves requiere otra integración con un proyecto Firebase de LEO y App Check; no está configurada en esta versión. La conexión disponible usa la clave personal de cada usuario.

### Comprobaciones de Configuración en 0.14.1
- **Probar búsqueda** ejecuta una consulta real. Solo anuncia lectura completada si recibió una fuente y un pasaje leído; de lo contrario muestra el fallo o los enlaces parciales. **Ver diagnóstico de la prueba** permite distinguir conexión, bloqueos y tiempos de espera. Una lectura directa puede reintentar una vez un fallo de conexión, sin repetir un rechazo HTTP.
- **Gemini** utiliza su API nativa. Flash-Lite es la opción rápida propuesta; cada modelo recibe únicamente parámetros de razonamiento admitidos. La prueba exige la respuesta «LEO conectado» antes de guardar y activar. Un error no reemplaza la conexión anterior. Puede reintentar una vez errores temporales 502/503/504 si queda tiempo; los errores de cuota o permisos no se reintentan automáticamente.
- **Reiniciar voz** cierra la sesión anterior y solicita un nuevo inicio. Los mensajes posteriores del motor indican si efectivamente quedó listo o si Android impide usar el micrófono.
- **Consultar sensores** espera una lectura breve. Un dato ausente se muestra como «sin lectura» o «no disponible»; no se completa con números de ejemplo.
- **Restablecer burbuja** informa si guardó la posición y si envió la solicitud al servicio; no afirma haber movido una burbuja que está apagada.
- El número de versión aparece al final de Configuración para identificar la compilación instalada.
