# Corpus de activación de Niko

35 muestras sintéticas, sin grabaciones del usuario. PCM firmado de 16 bits,
little endian, mono, 16.000 Hz, sin cabecera. Son recursos de pruebas JVM y no assets
de la aplicación. La prueba añade 0,5 s de silencio antes y 1 s después; entrega
bloques de 512 muestras al mismo JNI y configuración que usa el micrófono.

Generadas con `sherpa-onnx-offline-tts` 1.13.6 y
[vits-piper-es_MX-claude-high](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_MX-claude-high.tar.bz2).
Su MODEL_CARD atribuye el conjunto a
[HirCoir/Piper-TTS-Spanish](https://huggingface.co/spaces/HirCoir/Piper-TTS-Spanish)
y declara Apache-2.0. No se incluyen pesos. Las menciones del nombre retirado son
casos negativos: permiten comprobar que ya no activa el asistente.

```sh
sherpa-onnx-offline-tts \
  --vits-model=vits-piper-es_MX-claude-high/es_MX-claude-high.onnx \
  --vits-tokens=vits-piper-es_MX-claude-high/tokens.txt \
  --vits-data-dir=vits-piper-es_MX-claude-high/espeak-ng-data \
  --num-threads=2 --speed=1 --output-filename=niko.wav 'Niko.'
ffmpeg -i niko.wav -ac 1 -ar 16000 -f s16le niko.pcm
```

| Archivo | Texto | Velocidad | Debe activar |
| --- | --- | --- | --- |
| niko.pcm | Niko. | 1 | Sí |
| nico.pcm | Nico. | 1 | Sí |
| hey_niko.pcm | Hey Niko. | 1 | Sí |
| hola_niko.pcm | Hola Niko. | 1 | Sí |
| niko_command.pcm | Niko, qué hora es. | 1 | Sí |
| niko_slow.pcm | Niko. | 0.85 | Sí |
| niko_fast.pcm | Niko. | 1.15 | Sí |
| rico.pcm | Qué rico. | 1 | No |
| pico.pcm | El pico es azul. | 1 | No |
| micro.pcm | El micro está listo. | 1 | No |
| mexico.pcm | Viajé a México. | 1 | No |
| tecnico.pcm | Llamá al técnico. | 1 | No |
| unico.pcm | Es el único. | 1 | No |
| abanico.pcm | Encendé el abanico. | 1 | No |
| nicolas.pcm | Nicolás está aquí. | 1 | No |
| nicole.pcm | Hola Nicole. | 1 | No |
| retired_01.pcm | Eddy. | 1 | No |
| retired_02.pcm | Edi. | 1 | No |
| retired_03.pcm | Hey Edi. | 1 | No |
| retired_04.pcm | Oye Eddy. | 1 | No |
| retired_05.pcm | Edi, qué hora es. | 1 | No |
| pedi.pcm | Pedí la comida. | 1 | No |
| medio.pcm | Me dio un vaso. | 1 | No |
| nadie.pcm | No vino nadie. | 1 | No |
| radio.pcm | Prendé la radio. | 1 | No |
| dia.pcm | Buenos días. | 1 | No |
| ella.pcm | Ella está aquí. | 1 | No |
| luz.pcm | Encendé la luz. | 1 | No |
| edificio.pcm | El edificio es alto. | 1 | No |
| edita.pcm | Editá ese texto. | 1 | No |
| le_di.pcm | Le di la comida. | 1 | No |
| other_name.pcm | Freddy. | 1 | No |
| pedir.pcm | Voy a pedir ayuda. | 1 | No |
| ayer_pedi.pcm | Ayer pedí ayuda. | 1 | No |
| edison.pcm | Edison. | 1 | No |

Con la configuración actual, se reconocen seis de siete llamadas a Niko y se
rechazan 27 de las 28 negativas, incluidas las cinco llamadas al nombre retirado.
La muestra aislada `nico` aún falla y `abanico` activa incorrectamente. Se informan
como limitaciones; no se exige mantener esos fallos. La regresión exige las seis
llamadas que funcionan y el rechazo de los casos negativos restantes.

Este corpus pequeño es una regresión sintética, no una medida de precisión con
personas, dialectos, ruido o micrófonos reales. Hay que probar el dispositivo.
