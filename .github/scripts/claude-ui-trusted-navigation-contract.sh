#!/bin/sh
set -eu

PREVIEW="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiPreviewActivity.java"

[ -f "$PREVIEW" ] || { echo "missing ClaudeUiPreviewActivity.java" >&2; exit 1; }

grep -F 'WebViewClient' "$PREVIEW" >/dev/null || {
  echo "Claude preview must install a WebViewClient before exposing the Android bridge" >&2
  exit 1
}

grep -F 'CANONICAL_URL.equals' "$PREVIEW" >/dev/null || {
  echo "Claude preview must restrict top-level navigation to the packaged canonical asset" >&2
  exit 1
}

grep -F 'shouldOverrideUrlLoading' "$PREVIEW" >/dev/null || {
  echo "Claude preview must actively reject untrusted navigation" >&2
  exit 1
}

echo "CLAUDE_UI_TRUSTED_NAVIGATION_CONTRACT_GREEN"
