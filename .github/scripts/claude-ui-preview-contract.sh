#!/bin/sh
set -eu

PREVIEW="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiPreviewActivity.java"
MANIFEST="android/app/src/main/AndroidManifest.xml"

# The preview lane must never recreate Claude's UI from memory. It may only render the exact
# jarvis-live.html asset when that asset is physically present in the APK.
test -f "$PREVIEW"
grep -q 'CANONICAL_ASSET = "jarvis-live.html"' "$PREVIEW"
grep -q 'getAssets().open(CANONICAL_ASSET)' "$PREVIEW"
grep -q 'file:///android_asset/jarvis-live.html' "$PREVIEW"
grep -q 'WebView' "$PREVIEW"
! grep -q 'loadData' "$PREVIEW"

# Preview is an internal verification surface, not an exported Android entry point.
grep -q 'com.jarvis.mobile.ui.ClaudeUiPreviewActivity' "$MANIFEST"
python3 - "$MANIFEST" <<'PY'
import sys
import xml.etree.ElementTree as ET

android = '{http://schemas.android.com/apk/res/android}'
root = ET.parse(sys.argv[1]).getroot()
for activity in root.findall('.//activity'):
    if activity.attrib.get(android + 'name') == 'com.jarvis.mobile.ui.ClaudeUiPreviewActivity':
        if activity.attrib.get(android + 'exported') != 'false':
            raise SystemExit('Claude UI preview activity must remain non-exported')
        break
else:
    raise SystemExit('Claude UI preview activity missing from manifest')
PY

echo 'CLAUDE_UI_PREVIEW_CONTRACT_GREEN'
