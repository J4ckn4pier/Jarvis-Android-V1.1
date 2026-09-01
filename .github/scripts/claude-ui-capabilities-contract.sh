#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"

[ -f "$ROUTER" ] || { echo "missing ClaudeUiActionRouter.java" >&2; exit 1; }

grep -F '@JavascriptInterface' "$ROUTER" >/dev/null

grep -F 'public boolean isSupported(String action)' "$ROUTER" >/dev/null || {
  echo "Claude UI bridge must expose isSupported(action) so canonical controls can truthfully disable unwired actions" >&2
  exit 1
}

grep -F 'public String supportedActions()' "$ROUTER" >/dev/null || {
  echo "Claude UI bridge must expose supportedActions() so canonical UI can discover the production capability surface" >&2
  exit 1
}

for action in listen settings developer_options help notes memory routines skills overlays activity_feed browser hub default_assistant notification_access accessibility; do
  grep -F "\"$action\"" "$ROUTER" >/dev/null || {
    echo "supported Claude action missing from bridge declaration: $action" >&2
    exit 1
  }
done

grep -F 'return false;' "$ROUTER" >/dev/null || {
  echo "unknown Claude actions must report unsupported instead of pretending to work" >&2
  exit 1
}

echo "CLAUDE_UI_CAPABILITIES_CONTRACT_GREEN"
