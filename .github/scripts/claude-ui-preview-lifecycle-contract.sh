#!/bin/sh
set -eu

PREVIEW="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiPreviewActivity.java"

[ -f "$PREVIEW" ] || { echo "missing ClaudeUiPreviewActivity.java" >&2; exit 1; }

grep -F 'private WebView preview;' "$PREVIEW" >/dev/null || {
  echo "Claude preview must retain its WebView for explicit lifecycle cleanup" >&2
  exit 1
}

grep -F 'removeJavascriptInterface(ANDROID_BRIDGE)' "$PREVIEW" >/dev/null || {
  echo "Claude preview must remove the Android bridge when the Activity is destroyed" >&2
  exit 1
}

grep -F 'preview.destroy()' "$PREVIEW" >/dev/null || {
  echo "Claude preview must destroy the WebView with the Activity" >&2
  exit 1
}

echo "CLAUDE_UI_PREVIEW_LIFECYCLE_CONTRACT_GREEN"
