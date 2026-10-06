# LEO: seguridad de ejecución

Riesgos: READ_ONLY, LOW_RISK, USER_VISIBLE, SENSITIVE, IRREVERSIBLE. El registro no acepta autorización producida por el modelo. Confirmación de memoria exacta, ligada a AssistantCommand.ClearMemory, de un solo uso y con TTL. Un “sí” posterior a otra petición no autoriza el borrado anterior.

Accessibility conserva DSL allow-list sobre node_id del snapshot actual, verificación de revisión, bloqueo de nodos sensibles y límites de iteraciones. Se añaden cancelación propagada, detección de ciclos pantalla+paso no solo consecutivos y clics directos por etiqueta exacta y única. No se habilitan coordenadas ni comandos arbitrarios.

Pagos, transferencias, envío irreversible, permisos críticos y desinstalación permanecen bloqueados para UI autónoma; la persona los realiza en el teléfono. SMS/WhatsApp usan composición para revisión; no se inventa envío confirmado. El texto DONE del planificador visual aún necesita verificación de objetivo independiente: es una limitación conocida.

Privacidad: consultas de seguimiento derivan solo del tema público previamente buscado. No se adjunta el archivo completo de memoria al buscador. La conversación cloud puede recibir el contexto local acotado que ya usa la aplicación; no se afirma modo completamente privado cuando Groq está activo. Evidencias de dispositivo pueden contener datos de apps/red y se conservan localmente para revisión antes de compartir.

PLANIFICADO: confirmación fuerte por destinatario/importe/contenido, políticas por app, verificación independiente de resultados, cifrado granular, auditoría completa de datos sensibles en historial/cloud y defensa adversarial extensa.


## Evidencia y límites

Estado de esta iteración: IMPLEMENTADO donde se identifica una llamada real; AUTOMATIZADAMENTE PROBADO solo para los casos ejecutados en [LEO_VALIDATION.md](LEO_VALIDATION.md). Toda propiedad del teléfono permanece **PENDIENTE DE VALIDACIÓN FÍSICA**. Los contratos o tipos sin ruta productiva se indican como PLANIFICADO.
