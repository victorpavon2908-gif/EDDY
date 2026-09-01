@ECHO OFF
SETLOCAL ENABLEEXTENSIONS

SET "GRADLE_VERSION=8.13"
SET "GRADLE_DIST_URL=https://services.gradle.org/distributions/gradle-8.13-bin.zip"
SET "GRADLE_DIST_SHA256=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
IF DEFINED GRADLE_USER_HOME (
  SET "GRADLE_HOME_BASE=%GRADLE_USER_HOME%\niko-bootstrap"
) ELSE (
  SET "GRADLE_HOME_BASE=%USERPROFILE%\.gradle\niko-bootstrap"
)
SET "GRADLE_DIR=%GRADLE_HOME_BASE%\gradle-%GRADLE_VERSION%"
SET "GRADLE_BIN=%GRADLE_DIR%\bin\gradle.bat"
SET "ZIP_FILE=%GRADLE_HOME_BASE%\gradle-%GRADLE_VERSION%-bin.zip"

IF EXIST "%GRADLE_BIN%" GOTO RUN_GRADLE

IF NOT EXIST "%GRADLE_HOME_BASE%" MKDIR "%GRADLE_HOME_BASE%"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip='%ZIP_FILE%'; $url='%GRADLE_DIST_URL%'; $expected='%GRADLE_DIST_SHA256%'; if(Test-Path $zip){$actual=(Get-FileHash -Algorithm SHA256 $zip).Hash.ToLower(); if($actual -ne $expected){Remove-Item -Force $zip}}; if(-not (Test-Path $zip)){Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile ($zip + '.tmp'); $actual=(Get-FileHash -Algorithm SHA256 ($zip + '.tmp')).Hash.ToLower(); if($actual -ne $expected){Remove-Item -Force ($zip + '.tmp'); throw 'Checksum invalido para Gradle 8.13'}; Move-Item -Force ($zip + '.tmp') $zip}; if(Test-Path '%GRADLE_DIR%'){Remove-Item -Recurse -Force '%GRADLE_DIR%'}; Expand-Archive -Path $zip -DestinationPath '%GRADLE_HOME_BASE%' -Force"
IF ERRORLEVEL 1 (
  ECHO ERROR: No se pudo descargar, validar o extraer Gradle 8.13.
  EXIT /B 1
)

IF NOT EXIST "%GRADLE_BIN%" (
  ECHO ERROR: No se pudo preparar Gradle 8.13.
  EXIT /B 1
)

:RUN_GRADLE
CALL "%GRADLE_BIN%" %*
SET EXIT_CODE=%ERRORLEVEL%
ENDLOCAL & EXIT /B %EXIT_CODE%
