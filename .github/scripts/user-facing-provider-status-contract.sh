#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
FACTORY="android/app/src/main/java/com/jarvis/mobile/brain/providers/CortexProviderFactory.java"

# The runtime provider factory owns readiness. The user-facing summary must preserve
# the distinction between configured and incomplete providers instead of labelling
# every selected remote provider as connected.
grep -q 'Free/local AI needs setup' "$SETTINGS"
grep -q 'OpenAI provider needs setup' "$SETTINGS"
grep -q 'Anthropic needs setup' "$SETTINGS"
grep -q 'provider.isConfigured()' "$FACTORY"
grep -q 'OpenAI-compatible local cortex needs a model and allowed endpoint' "$FACTORY"
grep -q 'needs a model and API key' "$FACTORY"

# Free/local OpenAI-compatible mode must remain API-key optional; do not regress
# into requiring a paid-provider credential for an Ollama/local endpoint.
grep -q 'MODE_OPENAI_COMPATIBLE' "$FACTORY"
grep -q 'new OpenAiCompatibleChatProvider(endpoint, model, key)' "$FACTORY"
