# Entrenamiento adicional de LEO Neural Core

## Alcance real

La aplicación contiene dos sistemas diferentes: una base SQLite de conocimiento para
recuperación y un decoder Transformer pequeño para conversación breve. SQLite no tiene
pesos entrenables. Este experimento ajusta los pesos existentes del decoder; no crea
un modelo general de 2 GB ni sustituye el índice de conocimiento. El tamaño de una
descarga no es una medida de inteligencia ni de comprensión de lo correcto/incorrecto.

El formato INT8, las dos capas, cuatro cabezas, dimensión 64 y contexto de 40 tokens
se conservan. El vocabulario se amplía manteniendo los identificadores anteriores y
copiando todos los parámetros existentes. El entrenamiento usa solo el corpus propio
del repositorio y 22 ejemplos adicionales escritos para LEO. No usa recuerdos privados.
El candidato tiene 111.947 parámetros, 651 tokens y 117.916 bytes, sin relleno artificial.

## Reproducción

Instalar Python 3.11, PyTorch CPU 2.14.1 y NumPy 2.2.6 en un entorno de entrenamiento:

```bash
python -m pip install 'torch==2.14.1' --index-url https://download.pytorch.org/whl/cpu
python -m pip install 'numpy==2.2.6'
python scripts/test_neural_training.py
python scripts/test_neural_scoring.py
python scripts/finetune_leo_neural_core.py --steps 3200
./gradlew --version
bash scripts/test_neural_candidate.sh build/leo-neural-candidate/leo-neural-core-candidate.bundle > build/leo-neural-candidate/runtime.tsv
python scripts/score_neural_candidate.py build/leo-neural-candidate/runtime.tsv build/leo-neural-candidate/runtime-report.json
```

`LEO_KOTLIN_LIB_DIR` permite indicar la carpeta `lib` de Gradle 8.13 si no está en la
ruta bootstrap que usa el repositorio. `.github/workflows/leo-neural-training.yml`
ejecuta el proceso y guarda un artefacto descargable con pesos, métricas, respuestas y
decisión. Un workflow terminado correctamente NO significa candidato aprobado:
consultar `runtime-report.json` y `DECISION.txt`. No genera APK, no publica una versión
estable y no activa el candidato en teléfonos.

## Criterios y límites

- Verificación SHA-256 de los pesos base antes de entrenar.
- Pruebas de conservación de parámetros/token IDs y actualización real por gradiente.
- Pérdida numérica sobre 12 prompts de desarrollo excluidos del entrenamiento y
  31 ejemplos de retención. Los prompts de desarrollo se usan para selección del modelo;
  NO constituyen un benchmark independiente. Las respuestas objetivo pueden aparecer
  en entrenamiento. La retención también contiene ejemplos de entrenamiento.
- Exportación INT8 y generación con el motor Kotlin real de Android, con etiquetas de
  familia suministradas explícitamente. Esto no prueba que el router identifique todas
  esas frases de manera autónoma.
- Las 43 salidas deben coincidir con una formulación aprobada de su familia. El
  control es deliberadamente conservador: incluso una paráfrasis válida se rechaza.
  Comprueba fiabilidad de respuestas acotadas, no comprensión general ni verdad.
- Ningún candidato sustituye automáticamente al modelo estable. Una aceptación de
  estos controles todavía necesita evaluación independiente y validación física.

La primera ejecución de 400 pasos bajó la pérdida pero generó texto incoherente.
La segunda, de 1.600 pasos, obtuvo 39/43 formulaciones aprobadas: RECHAZADA PARA
ACTIVACIÓN. Se conservan evidencias de los experimentos, incluidos sus fallos.
Los primeros informes llaman `heldout` a los prompts excluidos; deben interpretarse
como desarrollo porque posteriormente se usaron para selección. El script actual
emplea el nombre correcto `development`.

## Reparación del cargador estable

El hash esperado por `LeoMicroGptCore.SHA256` era distinto del archivo distribuido y
validado en CI. Se alinea con los bytes empaquetados, sin cambiar esos pesos. La prueba
`LeoMicroGptAssetFilesTest` ahora exige además que el cargador y el asset coincidan.
Esto corrige una causa de rechazo del modelo durante su carga.

## Pendiente para un modelo general mayor

Se necesita un runtime compatible, modelo preentrenado con licencia revisada, corpus
amplio y evaluado, recursos de entrenamiento y conversión, evaluación independiente,
paquete versionado y mecanismo de activación/rollback. Aumentar el límite del ZIP
SQLite a 2 GB no implementaría esas capacidades. La actualización de conocimiento
existente sigue separada de este experimento neuronal.

La ejecución de 3.200 pasos obtuvo **41/43** formulaciones aprobadas y también fue
RECHAZADA PARA ACTIVACIÓN. Falló en una explicación sobre certeza y en continuidad
sin contexto. Evidencia completa: `evidencias/leo-neural-training-2026-10-20/`.
Las métricas del host son tiempo de entrenamiento, nunca latencia o consumo Android.
