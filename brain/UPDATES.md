# Ediciones del conocimiento offline de LEO

## Implementado

El paquete congelado es SQLite + FTS4: reconstruirlo **indexa documentos**, no entrena pesos neuronales. MicroGPT, embeddings y recuerdos adaptativos son sistemas distintos y no se modifican en esta entrega.

El currículo propio pasa de 26 a 50 entradas: depuración, Git, pruebas, SQL, algoritmos, reportes, eficiencia, planificación, cocina y evaluación de fuentes. Es contenido de orientación limitado, no una certificación de dominio experto ni información de actualidad.

Ajustes → Conocimiento offline → **DESCARGAR / ACTUALIZAR CEREBRO** consulta el manifiesto de la publicación. Si el SHA-256 instalado coincide, evita volver a descargar. Si cambió, descarga y verifica el archivo comprimido y la base extraída antes de activar. Las descargas parciales están asociadas al hash del archivo; nunca se reutilizan bytes de otra edición. Se conserva la activación transaccional anterior y la memoria adaptativa. Los lectores reabren la base al detectar otra revisión, serializando las consultas y el cierre del lector.

El contrato `leo-brain-v1` identifica el formato compatible; el hash identifica cada edición de contenido. Los manifiestos incorporan el hash del currículo y el commit constructor. El workflow se ejecuta al cambiar currículo, constructor o workflow en main. Publica el paquete de 490–500 MB después de comprobar integridad y consultas reales. No produce APK. La instalación automática inicial conserva su comportamiento; buscar una actualización instalada requiere pulsar el botón. Una actualización interrumpida puede retomarse pulsándolo nuevamente.

## Evidencia y límites

`tests/test_frozen_curriculum.py` construye el índice real del currículo y comprueba seis consultas, documentos únicos e integridad SQLite. `LeoFrozenUpdatePolicyTest` comprueba reanudación y descarte entre versiones. Las pruebas existentes de activación cubren recuperación y rollback. El workflow de construcción verifica tamaño, hashes y consultas sobre el paquete completo antes de publicar. Consultar los resultados efectivos en Actions; no inferir aprobación por la existencia de estas pruebas.

**PENDIENTE DE VALIDACIÓN FÍSICA:** actualizar desde una edición anterior en HONOR, cortar y reanudar red, cerrar la aplicación durante la descarga, consultar mientras se activa, comprobar conservación de recuerdos y resultado sin red. No se han medido batería, latencia ni rendimiento físico.

## Boca durante el habla

Se conserva el contorno de sonrisa de la referencia y se anima su curva inferior durante SPEAKING; se retira el óvalo relleno con dientes de ese estado. No se cambian los ojos ni la boca de reposo. Es movimiento rítmico, no sincronización fonética. Aspecto durante TTS e interrupción: PENDIENTE DE VALIDACIÓN FÍSICA.
