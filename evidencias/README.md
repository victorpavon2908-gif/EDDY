# Evidencias de medición — LEO

Esta carpeta contiene la evidencia cuantitativa requerida para sustentar las mediciones del proyecto.

## Regla principal

**No se inventan resultados.** Cada dato numérico debe provenir de una prueba ejecutada y debe quedar asociado a una evidencia verificable: captura, video, log o registro manual.

## Métricas obligatorias

| ID | Métrica | Fórmula / unidad | Evidencia esperada |
| --- | --- | --- | --- |
| M01 | Tasa de activación de voz | TP / (TP + FN) × 100 | Captura del diagnóstico + registro de 100 intentos |
| M02 | Falsos positivos | FP / horas observadas | Captura del contador + tiempo de observación |
| M03 | Latencia de activación | mediana en ms | Captura/log del diagnóstico |
| M04 | Tiempo de interrupción | ms desde "pará" hasta silencio | Video o cronómetro |
| M05 | Tiempo de búsqueda | segundos hasta texto visible y primer audio | Video/captura con hora o cronómetro |
| M06 | Precisión de fuentes | fuentes que respaldan / fuentes abiertas × 100 | Capturas de resultados y enlaces revisados |
| M07 | Exactitud de WhatsApp | casos correctos / casos ejecutados × 100 | Captura del compositor sin enviar |
| M08 | Consumo en reposo | batería inicial − batería final | Capturas de batería inicial/final |
| M09 | Consumo en uso mixto | batería inicial − batería final | Capturas de batería inicial/final |
| M10 | Estabilidad | cierres, bloqueos y reinicios por sesión | Video/log/observación registrada |

## Archivos

- `mediciones.csv`: registro central de resultados.
- `../docs/LEO_011_PHYSICAL_TEST_PROTOCOL.md`: procedimiento de prueba.
- `../docs/LEO_011_PHYSICAL_TEST_RESULTS.md`: resumen formal de resultados.
- `../scripts/calcular_metricas.mjs`: calcula métricas desde el CSV cuando existan datos reales.

## Cómo adjuntar evidencia por medición

Crear subcarpetas con este formato:

```
evidencias/
  M01_activacion_voz/
  M02_falsos_positivos/
  M03_latencia/
  M04_interrupcion/
  M05_busqueda/
  M06_fuentes/
  M07_whatsapp/
  M08_bateria_reposo/
  M09_bateria_uso_mixto/
  M10_estabilidad/
```

Dentro de cada carpeta, guardar las capturas/videos/logs con nombres que incluyan fecha e intento, por ejemplo:

`2026-09-29_intento_01.png`

## Estado actual

La infraestructura de medición está preparada. Los campos permanecen como **PENDIENTE** hasta que las pruebas se ejecuten físicamente y se adjunte evidencia real.

## Revisión ejecutada del 29 de septiembre

Consultar [INFORME_EJECUTADO.md](INFORME_EJECUTADO.md) para pruebas de software ejecutadas, resultados, logs, hashes y límites. El código actual de main es web; las plantillas Android no prueban que exista una versión Android medible en esta rama.

El CSV incluye `duracion_h`, obligatoria en M02, M08 y M09. Usar estado `MEDIDO` para datos observados y una ruta de evidencia relativa a esta carpeta. `npm run measure` devuelve JSON y termina con error si encuentra filas registradas inválidas. Una métrica medida no se considera automáticamente aprobada contra un umbral.
