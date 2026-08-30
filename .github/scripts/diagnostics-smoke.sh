#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
MAIN="$PACKAGE/com.jarvis.mobile.MainActivity"

# Keep JARVIS foreground so the debug-only receiver can open the production non-exported diagnostics Activity.
adb shell am start -W -n "$MAIN" >/dev/null
adb logcat -c
adb shell am broadcast --receiver-foreground -a com.jarvis.mobile.DEBUG_SHOW_DIAGNOSTICS -p "$PACKAGE" \
  | tee "$OUTPUT/emulator-diagnostics-broadcast.txt"

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
  grep -E 'JARVIS_DIAGNOSTICS_TEST|AndroidRuntime|FATAL EXCEPTION|SecurityException|Background activity' "$OUTPUT/emulator-diagnostics-logcat.txt" || true
  cat "$OUTPUT/emulator-diagnostics-activity.txt" || true
fi

test "$DIAGNOSTICS_READY" -eq 1
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-diagnostics.png"
