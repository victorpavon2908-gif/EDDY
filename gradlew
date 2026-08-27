#!/bin/sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://services.gradle.org/distributions/gradle-8.13-wrapper.jar"
WRAPPER_SHA256="81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"

checksum() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo "ERROR: sha256sum o shasum es requerido para validar Gradle." >&2
    exit 1
  fi
}

download_wrapper() {
  tmp="$WRAPPER_JAR.tmp"
  rm -f "$tmp"
  mkdir -p "$(dirname "$WRAPPER_JAR")"

  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error "$WRAPPER_URL" --output "$tmp"
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "$tmp" "$WRAPPER_URL"
  else
    echo "ERROR: curl o wget es requerido para descargar el wrapper oficial de Gradle." >&2
    exit 1
  fi

  actual=$(checksum "$tmp")
  if [ "$actual" != "$WRAPPER_SHA256" ]; then
    rm -f "$tmp"
    echo "ERROR: checksum invalido para gradle-wrapper.jar." >&2
    exit 1
  fi

  mv "$tmp" "$WRAPPER_JAR"
}

if [ -f "$WRAPPER_JAR" ]; then
  actual=$(checksum "$WRAPPER_JAR")
  if [ "$actual" != "$WRAPPER_SHA256" ]; then
    rm -f "$WRAPPER_JAR"
  fi
fi

if [ ! -f "$WRAPPER_JAR" ]; then
  download_wrapper
fi

if [ -n "${JAVA_HOME:-}" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD=java
fi

if ! command -v "$JAVA_CMD" >/dev/null 2>&1 && [ ! -x "$JAVA_CMD" ]; then
  echo "ERROR: Java no esta disponible. Instala JDK 17 o configura JAVA_HOME." >&2
  exit 1
fi

exec "$JAVA_CMD" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
