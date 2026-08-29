#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"

# Start from HOME so the prior assistant smoke session is not reused. The debug-only receiver asks
# Android's already-bound JARVIS VoiceInteractionService to show a fresh real session and carries a
# deterministic command only in debuggable builds.
adb shell input keyevent KEYCODE_HOME
sleep 1
adb logcat -c
adb shell am broadcast -a com.jarvis.mobile.DEBUG_SHOW_ASSISTANT -p "$PACKAGE" \
  --es jarvis_test_command "Jarvis, text Mom I am on my way" \
  | tee "$OUTPUT/emulator-overlay-decision-trigger.txt"

OVERLAY_READY=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-overlay-decision-logcat.txt"
  adb shell dumpsys voiceinteraction > "$OUTPUT/emulator-overlay-decision-dumpsys.txt" || true
  if grep -q 'JARVIS_OVERLAY_SESSION_SHOWN' "$OUTPUT/emulator-overlay-decision-logcat.txt" \
      && grep -q 'JARVIS_RUNTIME_OUTPUT state=AWAITING_APPROVAL' "$OUTPUT/emulator-overlay-decision-logcat.txt" \
      && grep -q 'mShown=true' "$OUTPUT/emulator-overlay-decision-dumpsys.txt"; then
    OVERLAY_READY=1
    break
  fi
  sleep 1
done

if [ "$OVERLAY_READY" -ne 1 ]; then
  echo '--- JARVIS overlay decision trace ---'
  grep -E 'JARVIS_DEBUG_SESSION|JARVIS_OVERLAY_SESSION_SHOWN|JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_SHARED_BRAIN_ACTIVE|VoiceInteraction|voiceinteraction|FATAL EXCEPTION' \
    "$OUTPUT/emulator-overlay-decision-logcat.txt" || true
  if grep -q 'JARVIS_OVERLAY_SESSION_SHOWN' "$OUTPUT/emulator-overlay-decision-logcat.txt" \
      && ! grep -q 'mShown=true' "$OUTPUT/emulator-overlay-decision-dumpsys.txt"; then
    echo 'overlay session dismissed before decision controls could be inspected'
  else
    echo 'overlay session never reached shown + AWAITING_APPROVAL state'
  fi
  exit 1
fi

# Reconfirm foreground/shown state immediately before accessibility lookup so a window-focus race
# cannot be misreported as a missing decision control.
adb shell dumpsys voiceinteraction > "$OUTPUT/emulator-overlay-decision-dumpsys-before-ui.txt" || true
if ! grep -q 'mShown=true' "$OUTPUT/emulator-overlay-decision-dumpsys-before-ui.txt"; then
  echo 'overlay session dismissed before decision controls could be inspected'
  exit 1
fi

adb shell uiautomator dump /sdcard/jarvis-overlay-decision-ui.xml
adb pull /sdcard/jarvis-overlay-decision-ui.xml "$OUTPUT/jarvis-overlay-decision-ui.xml"
if ! grep -q 'content-desc="JARVIS APPROVE action"' "$OUTPUT/jarvis-overlay-decision-ui.xml"; then
  adb shell dumpsys voiceinteraction > "$OUTPUT/emulator-overlay-decision-dumpsys-approve-miss.txt" || true
  if ! grep -q 'mShown=true' "$OUTPUT/emulator-overlay-decision-dumpsys-approve-miss.txt"; then
    echo 'overlay session dismissed before decision controls could be inspected'
  else
    echo 'overlay APPROVE control missing from shown session UI tree'
  fi
  exit 1
fi
if ! grep -q 'content-desc="JARVIS CANCEL action"' "$OUTPUT/jarvis-overlay-decision-ui.xml"; then
  adb shell dumpsys voiceinteraction > "$OUTPUT/emulator-overlay-decision-dumpsys-cancel-miss.txt" || true
  if ! grep -q 'mShown=true' "$OUTPUT/emulator-overlay-decision-dumpsys-cancel-miss.txt"; then
    echo 'overlay session dismissed before decision controls could be inspected'
  else
    echo 'overlay CANCEL control missing from shown session UI tree'
  fi
  exit 1
fi
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-overlay-decision-pending.png"

python3 - "$OUTPUT/jarvis-overlay-decision-ui.xml" > "$OUTPUT/jarvis-overlay-cancel-tap.txt" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('content-desc') == 'JARVIS CANCEL action':
        match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib['bounds'])
        if not match:
            raise SystemExit('overlay CANCEL control has invalid bounds')
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
else:
    raise SystemExit('overlay CANCEL control missing from UI tree')
PY
set -- $(cat "$OUTPUT/jarvis-overlay-cancel-tap.txt")
adb shell input tap "$1" "$2"

OVERLAY_CANCELLED=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/emulator-overlay-cancel-logcat.txt"
  if grep -q 'JARVIS_SHARED_BRAIN_ACTIVE.*state=IDLE' "$OUTPUT/emulator-overlay-cancel-logcat.txt"; then
    OVERLAY_CANCELLED=1
    break
  fi
  sleep 1
done
if [ "$OVERLAY_CANCELLED" -ne 1 ]; then
  echo '--- JARVIS overlay cancellation trace ---'
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_SHARED_BRAIN_ACTIVE|JARVIS_COMMAND_RESULT' \
    "$OUTPUT/emulator-overlay-cancel-logcat.txt" || true
fi
test "$OVERLAY_CANCELLED" -eq 1

adb shell uiautomator dump /sdcard/jarvis-overlay-cancelled-ui.xml
adb pull /sdcard/jarvis-overlay-cancelled-ui.xml "$OUTPUT/jarvis-overlay-cancelled-ui.xml"
! grep -q 'content-desc="JARVIS APPROVE action"' "$OUTPUT/jarvis-overlay-cancelled-ui.xml"
! grep -q 'content-desc="JARVIS CANCEL action"' "$OUTPUT/jarvis-overlay-cancelled-ui.xml"
adb exec-out screencap -p > "$OUTPUT/jarvis-emulator-overlay-decision-cancelled.png"
