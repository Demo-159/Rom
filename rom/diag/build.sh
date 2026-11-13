#!/bin/bash
# Builds rom/diag/system/framework/tvdiag.jar (dex) from Diag.java. Test builds only.
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/home/user/sdk}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-36/android.jar
OUT=$(mktemp -d)
javac -source 8 -target 8 -bootclasspath "$JAR" -classpath "$JAR" -d "$OUT/classes" src/tv/rom/diag/Diag.java 2>&1 | grep -v -i "warning" || true
"$BT/d8" --min-api 21 --release --lib "$JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
mkdir -p system/framework
(cd "$OUT" && rm -f tvdiag.jar && zip -q tvdiag.jar classes.dex)
cp "$OUT/tvdiag.jar" system/framework/tvdiag.jar
rm -rf "$OUT"
ls -la system/framework/tvdiag.jar
