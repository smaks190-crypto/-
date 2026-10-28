#!/usr/bin/env bash
set -euo pipefail

echo "=== [CI Android Emulator] Preparing KVM and Android SDK ==="
if [ -e /dev/kvm ]; then
  sudo chmod 666 /dev/kvm || true
  ls -la /dev/kvm
else
  echo "WARNING: /dev/kvm not found!"
fi

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"

SDKMANAGER=$(find "$SDK_ROOT/cmdline-tools" -name sdkmanager | head -n 1)
AVDMANAGER=$(find "$SDK_ROOT/cmdline-tools" -name avdmanager | head -n 1)
ADB="$SDK_ROOT/platform-tools/adb"
EMULATOR="$SDK_ROOT/emulator/emulator"

echo "Using SDK_ROOT=$SDK_ROOT"
echo "Using SDKMANAGER=$SDKMANAGER"
echo "Using AVDMANAGER=$AVDMANAGER"

SYS_IMG="system-images;android-30;default;x86_64"
echo "=== [CI Android Emulator] Installing $SYS_IMG ==="
yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
"$SDKMANAGER" --install "$SYS_IMG" "platforms;android-30" "platform-tools" "emulator"

echo "=== [CI Android Emulator] Creating AVD ci_pixel_6 ==="
echo "no" | "$AVDMANAGER" create avd --force --name ci_pixel_6 --package "$SYS_IMG" --device "pixel_6"

echo "=== [CI Android Emulator] Starting headless Android Emulator with KVM ==="
"$ADB" start-server
nohup "$EMULATOR" \
  -avd ci_pixel_6 \
  -no-snapshot-save \
  -no-window \
  -gpu swiftshader_indirect \
  -noaudio \
  -no-boot-anim \
  -camera-back none \
  -memory 4096 \
  > /tmp/android_emulator.log 2>&1 </dev/null &

EMULATOR_PID=$!
echo "$EMULATOR_PID" > /tmp/android_emulator.pid
echo "Emulator launched with PID=$EMULATOR_PID. Waiting for boot completion..."

"$ADB" wait-for-device

BOOT_COMPLETED=0
for i in $(seq 1 90); do
  BOOT_PROP=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [ "$BOOT_PROP" = "1" ]; then
    BOOT_COMPLETED=1
    echo "Emulator booted in $((i * 2)) seconds!"
    break
  fi
  sleep 2
done

if [ "$BOOT_COMPLETED" != "1" ]; then
  echo "ERROR: Emulator failed to boot within timeout. Emulator log:"
  cat /tmp/android_emulator.log || true
  exit 1
fi

# Wait for Package Manager service to be ready
for i in $(seq 1 30); do
  if "$ADB" shell pm path android >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "=== [CI Android Emulator] Configuring device for deterministic UI tests ==="
"$ADB" shell settings put global window_animation_scale 0 || true
"$ADB" shell settings put global transition_animation_scale 0 || true
"$ADB" shell settings put global animator_duration_scale 0 || true
"$ADB" shell input keyevent 82 || true
"$ADB" shell wm dismiss-keyguard || true
"$ADB" shell mkdir -p /sdcard/Download/ui_screenshots || true
"$ADB" logcat -c || true

echo "=== [CI Android Emulator] Ready for connectedDebugAndroidTest ==="
