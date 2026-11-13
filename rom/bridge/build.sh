#!/bin/bash
# Builds rom/prebuilt/YouTubeBridge.apk (no Gradle: javac, d8, aapt2, apksigner).
# Version code at the maximum, so the Play Store never sees a "newer" YouTube to install over it.
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/home/user/sdk}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-36/android.jar
KEYS=${KEYS:-/home/user/work/keys}
OUT=$(mktemp -d)
javac -source 8 -target 8 -bootclasspath "$JAR" -classpath "$JAR" -d "$OUT/classes" src/tv/rom/youtubebridge/Bridge.java 2>&1 | grep -v "warning" || true
"$BT/d8" --min-api 21 --release --lib "$JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest AndroidManifest.xml \
    --min-sdk-version 21 --target-sdk-version 25 --version-code 2147483647 --version-name "TV bridge"
(cd "$OUT" && zip -q -j base.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"
mkdir -p ../prebuilt
"$BT/apksigner" sign --key "$KEYS/testkey.pk8" --cert "$KEYS/testkey.x509.pem" --min-sdk-version 21 \
    --out ../prebuilt/YouTubeBridge.apk "$OUT/aligned.apk"
rm -rf "$OUT"
ls -la ../prebuilt/YouTubeBridge.apk
