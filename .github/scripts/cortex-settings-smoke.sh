#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
SETTINGS="$PACKAGE/com.jarvis.mobile.DeveloperSettingsActivity"

# Raw model/endpoint/credential controls are preserved, but are no longer the normal user Settings screen.
adb shell am force-stop "$PACKAGE"
sleep 1
adb logcat -c
adb shell am start -W -n "$SETTINGS" | tee "$OUTPUT/emulator-cortex-settings-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-cortex-settings-launch.txt"

SETTINGS_READY=0
for attempt in $(seq 1 20); do
  adb shell uiautomator dump /sdcard/jarvis-cortex-settings-ui.xml >/dev/null 2>&1 || true
  if adb pull /sdcard/jarvis-cortex-settings-ui.xml "$OUTPUT/jarvis-cortex-settings-ui-attempt-$attempt.xml" >/dev/null 2>&1; then
    UI="$OUTPUT/jarvis-cortex-settings-ui-attempt-$attempt.xml"
    if grep -q 'DEVELOPER OPTIONS' "$UI" \
        && grep -q 'Deterministic brain active; no general local cortex configured' "$UI" \
        && grep -q 'OpenAI-compatible endpoint' "$UI" \
        && grep -q 'CLEAR SAVED API KEY' "$UI"; then
      cp "$UI" "$OUTPUT/jarvis-cortex-settings-ui.xml"
      SETTINGS_READY=1
      break
    fi
  fi
  sleep 1
done

if [ "$SETTINGS_READY" -ne 1 ]; then
  adb logcat -d > "$OUTPUT/emulator-cortex-settings-logcat.txt" || true
  adb shell dumpsys activity activities > "$OUTPUT/emulator-cortex-settings-activity.txt" || true
  echo '--- JARVIS developer provider settings trace ---'
  cat "$OUTPUT/emulator-cortex-settings-launch.txt" || true
  grep -E 'AndroidRuntime|FATAL EXCEPTION|SecurityException|Background activity' "$OUTPUT/emulator-cortex-settings-logcat.txt" || true
  cat "$OUTPUT/emulator-cortex-settings-activity.txt" || true
fi

test "$SETTINGS_READY" -eq 1
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-cortex-settings.png"
