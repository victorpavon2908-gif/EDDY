# EDDY - arquitectura y hoja de ruta

## Objetivo
EDDY debe ser la interfaz personal del teléfono: escuchar lenguaje natural, entender la intención, crear un plan de acciones, ejecutar ese plan y aprender localmente para depender cada vez menos de APIs.

## Flujo
1. Voz / wake word.
2. Interpretación local rápida.
3. Planificador por API cuando la frase sea ambigua o compleja.
4. Ejecución mediante APIs Android, intents y AccessibilityService.
5. Memoria local de preferencias, patrones y respuestas aprendidas.
6. Respuesta por voz.

## Estado actual
- Fallback real de Android SpeechRecognizer.
- Preferencia por reconocimiento on-device cuando está disponible.
- Modo ligero sin descargas automáticas de modelos locales grandes.
- Memoria local y caché de respuestas aprendidas.
- Interpretación tolerante a lenguaje conversacional y muletillas.
- Separación y ejecución secuencial de múltiples acciones conocidas.
- EddyAccessibilityService registrado para control visual del dispositivo.

## Ejemplos
- "EDDY, haceme el favor y prendeme la linterna" -> SET_TORCH(true)
- "EDDY, entra a WhatsApp y prende la linterna" -> OPEN_APP(WhatsApp), SET_TORCH(true)
- "EDDY, bajame el volumen y abre Spotify" -> VOLUME_DOWN, OPEN_APP(Spotify)

## Próxima sesión: APIs
La aplicación no debe quedar acoplada a un proveedor. Se implementará un gateway propio que reciba texto + memoria + capacidades y devuelva JSON validable, por ejemplo:

```json
{
  "actions": [
    {"type": "OPEN_APP", "args": {"name": "WhatsApp"}},
    {"type": "SET_TORCH", "args": {"enabled": true}}
  ],
  "confidence": 0.95,
  "requires_confirmation": false
}
```

Las claves de proveedores vivirán en backend, nunca dentro del APK. Antes de escoger proveedor se revisarán los niveles gratuitos vigentes.

## Device Control
Prioridad de ejecución:
1. API Android directa.
2. Intent Android.
3. AccessibilityService para click, texto, scroll, atrás, inicio, recientes y paneles.

El usuario debe habilitar EDDY Device Control manualmente en Ajustes > Accesibilidad. Android seguirá exigiendo intervención humana para PIN/huella, permisos críticos, instalaciones protegidas y confirmaciones financieras.

## Memoria
EDDY consulta memoria local antes de consumir API. Se almacenan conversación reciente, preferencias, patrones y respuestas reutilizables. Información web debe caducar más rápido que conocimiento general.

## Principio de hardware
Gama baja primero. El teléfono no debe cargar modelos grandes por defecto. La IA pesada se mueve al gateway/API y el dispositivo se concentra en voz, memoria, seguridad y ejecución.
