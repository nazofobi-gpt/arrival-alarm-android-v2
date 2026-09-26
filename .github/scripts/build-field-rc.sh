#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

./gradlew --no-daemon assembleRelease

BUILD_TOOLS_VERSION="$(ls -1 "$ANDROID_HOME/build-tools" | sort -V | tail -n 1)"
BUILD_TOOLS="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION"
RC_DIR="app/build/outputs/field-rc"
ALIGNED_APK="$RC_DIR/app-release-aligned.apk"
RC_APK="$RC_DIR/arrival-alarm-v2-field-rc.apk"
KEYSTORE="$RUNNER_TEMP/arrival-alarm-field-rc.jks"

mkdir -p "$RC_DIR"
mapfile -t RELEASE_APKS < <(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' -print | sort)
if [[ "${#RELEASE_APKS[@]}" -ne 1 ]]; then
  echo "Expected exactly one release APK, found ${#RELEASE_APKS[@]}" >&2
  find app/build/outputs/apk/release -maxdepth 1 -type f -print >&2 || true
  exit 1
fi
UNSIGNED_APK="${RELEASE_APKS[0]}"

keytool -genkeypair -noprompt \
  -keystore "$KEYSTORE" \
  -storepass changeit \
  -keypass changeit \
  -alias fieldrc \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -dname "CN=Arrival Alarm Field RC,O=Ephemeral CI Test,C=DE"

"$BUILD_TOOLS/zipalign" -f -v 4 "$UNSIGNED_APK" "$ALIGNED_APK"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:changeit \
  --key-pass pass:changeit \
  --out "$RC_APK" \
  "$ALIGNED_APK"

"$BUILD_TOOLS/zipalign" -c -v 4 "$RC_APK"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$RC_APK" | tee "$RC_DIR/apksigner-verify.txt"
"$BUILD_TOOLS/aapt" dump badging "$RC_APK" | tee "$RC_DIR/manifest-badging.txt"
if grep -q '^application-debuggable' "$RC_DIR/manifest-badging.txt"; then
  echo "Field RC must not be debuggable" >&2
  exit 1
fi

SOURCE_SHA="$(git rev-parse HEAD)"
if [[ -n "${CONNECTOR_BASE_URL:-}" ]]; then
  CONNECTOR_CONFIGURED=true
else
  CONNECTOR_CONFIGURED=false
fi
printf 'source_commit=%s\nvariant=release\ndebuggable=false\nsigning=ephemeral_ci_field_test_not_production\nconnector_base_url_configured=%s\n' \
  "$SOURCE_SHA" "$CONNECTOR_CONFIGURED" > "$RC_DIR/BUILD_PROVENANCE.txt"
sha256sum "$RC_APK" | tee "$RC_DIR/SHA256SUMS.txt"

adb uninstall com.nazofobi.arrivalalarm >/dev/null 2>&1 || true
adb install "$RC_APK"
adb shell am start -W -n com.nazofobi.arrivalalarm/.MainActivity | tee "$RC_DIR/api36-launch.txt"
adb shell pidof com.nazofobi.arrivalalarm | tee "$RC_DIR/api36-pid.txt"
test -s "$RC_DIR/api36-pid.txt"
adb exec-out screencap -p > "$RC_DIR/api36-field-rc-launch.png"
