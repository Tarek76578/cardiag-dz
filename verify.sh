#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT/android"

chmod +x ./gradlew

echo "== CarDiag fast verification =="
echo "Gradle:"
./gradlew --version

echo
echo "== Unit tests + lint + debug APK =="
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug --stacktrace --console=plain

echo
echo "== Verification passed =="
echo "Debug APK: android/app/build/outputs/apk/debug/"
