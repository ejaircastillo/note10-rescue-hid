#!/usr/bin/env bash
# Compila el APK PRIVADO con el PIN embebido.
#
# Requiere `pin-local.properties` (no versionado; ver pin-local.properties.example).
# El APK resultante lleva el PIN ofuscado: NO se publica ni se comparte.
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -f pin-local.properties ]; then
  echo "ERROR: falta pin-local.properties."
  echo "       cp pin-local.properties.example pin-local.properties  y poné pin=XXXX"
  exit 1
fi

export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Java/jdk-17}"

./gradlew clean test assembleDebug --console=plain

mkdir -p dist-privado
cp app/build/outputs/apk/debug/app-debug.apk dist-privado/note10-rescue-hid-privado-debug.apk

echo
echo "APK privado: dist-privado/note10-rescue-hid-privado-debug.apk"
sha256sum dist-privado/note10-rescue-hid-privado-debug.apk
echo "NO publicar ni compartir este APK (lleva el PIN embebido)."
