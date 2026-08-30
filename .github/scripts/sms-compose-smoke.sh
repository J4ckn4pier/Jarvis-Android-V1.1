#!/bin/sh
set -eux

OUTPUT="android/app/build/outputs/apk/debug"
PACKAGE="com.jarvis.mobile"
ACTIVITY="$PACKAGE/com.jarvis.mobile.MainActivity"

start_test_command() {
  command="$1"
  adb shell am start -W -n "$ACTIVITY" --es jarvis_test_command "\"$command\""
}

# Compiled-APK proof for the real SMS approval + compose handoff. CI must never transmit a message:
# it approves only JARVIS's existing consequential-action gate, then explicitly chooses a clearly
# labeled debug-only smsto: target from Android's real chooser and inspects the original handoff.
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
start_test_command "Text 5550100 saying I'm on my way" | tee "$OUTPUT/sms-compose-launch.txt"
grep -q 'Status: ok' "$OUTPUT/sms-compose-launch.txt"

APPROVAL_READY=0
for attempt in $(seq 1 30); do
  adb logcat -d > "$OUTPUT/sms-compose-logcat.txt" || true
  if grep -Fq "JARVIS_RUNTIME_INPUT utterance=Text 5550100 saying I'm on my way" "$OUTPUT/sms-compose-logcat.txt" \
      && grep -q 'JARVIS_RUNTIME_OUTPUT state=AWAITING_APPROVAL' "$OUTPUT/sms-compose-logcat.txt"; then
    APPROVAL_READY=1
    break
  fi
  sleep 1
done
if [ "$APPROVAL_READY" -ne 1 ]; then
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT|JARVIS_SMS_CAPTURE' "$OUTPUT/sms-compose-logcat.txt" || true
fi
test "$APPROVAL_READY" -eq 1

adb shell uiautomator dump /sdcard/jarvis-sms-approval.xml
adb pull /sdcard/jarvis-sms-approval.xml "$OUTPUT/sms-compose-approval.xml"
python3 - "$OUTPUT/sms-compose-approval.xml" > "$OUTPUT/sms-compose-approve-tap.txt" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('content-desc') == 'JARVIS APPROVE action':
        match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib['bounds'])
        if not match:
            raise SystemExit('SMS APPROVE control has invalid bounds')
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
else:
    raise SystemExit('SMS APPROVE control missing from UI tree')
PY
set -- $(cat "$OUTPUT/sms-compose-approve-tap.txt")
adb shell input tap "$1" "$2"

# On stock Android the real SENDTO intent may correctly produce a chooser because Messages and the
# debug verifier both support smsto:. Select only the verifier; never select the real Messages app.
CAPTURE_SELECTED=0
for attempt in $(seq 1 10); do
  adb logcat -d > "$OUTPUT/sms-compose-logcat.txt" || true
  if grep -Fq "JARVIS_SMS_CAPTURE number=5550100 body=I'm on my way" "$OUTPUT/sms-compose-logcat.txt"; then
    CAPTURE_SELECTED=1
    break
  fi
  adb shell uiautomator dump /sdcard/jarvis-sms-chooser.xml >/dev/null 2>&1 || true
  if adb pull /sdcard/jarvis-sms-chooser.xml "$OUTPUT/sms-compose-chooser.xml" >/dev/null 2>&1; then
    if python3 - "$OUTPUT/sms-compose-chooser.xml" > "$OUTPUT/sms-compose-capture-tap.txt" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    label = (node.attrib.get('text') or '') + ' ' + (node.attrib.get('content-desc') or '')
    if 'JARVIS SMS Capture' in label:
        match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if not match:
            continue
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        raise SystemExit(0)
raise SystemExit(1)
PY
    then
      set -- $(cat "$OUTPUT/sms-compose-capture-tap.txt")
      adb shell input tap "$1" "$2"
      CAPTURE_SELECTED=1
      break
    fi
  fi
  sleep 1
done

test "$CAPTURE_SELECTED" -eq 1

SMS_PROVEN=0
for attempt in $(seq 1 20); do
  adb logcat -d > "$OUTPUT/sms-compose-logcat.txt" || true
  if grep -Fq "JARVIS_SMS_CAPTURE number=5550100 body=I'm on my way" "$OUTPUT/sms-compose-logcat.txt"; then
    SMS_PROVEN=1
    break
  fi
  sleep 1
done

if [ "$SMS_PROVEN" -ne 1 ]; then
  grep -E 'JARVIS_RUNTIME_INPUT|JARVIS_RUNTIME_OUTPUT|JARVIS_COMMAND_RESULT|JARVIS_SMS_CAPTURE' "$OUTPUT/sms-compose-logcat.txt" || true
  cat "$OUTPUT/sms-compose-chooser.xml" 2>/dev/null || true
fi

test "$SMS_PROVEN" -eq 1
