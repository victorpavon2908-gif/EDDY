# LEO: matriz de evidencia, no comparación comercial

Escala de madurez de evidencia: 0 = ausencia comprobada; 1 = ruta real inspeccionada; 2 = comportamiento acotado probado automáticamente; 3 = escenario de teléfono reproducible con evidencia; 4 = evaluación repetida y benchmark comparativo. N/E = sin evidencia suficiente para puntuar. Los números no miden inteligencia ni calidad general. No se promedian ni se comparan con Siri/Alexa. Un test de política no certifica el producto completo.

| Área | Puntaje /4 | Qué funciona / evidencia | Qué falta / limita el puntaje | Siguiente mejora |
|---|---|---|---|---|
| Conversación | 2 | LeoAgentRuntimeTest: tema, referencias y caducidad | Resolución semántica abierta y persistencia de estado | Corpus de diálogos largos en español |
| Memoria | 2 | MemoryRevisionTest, pruebas existentes de archivo/recuperación | DistilUSE probado en seis contrastes; falta índice persistente y corpus amplio | Recuperación end-to-end en teléfono y consolidación |
| Proactividad | 2 | LeoInitiativeEngineTest: evidencia, silencio, rechazo, límites | Solo hábitos contados; no proyectos/calendario integrados | Candidatos de proyectos con consentimiento |
| Razonamiento | 1 | Rutas híbridas inspeccionadas; no benchmark de razonamiento | Modelo cloud/local no evaluado en tareas abiertas | Benchmark fijo con respuestas verificables |
| Investigación web | 2 | ResearchCitationPolicyTest y regresiones de recuperación/síntesis | Pertenencia de citas no prueba veracidad ni corroboración | Evaluación humana de afirmaciones y fuentes primarias |
| Acciones | 2 | ActionExecutorTest y skills tipados | Aceptación Android no demuestra efecto físico | Verificación por herramienta en dispositivo |
| Automatización | 2 | NikoUiAutomationAgentTest, DSL, cancelación y límites | DONE es afirmación del planificador; política léxica | Verificador de objetivo independiente |
| Offline | 2 | MicroGPT/assets, routing y fallback en suites existentes | No demuestra conversación abierta offline ni todos los proveedores ASR | Pruebas sin red en HONOR |
| Voz | 2 | 90 tests JVM de políticas/audio; callbacks dúplex en CI | Reconocedor principal escucha durante TTS; falta ensayo acústico | Medir eco e interrupciones en HONOR |
| Latencia | N/E | Instrumentación existente; sin medición física nueva | Callbacks no equivalen a primer audio audible | Capturar hitos monotónicos y video externo |
| Personalización | 2 | Preferencias/revisiones y aprendizaje adaptativo probado en suites | No evaluación longitudinal con usuario | Corpus de correcciones persistentes |
| Polimorfismo | 2 | Tests existentes de GeneratedToolSpec/router/fórmulas | No equivale a generar cualquier app ni juego | Pruebas por familia y estado guardado |
| Seguridad | 2 | Confirmación acotada, registry, política UI y citas | Faltan red team, autorización granular y verificación independiente | Auditoría de privilegios y prompt injection |
| Estabilidad | 2 | Suites locales; Android tests/Lint aprobados en 40b7c00; comprobar también SHA de entrega | Sin soak físico, CPU/temperatura ni prueba HONOR | Sesiones largas y recuperación de llamadas |
| Privacidad | 1 | Consultas públicas separadas del historial; manifest sin backup | Contexto personal puede ir a Groq; no auditoría completa/cifrado | Controles granulares y evaluación de egress |

Los puntajes 2 que dependen de integración Android requieren workflow Android verde en el SHA publicado. Si está bloqueado o falla, esa área conserva solo la evidencia JVM explícitamente registrada y no certifica integración. Resultados: [LEO_VALIDATION.md](LEO_VALIDATION.md).
