#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID="$ROOT/android"
cd "$ANDROID"

chmod +x ./gradlew

echo "== CarDiag fast verification =="
echo "== Repository sanity =="
test -f ./gradlew
test -f ./app/build.gradle.kts
test -f ./app/src/main/AndroidManifest.xml
grep -q 'CarDiagModernActivity' ./app/src/main/AndroidManifest.xml
grep -q 'CarDiagUnifiedApp' ./app/src/main/java/dz/cardiag/app/CarDiagModernActivity.kt
if grep -RInE 'localhost|127\.0\.0\.1' ./app/src/main ./app/src/debug 2>/dev/null; then
  echo "ERROR: local-host endpoint found in Android sources."
  exit 1
fi

echo "== Gradle =="
./gradlew --version

echo
echo "== Unit tests + lint + debug APK =="
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug --console=plain

echo
echo "== Verification passed =="
echo "Debug APK: $ANDROID/app/build/outputs/apk/debug/"
