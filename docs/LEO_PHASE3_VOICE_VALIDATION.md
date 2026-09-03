# LEO 0.12 — Fase 3: escucha y wake word

Esta fase convierte la afinación de voz en un proceso medible. El teléfono no guarda PCM ni grabaciones: el diagnóstico conserva únicamente métricas numéricas, texto transcrito y contadores etiquetados.

## Abrir el diagnóstico

Decir: `LEO, abrí diagnóstico de voz` o abrir la herramienta **Diagnóstico de voz** desde LEO.

El panel muestra en vivo:

- estado del wake word/micrófono;
- nivel RMS en dBFS;
- piso de ruido estimado y SNR;
- latencia aproximada desde inicio acústico hasta wake;
- texto transcrito;
- motor ASR que produjo el texto (Canary, Whisper o Canary + GTCRN);
- latencia de transcripción;
- coincidencia del perfil de voz cuando el modo propietario está activo;
- interrupciones y recuperaciones del micrófono;
- perfil de tuning activo y recomendación calculada a partir de datos etiquetados.

## Batería real de 100 llamadas

1. Pulsar **Iniciar 100**.
2. Elegir un escenario.
3. Pulsar **Preparar llamada** y decir `LEO` dentro de la ventana indicada.
4. Una detección dentro de la ventana registra un TP. Si la ventana expira sin wake, registra un FN automáticamente. También se puede marcar el fallo manualmente.
5. Repetir hasta 100 llamadas, repartiendo pruebas entre:
   - voz normal;
   - rápida;
   - lenta;
   - baja;
   - susurro;
   - 1 metro;
   - 2 metros;
   - ventilador;
   - TV;
   - auriculares;
   - pantalla bloqueada.

Objetivo inicial: **TP / (TP + FN) >= 95%** en el Honor X6c.

## Falsos positivos

Activar **Vigilancia de falsos positivos** y dejar el teléfono en uso normal sin decir `LEO`. Toda activación durante esa sesión se registra como FP. Para una primera afinación útil se recomienda acumular al menos 30–60 minutos, incluyendo TV o conversación ambiental.

El panel informa FP por hora. No bajar agresivamente el umbral de wake si esa tasa ya es alta.

## Afinación

LEO no cambia parámetros con menos de 20 llamadas etiquetadas. El asesor puede modificar de forma acotada:

- score y threshold del KWS;
- trailing blanks;
- duración mínima/máxima del probe pasivo;
- cooldown entre probes;
- piso de energía útil;
- ganancia máxima para voz baja;
- pre-roll usado por el wake.

**Aplicar y reiniciar** guarda el perfil y reinicia el servicio de voz para que el KWS nativo reconstruya su configuración. La ganancia, el probe temporal y el pre-roll se consultan dinámicamente.

## Transcripción literal

Canary sigue siendo la lectura primaria. Whisper o Canary + GTCRN se usan solo como segunda lectura cuando la calidad lo requiere. Ningún LLM reescribe el dictado.

Si dos lecturas acústicas discrepan en números, negaciones, nombres/contactos, direcciones, horas, cantidades o cuerpos de mensajes, LEO pide repetir en vez de escoger silenciosamente una versión.

## Recuperación de micrófono

Validar, una por una:

- llamada telefónica entrante/saliente;
- cámara o grabación de video;
- otra app usando el micrófono;
- interruptor de privacidad del micrófono;
- ahorro de batería;
- pantalla bloqueada y desbloqueada;
- auriculares conectados/desconectados.

Después de cada interrupción normal, el contador **Interrupciones detectadas** debe aumentar y posteriormente **Recuperaciones automáticas** debe aumentar sin tener que reiniciar manualmente LEO.

## Cierre de fase

La implementación y los tests automatizados pueden cerrar en CI. El criterio 95/100, la tasa real de FP y la recuperación bajo condiciones del fabricante solo se consideran certificados después de ejecutar esta batería físicamente en el Honor X6c.
