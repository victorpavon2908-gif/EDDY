# LEO 0.11.0 — protocolo de prueba física

Este protocolo valida en un teléfono real lo que las pruebas JVM, Robolectric y Lint no
pueden certificar. El dispositivo objetivo inicial es el **Honor X6c**. No se debe escribir
`APROBADO` ni completar porcentajes, tiempos o consumo sin observarlos o medirlos.

Los resultados se registran en [LEO_011_PHYSICAL_TEST_RESULTS.md](LEO_011_PHYSICAL_TEST_RESULTS.md).

## Reglas de registro

- Registrar la versión mostrada por la aplicación, modelo del teléfono, versión de Android,
  nivel inicial y final de batería, conectividad y permisos usados.
- Usar solamente estos estados: `NO EJECUTADO`, `APROBADO`, `FALLÓ`, `BLOQUEADO`.
- Cada intento cuenta aunque falle. No repetir silenciosamente un intento para mejorar la cifra.
- Anotar lo dicho literalmente y lo que LEO hizo. No deducir una causa sin evidencia.
- Si se reinicia LEO, Android, la red o un permiso, anotarlo en observaciones.
- No enviar mensajes reales durante la prueba. WhatsApp debe quedar en el compositor para
  revisión manual; LEO nunca debe pulsar **Enviar**.

## Preparación

1. Dejar la batería entre 60 % y 90 %, desconectar el cargador y esperar cinco minutos.
   Evitar iniciar en 100 %, porque Android puede ocultar parte del descenso inicial.
2. Confirmar LEO `0.11.0`, permiso de micrófono y funcionamiento en segundo plano.
3. Anotar si Android excluye a LEO de la optimización de batería. No cambiar este ajuste a
   mitad de una sesión de consumo.
4. Confirmar conexión Wi-Fi o datos y presencia de WhatsApp normal, Business o ambos.
5. En **Ajustes → Mi voz**, anotar si **Priorizar mi voz** está activo. Si se usa, registrar
   cuatro frases distintas de 2–6 segundos estando solo.
6. Abrir **Diagnóstico de voz** si está disponible y poner los contadores iniciales en la hoja.

## Bloque A — activación por voz

### A1. Batería de 100 llamadas

Ejecutar exactamente 100 llamadas a `Leo`, repartidas así:

| Escenario | Intentos |
| --- | ---: |
| Voz normal a distancia habitual | 20 |
| Voz rápida y voz lenta | 10 + 10 |
| Voz baja y susurro | 10 + 5 |
| A 1 metro y a 2 metros | 10 + 5 |
| Ventilador y TV/conversación ambiental | 5 + 5 |
| Auriculares conectados | 5 |
| Pantalla bloqueada | 5 |

Para cada intento, preparar la ventana del diagnóstico y decir `Leo` una sola vez. Registrar:

- `TP` si despierta dentro de la ventana;
- `FN` si no despierta;
- latencia mostrada, si el diagnóstico la proporciona.

La tasa se calcula como `TP / (TP + FN) × 100`. El objetivo ya definido por el proyecto es
**al menos 95 activaciones correctas de 100** en el Honor X6c. La hoja no presupone que se
alcanzará.

### A2. Falsos positivos y recuperación

Dejar activa durante 30 minutos la vigilancia de falsos positivos sin decir `Leo`: 10 minutos
en silencio/uso normal, 10 con conversación y 10 con TV. Registrar cada activación no llamada.
La tasa es `FP ÷ 0,5 horas`. No hay todavía un límite certificado del proyecto: se reporta el
valor observado sin convertirlo arbitrariamente en aprobado o falló.

Interrumpir el micrófono, una condición a la vez: llamada telefónica, cámara/video, otra app
con micrófono, privacidad del micrófono, bloqueo/desbloqueo y conexión/desconexión de
auriculares. Registrar si aumentan los contadores de interrupción y recuperación y si LEO
vuelve a exigir su nombre sin reinicio manual.

## Bloque B — interrupción mientras habla o busca

En cada caso pedir primero una respuesta suficientemente larga. Decir `Leo, pará`:

1. mientras investiga en la web;
2. mientras espera la síntesis remota, si Groq está configurado;
3. durante el primer bloque de voz;
4. cerca del final de la respuesta;
5. durante un gesto del robot.

Medir desde el final de `pará` hasta el silencio solo si se dispone de cronómetro o video.
Después de cada interrupción pedir inmediatamente `Leo, qué hora es`. Aprueba el intento si
la tarea y el audio anteriores se cancelan, no reaparecen y la nueva petición se atiende.

Probar además `Leo, desactívate` una vez. Debe detener la escucha persistente y requerir
reactivación manual desde la app. Anotar la reactivación antes de continuar.

## Bloque C — robot y gestos

Observar el robot durante reposo, escucha, procesamiento y habla. Luego probar saludo, salto,
baile y giro mediante toques, uno por uno.

Para cada estado o gesto registrar:

- si comenzó el movimiento correcto;
- si se mantuvo fluido o hubo congelamiento/parpadeo;
- si volvió a reposo;
- si `Leo, pará` lo canceló;
- si la escucha y las acciones siguieron funcionando aunque fallara la carga 3D.

No asignar FPS a ojo. Si no hay una herramienta que los mida, describir únicamente la
observación (`fluido`, `tirones visibles`, `congelado`) y adjuntar duración o video si existe.

## Bloque D — búsqueda con fuentes

Ejecutar estas consultas exactas:

1. `Leo, buscame información sobre Rubén Darío en 1916`.
2. `Leo, buscá noticias de Nicaragua hoy`.
3. Repetir la primera sin conexión.
4. Repetir una consulta con Groq deshabilitado y, si ya está configurado, con Groq activo.

Registrar tiempo desde el final de la orden hasta texto visible y hasta primer audio. Abrir
cada enlace mostrado y comprobar manualmente tema, fecha visible, dominio y que el extracto
corresponda a esa página. Anotar cuántas fuentes abrieron y cuántas respaldan realmente la
afirmación asociada. Un enlace no cuenta como corroboración por existir.

Un caso conectado con fuentes aprueba si termina dentro del presupuesto de red documentado
de 20 s, conserva tema/fecha, no abre logins o páginas ajenas y no inventa fuentes. Ante un
fallo real de red, aprueba el manejo del error solamente si termina y lo explica claramente;
eso no se registra como búsqueda exitosa. Sin red debe indicar que no obtuvo fuentes y no
reutilizar resultados como actuales.

## Bloque E — WhatsApp

No pulsar **Enviar**. Usar un texto inocuo de prueba y revisar el compositor.

1. `Leo, abrime WhatsApp`.
2. Dentro de 45 s: `Mandá un mensaje así Voy llegando 2`.
3. `Leo, mandale por WhatsApp al 8888 7777 que diga Prueba 24 NO ENVIAR`.
4. `Leo, mandá un mensaje por WhatsApp` (sin destinatario ni cuerpo).
5. `Leo, no abras WhatsApp`.
6. Abrir WhatsApp y pedir explícitamente un SMS; debe prevalecer SMS.
7. Repetir con pantalla bloqueada y registrar si Android muestra notificación en vez de
   afirmar falsamente que abrió la pantalla.

Comprobar aplicación elegida (normal/Business), destinatario, mayúsculas, números y cuerpo
exacto. Aprueba solo si deja revisión humana y nunca realiza el envío final.

## Bloque F — consumo y estabilidad

Hacer dos sesiones separadas, sin cargar el teléfono y sin cambiar Wi-Fi/datos, brillo,
ahorro de batería ni exclusión de optimización durante una misma sesión:

### F1. Reposo con escucha — 60 minutos

- Pantalla apagada, LEO escuchando y sin llamarlo.
- Registrar hora, batería inicial/final, temperatura que Android muestre (si la muestra),
  falsos positivos y si el servicio continúa activo.
- Consumo observado: `batería inicial − batería final` puntos porcentuales.

### F2. Uso mixto — 30 minutos

- 10 activaciones, 3 respuestas largas con voz, 2 búsquedas, 2 interrupciones, los cuatro
  gestos y 2 aperturas de WhatsApp sin enviar.
- Registrar batería inicial/final, calentamiento observable, cierres, bloqueos y recuperación.

Opcionalmente capturar **Ajustes → Batería → Uso de batería → LEO** al inicio y al final.
Ese porcentaje de Android se registra separado del descenso de carga; no son la misma métrica.
El proyecto aún no define un umbral físico de batería, por lo que este bloque produce una
línea base real y no un aprobado automático.

## Cierre

1. Completar totales únicamente desde los intentos registrados.
2. Marcar `APROBADO` solo los bloques con criterio explícito satisfecho. En F1/F2, `APROBADO`
   significa que la sesión se completó y quedó medida sin cierres ni pérdida del servicio; no
   significa que el consumo sea óptimo. Anotar `línea base medida` en observaciones hasta
   acordar un objetivo de consumo.
3. Enumerar fallos reproducibles con frase, estado del teléfono y pasos mínimos.
4. Conservar capturas o videos sin datos privados y enlazarlos desde la hoja si se publican.
5. No declarar certificada la prueba física mientras exista algún bloque `NO EJECUTADO`.
