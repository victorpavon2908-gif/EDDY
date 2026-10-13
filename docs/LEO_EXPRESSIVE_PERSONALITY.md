# Expresiones y humor — 2026-10-13

IMPLEMENTADO: `LeoExpression` comparte vocabulario entre el servicio y la cara Android: alegría, risa, burla/sarcasmo, asombro/sorpresa, miedo, enojo, tristeza y neutral. Acepta órdenes como «LEO, hacé una expresión de asombro» o «poné cara de miedo». Las expresiones sin implementar se reconocen como no disponibles, sin fingir mostrarlas. Las órdenes son locales y no pasan por búsqueda web.

El miedo, enojo y tristeza usan cejas y boca propias. Los ojos abiertos mantienen el arco superior, base horizontal y ausencia de pupilas pedidos por el usuario. Las expresiones conservan la caducidad y suspensión por escucha/pensamiento/background existentes. Durante TTS se mantiene el movimiento de boca de habla.

`LeoPlayfulDialogue` aporta variaciones escritas de bromas suaves cuando se solicita «burlate de mí» o «hacé una broma sobre mí». Se refieren a la interacción, sin inventar datos personales. «Respondeme de malas» activa un personaje gruñón teatral para saludos; «respondeme de buenas» lo restablece y «sin bromas» lo pone serio. Es estado de sesión, no emoción persistida ni conciencia. Son respuestas de repertorio limitado, no humor generativo abierto ni personalidad humana.

El reconocedor principal notifica un callback únicamente al recibir el resultado FINAL de una activación sola. El servicio responde con un saludo y mantiene continuación de escucha. No saluda sobre una orden pegada a LEO ni ante una interrupción. El motor local conserva su ruta de confirmación y varía solamente el «Ajá» existente. No cambia VAD, ASR, modelo de wake word, pre-roll ni micrófono. La voz usa el TTS existente: no se añaden risas grabadas ni actuación vocal profesional.

PRUEBAS: LeoExpressionTest (órdenes, límites, variación, modo serio), LeoFaceMoodTest (selección de caras), LeoWakeAcknowledgementTest (activación sola frente a orden o interrupción), más regresiones del núcleo y CI Android.

PENDIENTE DE VALIDACIÓN FÍSICA: «LEO» solo seguido de instrucción, «LEO abrí Spotify» sin saludo superpuesto, interrupción durante voz, ocho expresiones, rasgos visibles al hablar y ciclo de vida HONOR. No se certifica latencia ni duplex acústico mediante estos tests.
