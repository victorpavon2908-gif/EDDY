# Validación Android 0.12.0 — 2026-09-29

## Ejecutado en el entorno de desarrollo

| Comprobación | Resultado |
|---|---|
| `bash scripts/test_ai_core.sh` | 44 pruebas JVM aprobadas |
| `bash scripts/test_voice_fidelity.sh` | 78 pruebas JVM aprobadas, incluidas 3 del temporizador |
| `python scripts/validate_leo_microgpt_assets.py` | Checkpoint verificado, 109722 bytes |
| `git diff --check` | Sin errores |
| Gradle Android offline | Bloqueado: plugin Android 8.13.2 no disponible en caché |

Las pruebas JVM cubren lógica del núcleo y audio; no ejecutan una interfaz Android ni el micrófono real. No se declara compilación Android o Lint aprobados desde este entorno. El workflow `EDDY Android tests and lint` ejecuta ambas comprobaciones en un entorno con SDK y dependencias, y adjunta informes.

## Alcance

Recuperación del árbol nativo de `66930d1`, manteniendo el código web posterior como complemento. Correcciones adicionales: ruta Java no portable, sondeo de preferencias, navegación Atrás, formularios adaptables, conservación de estado de herramientas, actualización de relojes limitada al ciclo visible, temporizador monotónico y errores recuperables en preparación inicial. La lógica inalcanzable que proponía modelos Qwen fue eliminada; no se afirma un ahorro de descarga porque esa ruta ya estaba deshabilitada.

## Pendiente en dispositivo

Instalación/actualización con identidad de firma conservada, rotación, teclado y fuente grande, permisos rechazados, preparación interrumpida, escucha con pantalla apagada y recuperación, temporizador tras suspensión, pruebas de voz y mediciones de batería del protocolo existente. No se generó APK ni se inventaron resultados físicos.
