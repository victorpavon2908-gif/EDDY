# Identidad del producto: LEO

**LEO** es el único nombre público vigente de la aplicación, el asistente y su documentación. Las etiquetas de interfaz, notificaciones y entregables utilizan este nombre.

## Compatibilidad conservada

| Elemento heredado | Motivo de conservación |
| --- | --- |
| `com.eddy.assistant` y componentes Android asociados | Mantener actualizaciones, permisos y referencias a componentes instalados. |
| Paquetes y clases `com.niko.assistant`, `Niko*`, tema `Theme.NIKO` | Identificadores de implementación; renombrarlos exige una refactorización distinta de esta revisión visual. |
| Preferencias, SQLite, directorios, canales y extras `eddy_*` | Mantener datos, configuración, modelos y notificaciones existentes. |
| Órdenes internas `NIKO_TOOL_*` y alias reconocidos | Conservar el contrato entre analizador, acciones y herramientas sin modificar la escucha ni la interpretación. |
| Variables de entorno y configuración `NIKO_*`/`EDDY_*` | No invalidar instalaciones ni secretos configurados. |
| Repositorio `victorpavon2908-gif/EDDY`, URLs y caché de firma | Mantener integraciones, descargas y la identidad de actualización. El nombre del repositorio no define la marca visible. |
| Logs, resultados y recursos de pruebas históricas | Mantener evidencia original y trazabilidad; no reetiquetar pruebas antiguas como nuevas. |

Estos nombres técnicos son deuda de nomenclatura identificada, no productos diferentes. Eliminarlos globalmente sin migración podría romper funciones que deben conservarse.

## Alcance de esta revisión

Se actualizaron textos visibles de casa inteligente, sugerencias, herramientas, alarmas y temporizadores; se normaliza a LEO el texto de recordatorios heredados. También se unificaron metadatos web, nombre del proyecto Gradle, títulos de automatizaciones y documentación vigente. Se conservan los archivos históricos mediante enlaces a su revisión original.

No se cambian algoritmos de escucha, búsqueda, modelos, permisos, identificador de instalación ni bases de datos. Las dos modificaciones de LocalBrain son únicamente las etiquetas visibles de alarma y temporizador.

## Evidencia de comprobación

Se compararon byte por byte 68 archivos protegidos contra la revisión `b20e0dd5f298de07f83fc17cccb2af12f187f4dd`: voz, IA/búsqueda, servicio, modelos, manifiesto y configuración Android. Resultado: idénticos. Se comprobó aparte que LocalBrain solo tiene las dos sustituciones de etiquetas declaradas.

Ver [registro con hashes SHA-256](../evidencias/IDENTIDAD_LEO_2026-10-05.json). Esta comprobación acredita el alcance del cambio; no reemplaza pruebas físicas ni garantiza por sí sola el resultado de una auditoría. El workflow del commit de entrega aporta las pruebas Android, Lint y web.
