#!/usr/bin/env bash
set -uo pipefail

echo "=== [CI Artifacts Collector] Collecting AndroidTest Reports, Logcat, and Screenshots ==="
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
ADB="$SDK_ROOT/platform-tools/adb"

ART_DIR="build/test-artifacts"
mkdir -p "$ART_DIR/screenshots"
mkdir -p "$ART_DIR/reports/xml"
mkdir -p "$ART_DIR/reports/html"

echo "1. Dumping Logcat..."
"$ADB" logcat -d > "$ART_DIR/logcat.txt" 2>&1 || true
ls -lh "$ART_DIR/logcat.txt" || true

echo "2. Pulling UI Screenshots from Emulator..."
"$ADB" pull /sdcard/Download/ui_screenshots/. "$ART_DIR/screenshots/" 2>/dev/null || true
"$ADB" pull /sdcard/Android/data/ru.personalbudget.app.aadece/files/ui_screenshots/. "$ART_DIR/screenshots/" 2>/dev/null || true
"$ADB" exec-out screencap -p > "$ART_DIR/screenshots/99_emulator_final_screen.png" 2>/dev/null || true
ls -lh "$ART_DIR/screenshots/" || true

echo "3. Copying AndroidTest XML and HTML reports..."
cp -r app/build/outputs/androidTest-results/connected/* "$ART_DIR/reports/xml/" 2>/dev/null || true
cp -r app/build/reports/androidTests/connected/* "$ART_DIR/reports/html/" 2>/dev/null || true

echo "=== [CI Artifacts Collector] XML Report Summary ==="
for xml in $(find "$ART_DIR/reports/xml" -name "TEST-*.xml" 2>/dev/null); do
  echo "--- File: $xml ---"
  cat "$xml"
done

echo "4. Shutting down Android Emulator..."
"$ADB" emu kill >/dev/null 2>&1 || true
if [ -f /tmp/android_emulator.pid ]; then
  kill -9 "$(cat /tmp/android_emulator.pid)" >/dev/null 2>&1 || true
fi

# Publish collected artifacts (XML reports, HTML reports, Logcat, and PNG screenshots) to branch ci-artifacts/pr-19
if [ "${GITHUB_ACTIONS:-false}" = "true" ]; then
  echo "5. Archiving and publishing UI test artifacts to ci-artifacts/pr-19 branch..."
  ZIP_PATH="$ART_DIR/android-ui-test-artifacts.zip"
  (cd "$ART_DIR" && zip -r "android-ui-test-artifacts.zip" reports logcat.txt screenshots) || true

  TMP_ART_REPO="/tmp/ci_artifacts_publish"
  rm -rf "$TMP_ART_REPO"
  mkdir -p "$TMP_ART_REPO"
  cp -r "$ART_DIR/"* "$TMP_ART_REPO/"

  REMOTE_URL=$(git config --get remote.origin.url)
  AUTH_HEADER=$(git config --get http.https://github.com/.extraheader || true)

  (
    cd "$TMP_ART_REPO"
    git init -b ci-artifacts/pr-19
    git config user.name "github-actions[bot]"
    git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
    if [ -n "$AUTH_HEADER" ]; then
      git config http.https://github.com/.extraheader "$AUTH_HEADER"
    fi
    git add -A
    git commit -m "chore(ci): upload Android Emulator UI test reports, logcat, and screenshots for run ${GITHUB_RUN_ID:-local}"
    git push --force "$REMOTE_URL" ci-artifacts/pr-19:refs/heads/ci-artifacts/pr-19 || true
  )
fi

echo "=== [CI Artifacts Collector] Done ==="
