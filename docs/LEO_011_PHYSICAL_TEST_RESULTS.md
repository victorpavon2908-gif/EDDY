# LEO 0.11.0 — resultados de prueba física

> Estado general: **NO EJECUTADO**. Esta plantilla contiene criterios y campos vacíos, no
> resultados de laboratorio. Sustituir `—` solamente con datos observados.

Protocolo: [LEO_011_PHYSICAL_TEST_PROTOCOL.md](LEO_011_PHYSICAL_TEST_PROTOCOL.md)

## Identificación

| Campo | Valor observado |
| --- | --- |
| Fecha y hora de inicio | — |
| Probador | — |
| Modelo del teléfono | Honor X6c (confirmar en el equipo) |
| Versión de Android | — |
| Versión mostrada por LEO | — |
| Tipo de instalación/actualización | — |
| WhatsApp instalado | — |
| Conexión | — |
| Priorizar mi voz | — |
| Exclusión de optimización de batería | — |
| Voz neural / voz Android usada | — |

## Resumen

| Bloque | Estado | Resultado medido | Evidencia/observación |
| --- | --- | --- | --- |
| A. Activación y recuperación | NO EJECUTADO | TP —/100; FN —; FP —/h | — |
| B. Interrupción | NO EJECUTADO | —/6 casos aprobados | — |
| C. Robot y gestos | NO EJECUTADO | — | — |
| D. Búsqueda con fuentes | NO EJECUTADO | —/4 consultas | — |
| E. WhatsApp | NO EJECUTADO | —/7 casos | — |
| F1. Escucha 60 min | NO EJECUTADO | — puntos de batería | — |
| F2. Uso mixto 30 min | NO EJECUTADO | — puntos de batería | — |

## A. Activación por voz

| Escenario | Intentos | TP | FN | Latencia mediana mostrada | Observaciones |
| --- | ---: | ---: | ---: | --- | --- |
| Normal | 20 | — | — | — | — |
| Rápida | 10 | — | — | — | — |
| Lenta | 10 | — | — | — | — |
| Baja | 10 | — | — | — | — |
| Susurro | 5 | — | — | — | — |
| 1 metro | 10 | — | — | — | — |
| 2 metros | 5 | — | — | — | — |
| Ventilador | 5 | — | — | — | — |
| TV/conversación | 5 | — | — | — | — |
| Auriculares | 5 | — | — | — | — |
| Pantalla bloqueada | 5 | — | — | — | — |
| **Total** | **100** | **—** | **—** | **—** | — |

Tasa calculada: `— / (— + —) × 100 = — %`  
Criterio 95/100: **NO EJECUTADO**

| Prueba adicional | Antes | Después | Estado | Observación |
| --- | ---: | ---: | --- | --- |
| 30 min falsos positivos | FP — | FP — | NO EJECUTADO | — |
| Llamada telefónica | Interrupciones — / recuperaciones — | — / — | NO EJECUTADO | — |
| Cámara/video | — / — | — / — | NO EJECUTADO | — |
| Otra app con micrófono | — / — | — / — | NO EJECUTADO | — |
| Privacidad del micrófono | — / — | — / — | NO EJECUTADO | — |
| Bloquear/desbloquear | — / — | — / — | NO EJECUTADO | — |
| Auriculares conectar/desconectar | — / — | — / — | NO EJECUTADO | — |

## B. Interrupción

| Momento | Se calló | No reapareció audio viejo | Atendió `qué hora es` | Tiempo al silencio | Estado/nota |
| --- | --- | --- | --- | --- | --- |
| Durante investigación | — | — | — | — | NO EJECUTADO |
| Esperando síntesis Groq | — | — | — | — | NO EJECUTADO/BLOQUEADO |
| Primer bloque hablado | — | — | — | — | NO EJECUTADO |
| Final de respuesta | — | — | — | — | NO EJECUTADO |
| Durante gesto | — | — | — | — | NO EJECUTADO |
| `Leo, desactívate` | — | — | Reactivación manual: — | — | NO EJECUTADO |

## C. Robot y gestos

| Estado/gesto | Correcto | Fluidez observada | Volvió a reposo | `Leo, pará` cancela | Estado/nota |
| --- | --- | --- | --- | --- | --- |
| Reposo | — | — | No aplica | No aplica | NO EJECUTADO |
| Escucha | — | — | — | — | NO EJECUTADO |
| Procesamiento | — | — | — | — | NO EJECUTADO |
| Habla | — | — | — | — | NO EJECUTADO |
| Saludo | — | — | — | — | NO EJECUTADO |
| Salto | — | — | — | — | NO EJECUTADO |
| Baile | — | — | — | — | NO EJECUTADO |
| Giro | — | — | — | — | NO EJECUTADO |

Fallo de carga 3D observado: —  
Escucha/acciones continuaron: —

## D. Búsqueda con fuentes

| Consulta/condición | Texto visible | Primer audio | Fuentes abren | Fuentes respaldan | Tema/fecha correctos | Estado/nota |
| --- | --- | --- | ---: | ---: | --- | --- |
| Rubén Darío en 1916 | — | — | —/— | —/— | — | NO EJECUTADO |
| Nicaragua hoy | — | — | —/— | —/— | — | NO EJECUTADO |
| Sin conexión | — | — | — | — | — | NO EJECUTADO |
| Groq apagado/activo | — | — | —/— | —/— | — | NO EJECUTADO/BLOQUEADO |

Fuentes, dominios y discrepancias observadas:

- —

## E. WhatsApp

| Caso | App abierta | Destinatario | Cuerpo exacto | Quedó para revisión | No envió | Estado/nota |
| --- | --- | --- | --- | --- | --- | --- |
| Abrir WhatsApp | — | No aplica | No aplica | — | — | NO EJECUTADO |
| Seguimiento dentro de 45 s | — | Selector | — | — | — | NO EJECUTADO |
| Número + texto con números | — | — | — | — | — | NO EJECUTADO |
| Sin destinatario/cuerpo | — | Selector | — | — | — | NO EJECUTADO |
| Orden negada | — | No aplica | No aplica | No aplica | — | NO EJECUTADO |
| SMS explícito prevalece | — | — | — | — | — | NO EJECUTADO |
| Pantalla bloqueada | — | — | — | — | — | NO EJECUTADO |

## F. Batería y estabilidad

| Sesión | Inicio | Final | Duración real | Descenso | Uso atribuido por Android | Temperatura/calor | Incidentes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F1. Escucha | — % | — % | — min | — puntos | — % | — | — |
| F2. Uso mixto | — % | — % | — min | — puntos | — % | — | — |

Estado del servicio al final de F1: —  
Falsos positivos durante F1: —  
Cierres, bloqueos o reinicios: —

## Fallos reproducibles

| ID | Bloque | Frase/acción literal | Estado del teléfono | Resultado esperado | Resultado observado | Repite |
| --- | --- | --- | --- | --- | --- | --- |
| — | — | — | — | — | — | — |

## Conclusión

Estado general: **NO EJECUTADO**  
Bloques aprobados: —  
Bloques fallidos: —  
Bloques bloqueados: —  
Bloques pendientes: A, B, C, D, E, F1 y F2  
Versión física certificada: **NO**
