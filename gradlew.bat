@ECHO OFF
SETLOCAL ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION

SET "APP_HOME=%~dp0"
SET "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
SET "WRAPPER_URL=https://services.gradle.org/distributions/gradle-8.13-wrapper.jar"
SET "WRAPPER_SHA256=81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"

IF EXIST "%WRAPPER_JAR%" (
  FOR /F %%H IN ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%WRAPPER_JAR%').Hash.ToLower()"') DO SET "ACTUAL_SHA=%%H"
  IF /I NOT "!ACTUAL_SHA!"=="%WRAPPER_SHA256%" DEL /F /Q "%WRAPPER_JAR%"
)

IF NOT EXIST "%WRAPPER_JAR%" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%.tmp'; $actual=(Get-FileHash -Algorithm SHA256 '%WRAPPER_JAR%.tmp').Hash.ToLower(); if($actual -ne '%WRAPPER_SHA256%'){Remove-Item -Force '%WRAPPER_JAR%.tmp'; throw 'Checksum invalido para gradle-wrapper.jar'}; Move-Item -Force '%WRAPPER_JAR%.tmp' '%WRAPPER_JAR%'"
  IF ERRORLEVEL 1 (
    ECHO ERROR: No se pudo descargar o validar el wrapper oficial de Gradle 8.13.
    EXIT /B 1
  )
)

IF DEFINED JAVA_HOME (
  SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET "JAVA_EXE=java.exe"
)

"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
SET EXIT_CODE=%ERRORLEVEL%
ENDLOCAL & EXIT /B %EXIT_CODE%
