# Mediciones técnicas reproducibles del repositorio

Fecha de revisión: 2026-09-29  
Rama analizada: `main`  
Proyecto: LEO Assistant 0.11.0

Este documento contiene **mediciones reales obtenidas del contenido del repositorio**, no estimaciones ni resultados físicos inventados. Sirven como evidencia cuantitativa de implementación y documentación. Las pruebas físicas de micrófono, batería, latencia real del dispositivo y falsos positivos continúan separadas porque requieren ejecutar LEO en hardware real.

## Resumen cuantitativo

| ID | Elemento medido | Resultado | Método |
| --- | --- | ---: | --- |
| R01 | Versión declarada | 0.11.0 | `package.json` |
| R02 | Scripts npm definidos | 4 | Conteo de `dev`, `build`, `start`, `lint` |
| R03 | Dependencias de ejecución | 7 | Conteo de `dependencies` |
| R04 | Dependencias de desarrollo | 12 | Conteo de `devDependencies` |
| R05 | Dependencias totales declaradas | 19 | R03 + R04 |
| R06 | Líneas `voiceService.ts` | 218 | Conteo de líneas del archivo |
| R07 | Líneas no vacías `voiceService.ts` | 185 | Conteo de líneas con contenido |
| R08 | Miembros/métodos detectados en `voiceService.ts` | 18 | Conteo léxico de declaraciones `public/private/static/function` |
| R09 | Líneas `localBrain.ts` | 269 | Conteo de líneas del archivo |
| R10 | Líneas no vacías `localBrain.ts` | 243 | Conteo de líneas con contenido |
| R11 | Miembros/métodos detectados en `localBrain.ts` | 5 | Conteo léxico |
| R12 | Líneas `memoryStore.ts` | 122 | Conteo de líneas del archivo |
| R13 | Líneas no vacías `memoryStore.ts` | 108 | Conteo de líneas con contenido |
| R14 | Miembros/métodos detectados en `memoryStore.ts` | 9 | Conteo léxico |
| R15 | Líneas de los 3 servicios principales | 609 | 218 + 269 + 122 |
| R16 | Líneas no vacías de los 3 servicios | 536 | 185 + 243 + 108 |
| R17 | Líneas del protocolo físico | 172 | Conteo de `LEO_011_PHYSICAL_TEST_PROTOCOL.md` |
| R18 | Líneas de la hoja de resultados | 163 | Conteo de `LEO_011_PHYSICAL_TEST_RESULTS.md` |
| R19 | Métricas físicas formalizadas | 10 | IDs M01–M10 en `evidencias/README.md` |
| R20 | Categorías principales de enrutamiento local | 13 | Secciones numeradas del método `LocalBrain.understand` |

## Evidencia funcional observable en código

### Voz

`src/services/voiceService.ts` implementa:

- reconocimiento continuo;
- resultados parciales y finales;
- idioma `es-ES`;
- reinicio automático de escucha;
- medidor de audio;
- cálculo de dBFS y SNR;
- síntesis de voz;
- cancelación de voz.

El archivo medido contiene **218 líneas**, de las cuales **185 son no vacías**.

### Cerebro local

`src/services/localBrain.ts` contiene **269 líneas**, con **243 no vacías**, y divide el enrutamiento principal en **13 categorías** documentadas en el propio código: detener, gestos, herramientas, hora, batería, linterna, memoria, matemáticas, aplicaciones externas, mensajes, búsqueda web, saludos y consultas generales.

### Persistencia

`src/services/memoryStore.ts` contiene **122 líneas**, con **108 no vacías**, y almacena cuatro grupos principales mediante claves separadas:

1. ajustes;
2. hechos/memoria;
3. historial;
4. casa inteligente.

Además, el historial se limita explícitamente a los últimos **30 turnos**.

## Trazabilidad de archivos medidos

| Archivo | SHA del blob analizado |
| --- | --- |
| `package.json` | `3341469ad2cbac88cb9e3c37259ef754b39f4da4` |
| `src/services/voiceService.ts` | `24c4d13d3e610281997969d09cc23e4bd1fa6b62` |
| `src/services/localBrain.ts` | `41e4b28fe82915de03d027ed131b10f1553d4db5` |
| `src/services/memoryStore.ts` | `6d3a84888cc6e0594a15bb1b120b5544691ee91b` |
| `docs/LEO_011_PHYSICAL_TEST_PROTOCOL.md` | `d56b569259a294f0c81382f8ea77da3e55f4fd0a` |
| `docs/LEO_011_PHYSICAL_TEST_RESULTS.md` | `1e48253bfc0d6f6d7cf00d8cfd92efb2ccc100c0` |

## Interpretación

Estas mediciones demuestran de forma cuantitativa el tamaño, estructura, dependencias, rutas funcionales y cobertura documental del repositorio. **No sustituyen** las mediciones físicas de activación por voz, falsos positivos, consumo de batería o latencia en un Honor X6c. Esas métricas solo pueden cerrarse al ejecutar la prueba en el dispositivo y registrar la evidencia correspondiente.
