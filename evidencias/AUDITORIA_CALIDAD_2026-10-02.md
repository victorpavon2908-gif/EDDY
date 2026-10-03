# Auditoría de calidad ejecutada — EDDY / LEO

**Fecha:** 2 de octubre de 2026  
**Rama:** `main`  
**Commit auditado:** `279ac610db1a8d00e6b81ab7636161ea025c8307`  
**Objetivo:** dejar evidencia reproducible y defendible de las pruebas realmente ejecutadas sobre la versión Android y el complemento web de EDDY / LEO.


## 1. Resultado general de software

| Área | Ejecución real | Resultado | Evidencia |
| --- | --- | --- | --- |
| Android JVM/Robolectric | `./gradlew :app:testDebugUnitTest` | **412 pruebas, 412 aprobadas, 0 fallos, 0 ignoradas** | Workflow Android run `37054936384`, artefacto `android-validation` |
| Android Lint | `./gradlew :app:lintDebug` | **0 errores, 84 warnings, 16 hints** | `lint-results-debug.txt/html/xml` del mismo artefacto |
| Build de la validación Android | Gradle | **BUILD SUCCESSFUL** | Workflow Android run `37054936384` |
| Web companion | `npm test` | **36/36 aprobadas** | Workflow web run `37054936383` |
| Lector de batería con fixtures | `python -m unittest discover -s tests -p 'test_*.py' -v` | **5/5 aprobadas** | Workflow web run `37054936383` |
| Build web | build del workflow | **1.909 módulos transformados; build completado** | Workflow web run `37054936383` |

**Artefacto Android:** ID `11248401758`, nombre `android-validation`, tamaño 190.923 bytes, digest  
`sha256:6e8b347fd313584cc21b90d7d37145dd96924c0477e2eafd152797f034bdfcb6`.

## 2. Cobertura funcional observada en las pruebas Android

La suite aprobada no es una sola prueba de compilación. Incluye pruebas separadas de:

- **Acciones y transformación:** `ActionExecutorTest`, `TransformationPipelineTest`.
- **Cerebro e intención:** `LocalBrainTest`, `PolymorphismTest`, `LeoStructuredPlannerTest`, resolutores semánticos y rutas web.
- **Polimorfismo generado:** `GeneratedToolSpecTest`, `GeneratedFormulaEngineTest`, `GeneratedGameRouterTest`.
- **Voz:** 117 pruebas en el paquete `com.niko.assistant.voice`, incluyendo wake word, recuperación, fidelidad, transcripción, políticas de salida y ventana de comando.
- **IA y búsqueda:** 69 pruebas en `com.niko.assistant.ai`, incluyendo Groq, streaming, síntesis, calidad de investigación y búsqueda nativa.
- **Control del dispositivo:** 20 pruebas sobre planificación, automatización, acciones directas y contexto visual.
- **Aprendizaje:** 22 pruebas.
- **IA local:** 29 pruebas.
- **Memoria:** 16 pruebas.
- **Robot:** 9 pruebas.
- **Media:** 9 pruebas.

Los conteos anteriores salen del reporte HTML de `testDebugUnitTest` generado por Gradle en el workflow auditado.

## 3. Evidencia específica del polimorfismo

En la versión auditada existen y se ejecutan pruebas para comprobar que una orden de transformación no se queda solo en texto.

Casos cubiertos en la suite:

- frases naturales como `conviértete en...`, `transformate en...`, `quiero que seas...`, `hacete...`;
- transformaciones nativas como cámara, video, audio, música, calculadora, cronómetro, temporizador, reloj, notas y conversor;
- transformación generativa para formas no nativas;
- herramientas compuestas que no deben colapsar a una sola palabra conocida;
- juegos generados y selección de familia;
- Tetris/Tetrix/Tettrix;
- Snake, Pong, memoria, billar, plataformas, objetivos/disparos y arcade;
- apps o experiencias generadas a partir de nombres arbitrarios;
- bloques de aplicación como tabs, feed, chat, calendario, kanban, galería, perfil, navegador y navegación GPS;
- rechazo de componentes o acciones no permitidas dentro del esquema generado.

La finalidad de estas pruebas es defender que el comportamiento está **implementado y validado por código**, no solo descrito en una presentación.

## 4. Hallazgos de Lint

La ejecución terminó con **0 errores**, por lo tanto Lint no bloqueó la validación. Se conservaron **84 warnings** y **16 hints** como deuda técnica visible, no se ocultaron.

Entre los avisos presentes en el reporte se observan:

- uso implícito del locale en un `String.format`;
- avisos de versiones/dependencias disponibles;
- tamaño de un vector drawable;
- accesibilidad de un `OnTouchListener` que debería acompañarse de `performClick`.

Estos avisos no invalidaron el build, pero deben presentarse como observaciones reales de auditoría, no como “cero problemas”.

## 5. Qué sí demuestra esta auditoría

Esta evidencia permite afirmar que, en el commit auditado:

1. la suite Android de pruebas unitarias/Robolectric terminó sin fallos;
2. Android Lint terminó sin errores bloqueantes;
3. la ruta de transformación y el motor polimórfico tienen pruebas automatizadas;
4. la lógica de voz, IA, memoria, acciones, media y robot tiene cobertura automatizada;
5. el complemento web también pasó sus pruebas y build;
6. existe un artefacto descargable con los reportes generados por el workflow.

## 6. Qué NO demuestra y no debe inventarse

Esta auditoría de software **no certifica por sí sola**:

- tasa real de activación diciendo `Leo` en el Honor;
- falsos positivos por hora;
- latencia real del micrófono;
- tiempo real desde voz hasta respuesta en hardware;
- consumo real de batería;
- temperatura del teléfono;
- exactitud física de WhatsApp en el dispositivo;
- estabilidad durante una sesión prolongada;
- ausencia de crashes nativos en todas las condiciones del teléfono.

Esos resultados pertenecen a `docs/LEO_011_PHYSICAL_TEST_PROTOCOL.md` y `docs/LEO_011_PHYSICAL_TEST_RESULTS.md`. Deben permanecer como `NO EJECUTADO`, `BLOQUEADO`, `APROBADO` o `FALLÓ` según observación real en el teléfono.

## 7. Pruebas físicas necesarias para cerrar la auditoría

Se mantienen las métricas M01–M10 ya definidas:

| ID | Prueba física | Evidencia mínima |
| --- | --- | --- |
| M01 | 100 activaciones de voz | registro TP/FN + captura/log |
| M02 | falsos positivos | 30 min observados + contador |
| M03 | latencia de activación | logs/capturas del diagnóstico |
| M04 | interrupción con `Leo, pará` | video o cronómetro |
| M05 | tiempo de búsqueda | video/captura |
| M06 | precisión de fuentes | enlaces abiertos y revisión manual |
| M07 | exactitud de WhatsApp | compositor preparado, **sin enviar** |
| M08 | batería en reposo 60 min | inicio/final + evidencia |
| M09 | batería en uso mixto 30 min | inicio/final + evidencia |
| M10 | estabilidad | cierres, bloqueos y reinicios por sesión |

Hasta que esas pruebas se ejecuten en el teléfono, sus resultados no deben rellenarse con estimaciones.

## 8. Defensa de la tarea

Una defensa correcta puede separar la evidencia en dos niveles:

**Nivel 1 — verificación automatizada ya ejecutada:** 412 pruebas Android aprobadas, Lint sin errores, 36 pruebas web aprobadas, 5 pruebas del lector de batería con fixtures y builds exitosos.

**Nivel 2 — validación física:** protocolo M01–M10. Solo se completa con el Honor conectado y observación/registro real.

Esa separación evita presentar pruebas simuladas como mediciones físicas y hace la auditoría reproducible.

## 9. Trazabilidad

- Android workflow run: `37054936384`
- Web workflow run: `37054936383`
- Android artifact: `11248401758`
- Commit: `279ac610db1a8d00e6b81ab7636161ea025c8307`
- Documento de protocolo físico: `docs/LEO_011_PHYSICAL_TEST_PROTOCOL.md`
- Hoja de resultados físicos: `docs/LEO_011_PHYSICAL_TEST_RESULTS.md`
- Registro central de métricas: `evidencias/mediciones.csv`
