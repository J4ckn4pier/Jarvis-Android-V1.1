#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"
SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
DEV="android/app/src/main/java/com/jarvis/mobile/DeveloperSettingsActivity.java"

# Canonical presentation must expose the production Settings-hosted provider surface.
grep -q 'ACTION_AI_PROVIDERS = "ai_providers"' "$ROUTER"
grep -q 'case ACTION_AI_PROVIDERS:' "$ROUTER"
grep -q 'SettingsActivity.class' "$ROUTER"
grep -q 'showProviderConnections' "$SETTINGS"

# User-facing provider choices must preserve the free/local Ollama path.
grep -q 'Free Local AI (Ollama)' "$SETTINGS"
grep -q 'DEFAULT_LOCAL_AI_ENDPOINT' "$SETTINGS"
grep -q '11434/v1/chat/completions' "$SETTINGS"

# Developer options remain a production activity reachable from the presentation bridge.
grep -q 'ACTION_DEVELOPER_OPTIONS = "developer_options"' "$ROUTER"
grep -q 'DeveloperSettingsActivity.class' "$ROUTER"
test -s "$DEV"

echo "Claude UI provider/settings contract passed"
