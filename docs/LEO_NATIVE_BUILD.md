# Enlace C++ de embeddings — 2026-10-12

Error reportado en Windows/NDK 27: `undefined symbol: operator new(unsigned long)` al enlazar `leo_semantic_jni`. El objeto C++ usa asignación dinámica, STL y excepciones. El error demuestra que el enlace no resolvió el runtime; sin la línea completa de enlace y CMakeCache del equipo no se puede atribuir la causa local exacta.

Se selecciona `ANDROID_STL=c++_shared` en Gradle y antes de `project()` para CMake independiente. El target enlaza explícitamente `c++_shared` y exige resolución de símbolos con `--no-undefined`. Una configuración incompatible se rechaza con un mensaje claro. No se sustituye `new` ni se modifica la inferencia.

Referencia oficial: https://developer.android.com/ndk/guides/cpp-support (selección del runtime y empaquetado automático por Gradle).

## Validación reproducible

El workflow Android incluye `externalNativeBuildDebug`, `mergeDebugNativeLibs`, tests y Lint, sin APK. La fusión de bibliotecas comprueba además posibles conflictos con las dependencias AAR.

El nuevo job Windows ejecuta `scripts/validate_android_native.py` con NDK 27.0.12077973/CMake 3.22.1. Compila el código JNI real para arm64-v8a, armeabi-v7a, x86 y x86_64, con espacios en las rutas de fuente/build. `llvm-readelf` verifica que cada ELF requiere `libc++_shared.so`. Conserva logs de cada ABI. Los resultados se consultan en la ejecución asociada al commit, sin afirmar aprobación anticipada.

## Recuperar una caché local anterior (PowerShell)

Desde la raíz del repositorio, cerrar una compilación que esté en marcha, actualizar el código y ejecutar:

```powershell
git pull --ff-only
.\gradlew.bat --stop
Remove-Item -Recurse -Force .\app\.cxx -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\app\build -ErrorAction SilentlyContinue
.\gradlew.bat :app:externalNativeBuildDebug :app:mergeDebugNativeLibs --rerun-tasks
```

Las dos carpetas eliminadas son salidas generadas de compilación, no fuentes ni datos del teléfono. Después, sincronizar Gradle en Android Studio. No hace falta borrar el SDK, reinstalar el NDK ni cambiar el código de la línea 69. Si persiste, conservar el comando completo que ejecuta `clang++` y `app/.cxx/**/CMakeCache.txt` para diagnosticar flags o cachés locales.

**PENDIENTE DE VALIDACIÓN FÍSICA:** cargar el modelo semántico e inferir en el teléfono. Compilar en Windows no prueba el dispositivo del usuario.
