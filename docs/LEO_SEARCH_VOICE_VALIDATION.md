# LEO: búsqueda y preferencia de voz

## Regresiones automatizadas

`LeoNativeWebSearchTest` cubre petición natural, conservación del tema y fechas,
rechazo de coincidencias parciales, login de Outlook, redirecciones de login y páginas
ajenas, fallo de conexión, búsqueda vacía, asociación correcta entre cada enlace y
su extracto DuckDuckGo, y una búsqueda completa con transporte de prueba.

`WebQueryRouterTest` y `ConversationCoordinatorTest` comprueban prioridad de la búsqueda
explícita y distinción entre petición de búsqueda y noticias actuales.

`NearFieldAudioFocusTest` comprueba que no se amplifican voces débiles, se recupera la
voz cercana sin saturarla y se conservan finales de palabras sin cortes bruscos.
`OwnerVoicePolicyTest` comprueba coincidencia, cambio de hablante entre ventanas y
rechazo de vectores vacíos, inválidos y ambiguos. No utiliza audio humano real.
`NikoVoiceProfileTest` (Robolectric) comprueba registro explícito, consistencia entre
muestras, cancelación, persistencia, borrado y rechazo de perfiles antiguos automáticos.

Validación completa: `./gradlew :app:testDebugUnitTest :app:lintDebug`.
La CI de commits/PR no construye ni publica APK; la compilación requiere solicitud expresa.

## Prueba pendiente en teléfono real

1. Registrar cuatro frases estando solo, a distancia normal del teléfono. Cancelar
   un segundo registro y comprobar que el perfil confirmado sigue funcionando.
2. Pedir «Leo, buscame información sobre Rubén Darío en 1916» y «Leo, buscá noticias
   de Nicaragua hoy»: leer respuesta y abrir las fuentes, comprobando tema y fechas.
3. Repetir sin red: debe indicar que no obtuvo fuentes; no leer Outlook ni datos antiguos.
4. Hablar solo; después, con otra persona más lejos hablando al mismo tiempo.
   Medir frases correctas y rechazos. No considerar aprobada la separación de voces
   basándose solamente en las pruebas de vectores.
5. Pedir a la otra persona una orden durante el seguimiento y durante el TTS.
   Con «Priorizar mi voz» activo, una voz que no coincida no debe ejecutar la orden.
6. Probar voz baja, palabras cortas, auriculares y pantalla bloqueada. Si se rechaza
   demasiado al propietario, repetir el registro o desactivar la preferencia en Ajustes.
7. Confirmar que el registro no dispara búsquedas, llamadas ni cambios del teléfono.

## Referencias de implementación

- [Android MicrophoneDirection](https://developer.android.com/reference/android/media/MicrophoneDirection): orientación y zoom son preferencias cuya aceptación depende del equipo.
- [Sherpa speaker identification](https://k2-fsa.github.io/sherpa/onnx/speaker-identification/index.html): embeddings para comparar hablantes, no separación de voces superpuestas.

La selección web es extractiva y se basa en relevancia léxica. Los proveedores públicos
pueden bloquear peticiones o cambiar HTML. No hay garantía de disponibilidad ni de
veracidad por contar enlaces; se evita afirmar corroboración sin haberla realizado.


## LEO 0.10.3: interrupciones, latencia y acciones

Pruebas añadidas: controles completos frente a palabras citadas/negadas; arbitraje entre
primer audio, timeout y cancelación; interrupción del productor antes del reproductor;
variantes de WhatsApp, dictado sin destinatario y números dentro del mensaje; seguimiento
contextual con vencimiento y prioridad SMS; intents para Business y notificación Android;
reparto de evidencia, cancelación de búsqueda y rechazo de citas inventadas en la síntesis.

En un teléfono (pendiente, las pruebas JVM no miden audio real):

1. Pedir una explicación web larga y decir «Leo, pará» durante la investigación, durante
   la espera de síntesis y durante el habla. Debe callarse y no reanudar una respuesta vieja.
   Inmediatamente pedir «Leo, qué hora es»; la respuesta anterior no debe cerrar ese turno.
2. Repetir con «Leo, desactívate»: comprobar micrófono apagado, servicio detenido y ajuste
   deshabilitado incluso al volver a abrir la app. Reactivarlo manualmente para continuar.
3. Medir desde texto visible hasta primer sonido, con modelo frío y caliente. El plazo
   de 2,3 s es para cambiar al sistema, no una garantía de tiempo de audio en todo equipo.
   Probar sin voz del sistema: conserva la voz neural, sin prometer ese límite.
4. «Leo, abrime WhatsApp», con versión normal y solo Business. «Mandá un mensaje así
   Voy llegando» debe abrir selector/compositor sin inventar contacto. Probar con pantalla
   visible, bloqueada y con/sin superposición y notificaciones habilitadas.
5. Pedir un tema con y sin clave Groq. Abrir citas, comprobar exactitud, explicaciones,
   fechas y límites. El informe local debe sobrevivir a timeout, error o JSON inválido.

[Android: inicio de actividades desde segundo plano](https://developer.android.com/guide/components/activities/secure-bal)
documenta que un inicio bloqueado puede no devolver excepción; por eso se usa actividad
visible/superposición concedida o una notificación tocada por el usuario.
La síntesis opcional es generativa: las citas y límites se validan estructuralmente, pero
eso no prueba que cada interpretación del modelo sea correcta.
