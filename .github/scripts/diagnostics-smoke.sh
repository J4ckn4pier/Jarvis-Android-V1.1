#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
DIAGNOSTICS="$PACKAGE/com.jarvis.mobile.DiagnosticsActivity"

# DiagnosticsActivity stays non-exported in production. The debug manifest alone exports it so
# emulator CI can launch the real production screen directly instead of relying on a
# BroadcastReceiver-to-Activity hop that Android 16 may background-launch-block.
adb logcat -c
adb shell am start -W -n "$DIAGNOSTICS" | tee "$OUTPUT/emulator-diagnostics-launch.txt"
grep -q 'Status: ok' "$OUTPUT/emulator-diagnostics-launch.txt"

DIAGNOSTICS_READY=0
for attempt in $(seq 1 20); do
  adb shell uiautomator dump /sdcard/jarvis-diagnostics-ui.xml >/dev/null 2>&1 || true
  if adb pull /sdcard/jarvis-diagnostics-ui.xml "$OUTPUT/jarvis-diagnostics-ui-attempt-$attempt.xml" >/dev/null 2>&1; then
    UI="$OUTPUT/jarvis-diagnostics-ui-attempt-$attempt.xml"
    if grep -q 'JARVIS PREFRONTAL CORTEX' "$UI" \
        && grep -q 'Production typed-tool self-test' "$UI" \
        && grep -q 'Autonomous conversational calls' "$UI" \
        && grep -q 'External duplex phone-audio transport required' "$UI" \
        && grep -q 'Diagnostics inspect capability registration only' "$UI"; then
      cp "$UI" "$OUTPUT/jarvis-diagnostics-ui.xml"
      DIAGNOSTICS_READY=1
      break
    fi
  fi
  sleep 1
done

if [ "$DIAGNOSTICS_READY" -ne 1 ]; then
  adb logcat -d > "$OUTPUT/emulator-diagnostics-logcat.txt" || true
  adb shell dumpsys activity activities > "$OUTPUT/emulator-diagnostics-activity.txt" || true
  echo '--- JARVIS diagnostics trace ---'
  cat "$OUTPUT/emulator-diagnostics-launch.txt" || true
  grep -E 'AndroidRuntime|FATAL EXCEPTION|SecurityException|Background activity' "$OUTPUT/emulator-diagnostics-logcat.txt" || true
  cat "$OUTPUT/emulator-diagnostics-activity.txt" || true
fi

test "$DIAGNOSTICS_READY" -eq 1
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-diagnostics.png"
