# Cómo colaborar con LEO

EDDY es el nombre del repositorio y LEO es el nombre que vas a ver en la app. El cliente Android usa Kotlin y Jetpack Compose. En [brain](brain/README.md) vas a encontrar los recursos del cerebro local y en [scripts](scripts) las herramientas de preparación y validación.

## Probá la app

Abrí la raíz del repositorio en Android Studio con JDK 17 y Android SDK 36. Dejá que Gradle sincronice el proyecto, elegí el módulo app y pulsá Run con un teléfono o emulador Android 12 o superior. Para una primera contribución, seguí la [prueba de consulta con y sin internet](https://github.com/victorpavon2908-gif/EDDY/issues/25) y compartí los resultados en esa misma tarea.

## Proponé una mejora

Abrí un [issue](https://github.com/victorpavon2908-gif/EDDY/issues) con un ejemplo de lo que querés lograr y cómo comprobaríamos que funciona. Si te interesa una tarea abierta, comentá qué parte querés tomar. Quien prueba la app también puede proponer prioridades o revisar una solución.

## Enviá un cambio

Creá un fork, abrí una rama desde main y concentrá el cambio en una sola tarea. Para validar cambios de Android, ejecutá desde la raíz:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

En Windows usá `gradlew.bat` desde CMD. Abrí un pull request hacia main, enlazá el issue y contá qué probaste y qué quedó pendiente. Si cambiaste una pantalla, una captura facilita la revisión.

## Compartí el mantenimiento

Si te interesa acompañar un área de forma continua, proponelo en su issue. Indicá qué te gustaría mantener y cómo podrían participar otras personas. Para una decisión que afecte a varias áreas, describí las opciones en el issue y dejá allí el acuerdo alcanzado y quién le dará seguimiento.
