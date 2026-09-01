#!/bin/sh
set -eu

PUBLISHER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiStatePublisher.java"
PREVIEW="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiPreviewActivity.java"

fail() {
  echo "CLAUDE_UI_STATE_CHANNEL_FAIL: $1" >&2
  exit 1
}

[ -f "$PUBLISHER" ] || fail "missing presentation-only ClaudeUiStatePublisher"
[ -f "$PREVIEW" ] || fail "missing ClaudeUiPreviewActivity"

grep -Fq 'STATE_EVENT = "jarvis:state"' "$PUBLISHER" || fail "stable jarvis:state event name missing"
grep -Fq 'LISTENING = "listening"' "$PUBLISHER" || fail "listening state missing"
grep -Fq 'THINKING = "thinking"' "$PUBLISHER" || fail "thinking state missing"
grep -Fq 'RESPONDING = "responding"' "$PUBLISHER" || fail "responding state missing"
grep -Fq 'ACTING = "acting"' "$PUBLISHER" || fail "acting state missing"
grep -Fq 'IDLE = "idle"' "$PUBLISHER" || fail "idle state missing"
grep -Fq 'evaluateJavascript' "$PUBLISHER" || fail "publisher must push state into the canonical WebView"
grep -Fq 'CustomEvent' "$PUBLISHER" || fail "publisher must dispatch a browser event rather than modify canonical DOM assumptions"
grep -Fq 'JSONObject.quote' "$PUBLISHER" || fail "state payload must be safely quoted before JavaScript injection"
grep -Fq 'new ClaudeUiStatePublisher(preview)' "$PREVIEW" || fail "preview must create the state publisher"
grep -Fq 'statePublisher.publish(ClaudeUiStatePublisher.State.IDLE' "$PREVIEW" || fail "preview must publish an initial truthful idle state after page load"

echo "CLAUDE_UI_STATE_CHANNEL_GREEN"
