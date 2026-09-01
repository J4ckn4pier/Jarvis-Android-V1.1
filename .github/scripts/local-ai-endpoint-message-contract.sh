#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
POLICY="android/app/src/main/java/com/jarvis/mobile/brain/providers/LocalAiEndpointPolicy.java"

# The normal Settings surface must describe every host class that the Free Local AI policy accepts.
grep -Fq '"10.0.2.2".equals(host)' "$POLICY"
grep -Fq 'Android emulator host 10.0.2.2' "$SETTINGS"

# Preserve the no-provider-key promise and safe transport gate for local AI.
grep -Fq 'new SecureSecretStore(this).remove("provider_api_key")' "$SETTINGS"
grep -Fq 'if (!EndpointTransportPolicy.allows(endpoint)) return false;' "$POLICY"

echo "local-ai endpoint messaging contract: PASS"
