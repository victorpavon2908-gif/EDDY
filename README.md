<img src="app/src/main/res/drawable-nodpi/leo_emblem.webp" alt="Emblema de LEO" width="80">

# LEO · Asistente para Android

LEO es un asistente de voz en español para Android 12 o superior. Puede consultar una enciclopedia instalada en el teléfono, buscar información en internet con fuentes y abrir herramientas como notas y temporizadores. La aplicación está escrita en Kotlin con Jetpack Compose. Python se usa para preparar conocimiento, entrenar modelos y validar datos.

[Manual de la app](docs/MANUAL_USUARIO_LEO.md) · [Cómo contribuir](CONTRIBUTING.md) · [Historial técnico](HISTORIAL.md)

## Una primera prueba

Una vez preparados los módulos de voz, decí «LEO, ¿qué podés hacer?». Para probar la búsqueda, abrí **Ajustes → Internet y fuentes → Probar búsqueda**. La enciclopedia se instala por separado en **Modelos y descargas**. Podés ayudar a comprobar cómo cambia una respuesta al desconectar el teléfono en la [prueba comunitaria con Rubén Darío](https://github.com/victorpavon2908-gif/EDDY/issues/25).

## Empezá a desarrollar

Cloná el repositorio y entrá a su directorio.

```sh
git clone https://github.com/victorpavon2908-gif/EDDY.git
cd EDDY
```

**Para explorar el conocimiento local**, ejecutá esta prueba desde la raíz con Python 3 y SQLite con FTS4. Crea un índice temporal con artículos del proyecto y comprueba que búsquedas como «gallo pinto» encuentren la entrada esperada. Funciona sin descargar modelos ni instalar Android Studio.

```sh
python3 -m unittest discover -s tests -p test_frozen_curriculum.py -v
```

En Windows podés sustituir `python3` por `py -3`.

**Para trabajar en la app**, abrí la raíz en Android Studio con JDK 17. Desde SDK Manager instalá Android SDK 36, Build Tools 36.0.0, NDK 27.0.12077973 y CMake 3.22.1. Sincronizá Gradle y ejecutá el módulo `app` en un teléfono o emulador Android 12 o superior. La primera preparación de los módulos de voz requiere conexión.

## Dónde aportar

| Si te interesa… | Empezá por acá |
|---|---|
| Mejorar la búsqueda y sus fuentes | Revisá el [flujo de búsqueda](docs/SEARCH_RELIABILITY.md) y documentá una consulta que devuelva una cita incorrecta. |
| Ampliar el conocimiento en español | Explorá el [currículo local](brain/leo_seed_knowledge.jsonl) y las [pruebas de recuperación](tests/test_frozen_curriculum.py). |
| Probar la voz en un teléfono | Seguí el [protocolo de medición](docs/LEO_011_PHYSICAL_TEST_PROTOCOL.md). Las mediciones de latencia y batería requieren un dispositivo físico. |

Para proponer una función o asumir el mantenimiento de un área, consultá la [guía de colaboración](CONTRIBUTING.md). El repositorio conserva el nombre EDDY y el identificador Android `com.eddy.assistant`. La app se presenta como LEO.

## Licencia

El código original de EDDY / LEO se distribuye bajo la [GNU General Public License, versión 3](LICENSE) (`GPL-3.0-only`). Las dependencias, los modelos y los materiales de terceros conservan sus respectivas licencias. Consultá los [avisos de componentes y modelos](app/src/main/assets/licenses) y los [créditos de la interfaz](THIRD_PARTY_UI_NOTICES.md).

### Qué podés hacer con el código de LEO

| Situación | Qué implica GPLv3 |
|---|---|
| Usar LEO o modificarlo en privado | Podés hacerlo para uso personal o interno de una empresa. Podés conservar esos cambios en privado. |
| Crear tu propia versión | Podés copiar el repositorio y desarrollar un *fork*. Enviar mejoras al proyecto original es voluntario. Para incorporar un cambio a la versión oficial, seguí la [guía de contribución](CONTRIBUTING.md). |
| Vender copias o cobrar por soporte | Podés cobrar y conservar esos ingresos. Quien recibe una copia también tiene derecho a compartirla, incluso gratis. |
| Compartir código modificado | Debés distribuir la obra derivada bajo GPLv3, incluir la licencia, conservar los avisos de autoría, licencia y garantía, e indicar de forma visible que hiciste cambios y en qué fecha. |
| Entregar un APK, gratis o de pago | Debés facilitar el código fuente correspondiente a esa versión, incluidos tus cambios y los scripts necesarios para compilarla, según la sección 6. Si ofrecés el APK en una página de descarga, una opción es enlazar allí el código exacto, accesible sin cargo adicional. |
| Distribuir una versión derivada como software cerrado | GPLv3 lo prohíbe. También prohíbe imponer condiciones adicionales que impidan a quienes reciben la copia modificarla o redistribuirla. |

La licencia permite colaborar sin transferir la titularidad de las aportaciones. Un fork tiene su propio historial y sus responsables. Los permisos de escritura y publicación del repositorio oficial los administra el equipo del proyecto. Para los términos completos, consultá la [licencia](LICENSE) y las [preguntas frecuentes de GNU](https://www.gnu.org/licenses/gpl-faq.html).
