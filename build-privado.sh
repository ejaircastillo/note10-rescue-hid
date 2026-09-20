#!/usr/bin/env bash
# Compila el APK CON el PIN embebido y lo deja en dist/ con el nombre de la versión.
#
# El PIN vive en `pin-local.properties`, versionado a propósito: el dueño del
# dispositivo autorizó publicarlo (ver README). El build público se compila con
# `-PskipPin=true` y no lleva ningún PIN.
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -f pin-local.properties ]; then
  echo "ERROR: falta pin-local.properties (tiene que tener la línea pin=XXXX)."
  exit 1
fi

export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Java/jdk-17}"

VERSION="$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)"/\1/')"

./gradlew clean test assembleDebug --console=plain

mkdir -p dist
OUT="dist/note10-rescue-hid-${VERSION}-pin-debug.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$OUT"

echo
echo "APK con PIN embebido: $OUT"
sha256sum "$OUT"
echo "Ojo: después de correr este script, app/build/.../app-debug.apk lleva el PIN."
echo "Para volver al APK público: ./gradlew clean assembleDebug -PskipPin=true"
