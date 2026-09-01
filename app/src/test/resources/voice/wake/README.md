# Audio de regresión de la activación

Muestras sintéticas, no grabaciones del usuario. PCM firmado de 16 bits, little
endian, mono, 16.000 Hz, sin cabecera. Son recursos de pruebas JVM, no assets de la app.
`EddyKeywordNativeTest` añade 0,5 segundos de silencio antes y 1 segundo después,
y entrega bloques de 512 muestras, igual que `AudioRecord`.

Se generaron con `sherpa-onnx-offline-tts` 1.13.6, velocidad predeterminada y
[vits-piper-es_MX-claude-high](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_MX-claude-high.tar.bz2),
la misma voz opcional del catálogo. El `MODEL_CARD` atribuye el conjunto a
[HirCoir/Piper-TTS-Spanish](https://huggingface.co/spaces/HirCoir/Piper-TTS-Spanish)
y declara licencia Apache-2.0. Los pesos no se incluyen aquí.

Generación, con las rutas locales del modelo y los binarios oficiales:

```sh
sherpa-onnx-offline-tts \
  --vits-model=vits-piper-es_MX-claude-high/es_MX-claude-high.onnx \
  --vits-tokens=vits-piper-es_MX-claude-high/tokens.txt \
  --vits-data-dir=vits-piper-es_MX-claude-high/espeak-ng-data \
  --num-threads=2 --output-filename=eddy.wav 'Eddy.'
ffmpeg -i eddy.wav -ac 1 -ar 16000 -f s16le eddy.pcm
```

| Archivo | Texto sintetizado | Es una llamada |
| --- | --- | --- |
| eddy.pcm | Eddy. | Sí |
| edi.pcm | Edi. | Sí |
| hey_edi.pcm | Hey Edi. | Sí |
| oye_eddy.pcm | Oye Eddy. | Sí |
| edi_fast.pcm | Edi, qué hora es. | Sí; nombre histórico, velocidad normal |
| pedi.pcm | Pedí la comida. | No |
| medio.pcm | Me dio un vaso. | No |
| nadie.pcm | No vino nadie. | No |
| radio.pcm | Prendé la radio. | No |
| dia.pcm | Buenos días. | No |
| ella.pcm | Ella está aquí. | No |
| luz.pcm | Encendé la luz. | No |
| edificio.pcm | El edificio es alto. | No |
| edita.pcm | Editá ese texto. | No |
| le_di.pcm | Le di la comida. | No |
| freddy.pcm | Freddy. | No |
| pedir.pcm | Voy a pedir ayuda. | No |
| ayer_pedi.pcm | Ayer pedí ayuda. | No |
| edison.pcm | Edison. | No |

`previous-keywords.txt` conserva la configuración de 07997e6 para comparar el
mismo audio con ambos detectores. Se exige conservar todas las llamadas reconocidas,
reconocer al menos una adicional y no añadir falsas activaciones en este corpus.

Resultado local con JNI 1.13.6: llamadas reconocidas 2 → 3 de 5; falsas activaciones
3 → 3 de 14. Mejora `eddy.pcm`; siguen sin detectarse `edi.pcm` y `oye_eddy.pcm`.
Las falsas activaciones de `edificio`, `le_di` y `ayer_pedi` ya existían y siguen
pendientes. La prueba informa estas limitaciones; no exige mantener los errores.
Este corpus pequeño y sintético sirve de regresión, no mide precisión con personas,
ruido, distancias, dialectos ni teléfonos reales.
