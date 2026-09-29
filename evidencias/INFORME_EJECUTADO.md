# Revisión y mediciones ejecutadas de EDDY / LEO

Fecha: 29 de septiembre de 2026. Versión examinada: rama main, aplicación web.

## Resultados con evidencia

| Medición ejecutada | Resultado | Evidencia |
|---|---:|---|
| Regresiones de cálculo, enrutamiento y procesamiento de métricas | 36/36 aprobadas | [Log TAP](ejecucion_software/tests_web.log) |
| Validación del lector de batería Android con datos de prueba | 5/5 aprobadas | [Log unittest](ejecucion_software/tests_bateria.log) |
| Tipado TypeScript | Código de salida 0 | [Log](ejecucion_software/typescript.log) |
| Compilación web y servidor | Código de salida 0 | [Log](ejecucion_software/build_web.log) |
| Enrutamiento textual: 10 casos repetidos 1.000 veces | 10.000/10.000 correctos en este corpus | [Resultado JSON](ejecucion_software/benchmark_texto.log) |
| Latencia del enrutador textual en Node | Mediana y p95 en el JSON adjunto, unidad ms | [Benchmark](ejecucion_software/benchmark_texto.log) |

Los casos repetidos miden rendimiento del enrutador; no representan 10.000 frases diferentes ni una estimación de exactitud general. Las pruebas del lector de batería usan fixtures sintéticos y no miden consumo del teléfono. El benchmark no incluye reconocimiento de voz, red, interfaz ni micrófono. La compilación advierte que el paquete JavaScript supera 500 kB.

[Manifiesto](ejecucion_software/resultados.json): entorno, comandos, duración de cada ejecución, códigos de salida, commit base y SHA-256 de fuentes y logs. El commit base precede a las correcciones; los hashes identifican las fuentes efectivamente ejecutadas.

## Correcciones realizadas

- Conservación de signos aritméticos, paréntesis y separadores decimales en operaciones web.
- División expresada como «dividido por» y multiplicación como «multiplicado por».
- Rechazo de texto ajeno a la expresión; «Leonardo» ya no se corta como palabra de activación «Leo».
- Una orden de gesto negada como «no bailes» no ejecuta el gesto.
- CSV con comas entre comillas, saltos de línea y comillas escapadas.
- Falsos positivos calculados por hora, duración explícita de batería y rechazo de valores inválidos, intentos duplicados y evidencia ausente.
- Automatización de pruebas coherente con la aplicación web, sin generar APK.

## Hallazgo de arquitectura y límites

El commit `20f9139` convirtió el proyecto en una aplicación web y eliminó las fuentes Android y los assets de `app/src/main/assets`. `app/build.gradle.kts` actualmente delega en npm. Los scripts antiguos Android y de MicroGPT permanecieron en el repositorio, pero no son ejecutables contra el contenido actual. Se conservan sus fallos en los logs, no se cuentan como pruebas aprobadas. La automatización anterior también referenciaba un backend inexistente; se sustituyó por validación de la aplicación actual.

No hay un teléfono conectado a este entorno. **M01–M10 siguen sin medición física.** La revisión no certifica el micrófono, la batería, WhatsApp, la calidad de fuentes ni la estabilidad de una instalación Android. La tasa de pruebas aprobadas no sustituye estas métricas.

## Reproducción

```bash
npm ci --ignore-scripts --no-audit --no-fund
python3 scripts/medir_repo.py
npm run measure
```

`medir_repo.py` guarda también comprobaciones fallidas para hacer visible el alcance; consultar los códigos individuales del manifiesto, no interpretar la finalización del recolector como aprobación total.

## Captura física de batería preparada

Requiere Python 3, Android Platform Tools y un teléfono ya autorizado mediante depuración inalámbrica. No conectar el cargador durante la prueba. Ejecutar con LEO activo en la condición indicada y sin cambiar brillo ni conectividad entre sesiones:

```bash
adb devices
python scripts/medir_bateria.py --serial IP:PUERTO --modo reposo
python scripts/medir_bateria.py --serial IP:PUERTO --modo mixto
```

Duración por defecto: 60 minutos en reposo y 30 en uso mixto. Guarda modelo, versión Android, lecturas cada 30 segundos, tiempo monotónico, resultado y hashes. Rechaza batería simulada o carga; una interrupción produce estado INVALIDO. Mide descenso total del dispositivo, no gasto exclusivo de LEO. No envía mensajes ni instala aplicaciones.

Para incorporar un resultado válido en `mediciones.csv`, registrar el descenso en `valor`, `duracion_h`, estado `MEDIDO` y una ruta de `evidencia` relativa a la carpeta del CSV, por ejemplo `dispositivo/FECHA_reposo/resultado.json`. El cálculo exige un archivo local existente y no vacío; esto verifica presencia, no autenticidad ni contenido de la evidencia. Revisar el resultado y las lecturas antes de aceptarlo.

El resto de pruebas físicas conserva el [protocolo completo](../docs/LEO_011_PHYSICAL_TEST_PROTOCOL.md). En M01 hacen falta 100 intentos observados; en M02 se registra el total de FP y las horas observadas. M03–M05 usan tiempos observados, M06/M07 casos revisados y M10 incidentes por sesión. Cero solo se registra después de observar una sesión, nunca por falta de datos.
