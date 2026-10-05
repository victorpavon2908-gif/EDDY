# Transformaciones multimedia — LEO Android 0.13.0

LEO cambia su propia pantalla entre herramientas implementadas. No genera ni instala aplicaciones arbitrarias. Una transformación desconocida abre el catálogo disponible.

## Comandos
- «LEO, conviértete en una cámara».
- «LEO, conviértete en un grabador de video».
- «LEO, conviértete en una grabadora de audio».
- «LEO, conviértete en una calculadora».
- «LEO, conviértete en un cronómetro».
- «LEO, conviértete en un reproductor de música».
- «LEO, buscame música de Shakira».
- Botón **Transformarme** para elegir sin voz.

Por compatibilidad, el analizador conserva alias de nombres antiguos como `Eddy`; no son nombres actuales del producto. La palabra de activación acústica sigue siendo **LEO**. Las transformaciones reconocidas se ejecutan localmente, antes de la búsqueda web; no esperan una respuesta del modelo.

## Cámara y grabación
Cámara frontal/trasera, fotografía JPEG y video MP4 con audio mediante CameraX. Video HD con calidad alternativa según dispositivo. Audio AAC/M4A. Los botones inician y detienen la captura; entrar en la herramienta no inicia una grabación.

Permisos de cámara/micrófono solicitados y revisados al volver de ajustes. La escucha del asistente libera el micrófono antes de grabar y se recupera al finalizar. Durante la grabación se usa el botón Detener; la palabra de activación queda pausada. Cambiar de herramienta elimina la pantalla anterior inmediatamente para evitar dos cámaras enlazadas durante una animación.

Salir o bloquear el teléfono detiene la grabación. Video limitado a 512 MB; audio a una hora o 128 MB. Los archivos permanecen en almacenamiento privado de la aplicación y se pueden abrir o compartir mediante FileProvider. No se suben automáticamente. Exportarlos antes de desinstalar. El catálogo muestra hasta 30 capturas recientes.

## Música y personalidad
Archivos de audio elegidos por el usuario, controles de reproducción/pausa y posición, foco de audio para interrupciones. Preparación asíncrona: no inicia sola si la pantalla ya se pausó.

Búsqueda de muestras públicas en Apple/iTunes: no son canciones completas. Resultados con título, artista y enlace al servicio. Búsquedas alternativas en YouTube/Spotify abren esos servicios. No hay extracción de audio ni descarga de canciones protegidas.

Modo fiesta con baile de LEO y frases ocasionales como «¡Qué cool!» y «¡Hujuuu!». Puede desactivarse. Baja el volumen del reproductor durante las frases. Son reacciones programadas, no análisis de ritmo ni comprensión de la canción. Reproducción, voz y animación se detienen al abandonar la pantalla.

## Verificación
Pruebas unitarias de transformaciones, negaciones, búsquedas, exclusión del micrófono, cancelación y validación de URLs de muestras. CI Android ejecuta pruebas y Lint sin empaquetar APK.

**Pendiente en dispositivo real:** frontal/trasera, rotación, permisos denegados, grabación corta, pantalla bloqueada, interrupción de llamada, almacenamiento lleno, exportación, búsqueda sin conexión, foco de audio y reacción de LEO. Las pruebas de código no sustituyen esas comprobaciones ni miden batería o latencia real.

Referencias técnicas:
- https://developer.android.com/media/camera/camerax/video-capture
- https://developer.android.com/media/platform/mediarecorder
- https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/Searching.html
