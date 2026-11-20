#!/usr/bin/env bash
# Builds Disha.apk with only: a Java 21 runtime, the Eclipse compiler (ecj.jar),
# Android build-tools 34 and the android-34 platform. No JDK, no Gradle.
#
# Usage: ./build.sh [version-name version-code]     e.g. ./build.sh 0.2.0-beta.1 3
# version-code must go up with every release, or phones refuse the update.
# The keystore password is read from DISHA_KS_PASS (asked for if it is not set).
set -euo pipefail
VERSION_NAME="${1:-0.2.0-beta.1}"
VERSION_CODE="${2:-3}"
cd "$(dirname "$0")"
T="$PWD/tools"
BT="$T/android-14"            # build-tools_r34 unzips to android-14/
if [ ! -f "$T/ecj.jar" ] || [ ! -x "$BT/aapt2" ] || ! ls "$T"/android-*/android.jar >/dev/null 2>&1; then
    echo "Build tools missing: run tools/fetch.sh first"; exit 1
fi
PLATFORM_JAR="$(ls "$T"/android-*/android.jar | head -1)"
# Use a Java runtime in tools/jre if there is one, otherwise the system's Java (17 or newer).
if [ -x "$T/jre/bin/java" ]; then
    export JAVA_HOME="$T/jre"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
command -v java >/dev/null || { echo "Java 17+ is needed (or put a Java runtime in tools/jre)"; exit 1; }
OUT="$PWD/build"
rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/dex"

echo "== resources"
"$BT/aapt2" compile --dir app/res -o "$OUT/res.zip"

echo "== manifest + resources -> base apk"
"$BT/aapt2" link -I "$PLATFORM_JAR" --manifest app/AndroidManifest.xml "$OUT/res.zip" \
    --min-sdk-version 28 --target-sdk-version 34 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    -A app/assets -o "$OUT/base.apk"

echo "== compile java"
java -jar "$T/ecj.jar" -1.8 -nowarn -encoding UTF-8 \
    -bootclasspath "$PLATFORM_JAR:$BT/core-lambda-stubs.jar" -d "$OUT/classes" app/src

echo "== dex"
"$BT/d8" --min-api 28 --lib "$PLATFORM_JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "== package"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex 2>/dev/null) || \
    python3 -I -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1],'a'); z.write(sys.argv[2],'classes.dex'); z.close()" \
        "$OUT/unsigned.apk" "$OUT/dex/classes.dex"
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== sign"
KS="$T/disha.keystore"
if [ -z "${DISHA_KS_PASS:-}" ]; then
    [ -t 0 ] || { echo "Set DISHA_KS_PASS to the keystore password"; exit 1; }
    read -rsp "Keystore password: " DISHA_KS_PASS; echo
fi
export DISHA_KS_PASS
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass:env DISHA_KS_PASS -keypass:env DISHA_KS_PASS \
    -alias disha -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Disha" >/dev/null
"$BT/apksigner" sign --ks "$KS" --ks-pass env:DISHA_KS_PASS --key-pass env:DISHA_KS_PASS \
    --out "$PWD/Disha.apk" "$OUT/aligned.apk"
echo "Built: $PWD/Disha.apk ($VERSION_NAME, code $VERSION_CODE)"
