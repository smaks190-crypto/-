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
echo "Using EMULATOR=$EMULATOR"

SYS_IMG="system-images;android-30;default;x86_64"
echo "=== [CI Android Emulator] Installing $SYS_IMG ==="
yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
"$SDKMANAGER" --install "$SYS_IMG" "platforms;android-30"

echo "=== [CI Android Emulator] Creating AVD ci_pixel_6 ==="
echo "no" | "$AVDMANAGER" create avd --force --name ci_pixel_6 --package "$SYS_IMG" --device "pixel"

AVD_CONFIG="$HOME/.android/avd/ci_pixel_6.avd/config.ini"
if [ -f "$AVD_CONFIG" ]; then
  {
    echo "hw.lcd.density=420"
    echo "hw.lcd.width=1080"
    echo "hw.lcd.height=2400"
    echo "hw.ramSize=4096"
    echo "hw.keyboard=yes"
  } >> "$AVD_CONFIG"
fi

echo "=== [CI Android Emulator] Starting headless Android Emulator with KVM (detached FDs) ==="
python3 - "$ADB" "$EMULATOR" << 'PYEOF'
import subprocess, sys, time

adb_bin = sys.argv[1]
emulator_bin = sys.argv[2]

with open("/tmp/adb_server.log", "wb") as adb_log:
    subprocess.run(
        [adb_bin, "start-server"],
        stdin=subprocess.DEVNULL,
        stdout=adb_log,
        stderr=subprocess.STDOUT,
        close_fds=True,
        start_new_session=True,
        check=False
    )

emu_log = open("/tmp/android_emulator.log", "wb")
proc = subprocess.Popen(
    [
        emulator_bin,
        "-avd", "ci_pixel_6",
        "-no-window",
        "-gpu", "swiftshader_indirect",
        "-no-snapshot",
        "-noaudio",
        "-no-boot-anim"
    ],
    stdin=subprocess.DEVNULL,
    stdout=emu_log,
    stderr=subprocess.STDOUT,
    close_fds=True,
    start_new_session=True
)

with open("/tmp/android_emulator.pid", "w") as pf:
    pf.write(str(proc.pid))

print(f"Detached Emulator launched with PID={proc.pid}")
PYEOF

EMULATOR_PID=$(cat /tmp/android_emulator.pid)
echo "Waiting for Emulator (PID=$EMULATOR_PID) to finish booting..."

BOOT_COMPLETED=0
for i in $(seq 1 120); do
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "ERROR: Emulator process $EMULATOR_PID exited prematurely! Log output:"
    cat /tmp/android_emulator.log || true
    exit 1
  fi

  BOOT_PROP=$(timeout 5 "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || true)
  if [ "$BOOT_PROP" = "1" ]; then
    BOOT_COMPLETED=1
    echo "Emulator booted in $((i * 2)) seconds!"
    break
  fi

  if [ $((i % 10)) -eq 0 ]; then
    echo "Still waiting for boot ($((i * 2))s)... Current adb devices:"
    timeout 5 "$ADB" devices || true
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
  if timeout 5 "$ADB" shell pm path android >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "=== [CI Android Emulator] Configuring device for deterministic UI tests ==="
timeout 5 "$ADB" shell settings put global window_animation_scale 0 || true
timeout 5 "$ADB" shell settings put global transition_animation_scale 0 || true
timeout 5 "$ADB" shell settings put global animator_duration_scale 0 || true
timeout 5 "$ADB" shell input keyevent 82 || true
timeout 5 "$ADB" shell wm dismiss-keyguard || true
timeout 5 "$ADB" shell mkdir -p /sdcard/Download/ui_screenshots || true
timeout 5 "$ADB" logcat -c || true

echo "=== [CI Android Emulator] Ready for connectedDebugAndroidTest ==="

