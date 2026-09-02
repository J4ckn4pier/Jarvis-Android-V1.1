#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
LOCAL_POLICY="android/app/src/main/java/com/jarvis/mobile/brain/providers/LocalAiEndpointPolicy.java"
SHARED_POLICY="brain-core/src/main/java/com/jarvis/brain/EndpointTransportPolicy.java"

# Free Local AI must remain API-key-free and must still go through the shared fail-closed transport policy.
grep -q 'No OpenAI or Google provider credential is required' "$SETTINGS"
grep -q 'new SecureSecretStore(this).remove("provider_api_key")' "$SETTINGS"
grep -q 'LocalAiEndpointPolicy.allows' "$SETTINGS"
grep -q 'EndpointTransportPolicy.allows' "$LOCAL_POLICY"

# The user-facing rejection message must describe every HTTP host class the shared policy intentionally accepts.
grep -q 'localhost' "$SETTINGS"
grep -q '127.0.0.1' "$SETTINGS"
grep -q '\.local' "$SETTINGS"
grep -q '10.0.2.2' "$SETTINGS"

# Keep the shared policy fail-closed: HTTPS is accepted globally; HTTP is limited to explicit local hosts.
grep -q '"https".equals(scheme)' "$SHARED_POLICY"
grep -q 'host.equals("localhost")' "$SHARED_POLICY"
grep -q 'host.equals("127.0.0.1")' "$SHARED_POLICY"
grep -q 'host.equals("10.0.2.2")' "$SHARED_POLICY"
grep -q 'host.endsWith(".local")' "$SHARED_POLICY"
