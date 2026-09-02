#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
VOICE="android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java"

# The visible setting must describe the production value it actually controls.
grep -F 'row("Voice Speed"' "$SETTINGS" >/dev/null
grep -F 'setTitle("Voice Speed")' "$SETTINGS" >/dev/null
! grep -F 'row("Voice Model"' "$SETTINGS" >/dev/null

# SAVE persists the speech-rate value consumed by production runtime.
grep -F 'putFloat("voice_rate",rates[index])' "$SETTINGS" >/dev/null
grep -F 'getFloat("voice_rate"' "$VOICE" >/dev/null

# CANCEL remains non-mutating through the standard dialog negative action.
grep -F 'setNegativeButton("CANCEL",null)' "$SETTINGS" >/dev/null

echo "Settings Voice Speed runtime contract passed"
