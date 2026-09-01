#!/bin/sh
set -eu

GRADLE_VERSION="8.13"
GRADLE_DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_DIST_SHA256="20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
GRADLE_HOME_BASE="${GRADLE_USER_HOME:-${HOME:-/tmp/.gradle}}/niko-bootstrap"
GRADLE_DIR="$GRADLE_HOME_BASE/gradle-${GRADLE_VERSION}"
GRADLE_BIN="$GRADLE_DIR/bin/gradle"
ZIP_FILE="$GRADLE_HOME_BASE/gradle-${GRADLE_VERSION}-bin.zip"

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

download_gradle() {
  mkdir -p "$GRADLE_HOME_BASE"
  tmp="$ZIP_FILE.tmp"
  rm -f "$tmp"

  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 --silent --show-error "$GRADLE_DIST_URL" --output "$tmp"
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "$tmp" "$GRADLE_DIST_URL"
  else
    echo "ERROR: curl o wget es requerido para descargar Gradle 8.13." >&2
    exit 1
  fi

  actual=$(checksum "$tmp")
  if [ "$actual" != "$GRADLE_DIST_SHA256" ]; then
    rm -f "$tmp"
    echo "ERROR: checksum invalido para Gradle 8.13." >&2
    exit 1
  fi

  mv "$tmp" "$ZIP_FILE"
}

install_gradle() {
  if [ -x "$GRADLE_BIN" ]; then
    return
  fi

  if [ ! -f "$ZIP_FILE" ] || [ "$(checksum "$ZIP_FILE")" != "$GRADLE_DIST_SHA256" ]; then
    rm -f "$ZIP_FILE"
    download_gradle
  fi

  if ! command -v unzip >/dev/null 2>&1; then
    echo "ERROR: unzip es requerido para preparar Gradle 8.13." >&2
    exit 1
  fi

  rm -rf "$GRADLE_DIR"
  unzip -q "$ZIP_FILE" -d "$GRADLE_HOME_BASE"

  if [ ! -x "$GRADLE_BIN" ]; then
    echo "ERROR: no se pudo preparar Gradle 8.13." >&2
    exit 1
  fi
}

if [ -n "${JAVA_HOME:-}" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
  if [ ! -x "$JAVA_CMD" ]; then
    echo "ERROR: JAVA_HOME no apunta a un JDK valido." >&2
    exit 1
  fi
elif ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java no esta disponible. Instala JDK 17 o configura JAVA_HOME." >&2
  exit 1
fi

install_gradle
exec "$GRADLE_BIN" "$@"
