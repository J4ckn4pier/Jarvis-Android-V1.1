#!/bin/sh
set -eu

PREVIEW="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiPreviewActivity.java"
MANIFEST="android/app/src/main/AndroidManifest.xml"
DEBUG_MANIFEST="android/app/src/debug/AndroidManifest.xml"

# The preview lane must never recreate Claude's UI from memory. It may only render the exact
# jarvis-live.html asset when that asset is physically present in the APK.
test -f "$PREVIEW"
grep -q 'CANONICAL_ASSET = "jarvis-live.html"' "$PREVIEW"
grep -q 'getAssets().open(CANONICAL_ASSET)' "$PREVIEW"
grep -q 'file:///android_asset/jarvis-live.html' "$PREVIEW"
grep -q 'WebView' "$PREVIEW"
! grep -q 'loadData' "$PREVIEW"

# Production keeps the preview internal and non-exported.
grep -q 'com.jarvis.mobile.ui.ClaudeUiPreviewActivity' "$MANIFEST"
python3 - "$MANIFEST" <<'PY'
import sys
import xml.etree.ElementTree as ET

android = '{http://schemas.android.com/apk/res/android}'
root = ET.parse(sys.argv[1]).getroot()
for activity in root.findall('.//activity'):
    if activity.attrib.get(android + 'name') == 'com.jarvis.mobile.ui.ClaudeUiPreviewActivity':
        if activity.attrib.get(android + 'exported') != 'false':
            raise SystemExit('Claude UI preview activity must remain non-exported in production')
        break
else:
    raise SystemExit('Claude UI preview activity missing from production manifest')
PY

# Debug builds expose only this exact preview activity so Charles/CI can launch and inspect the
# canonical export before it replaces the live shell. Release behavior remains unchanged.
grep -q 'com.jarvis.mobile.ui.ClaudeUiPreviewActivity' "$DEBUG_MANIFEST"
grep -q 'com.jarvis.mobile.DEBUG_PREVIEW_CLAUDE_UI' "$DEBUG_MANIFEST"
python3 - "$DEBUG_MANIFEST" <<'PY'
import sys
import xml.etree.ElementTree as ET

android = '{http://schemas.android.com/apk/res/android}'
root = ET.parse(sys.argv[1]).getroot()
for activity in root.findall('.//activity'):
    if activity.attrib.get(android + 'name') == 'com.jarvis.mobile.ui.ClaudeUiPreviewActivity':
        if activity.attrib.get(android + 'exported') != 'true':
            raise SystemExit('Debug Claude UI preview must be explicitly launchable')
        actions = [a.attrib.get(android + 'name') for a in activity.findall('.//action')]
        if 'com.jarvis.mobile.DEBUG_PREVIEW_CLAUDE_UI' not in actions:
            raise SystemExit('Debug Claude UI preview action missing')
        break
else:
    raise SystemExit('Debug Claude UI preview activity override missing')
PY

echo 'CLAUDE_UI_PREVIEW_CONTRACT_GREEN'
