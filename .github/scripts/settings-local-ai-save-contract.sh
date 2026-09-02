#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"

# Invalid Local AI endpoints must not be persisted and must leave the setup dialog
# open so the user can correct the value instead of silently losing the form.
grep -F 'setPositiveButton("SAVE",null)' "$SETTINGS" >/dev/null
grep -F 'setOnShowListener' "$SETTINGS" >/dev/null
grep -F 'LocalAiEndpointPolicy.allows(endpointValue)' "$SETTINGS" >/dev/null
grep -F 'dialog.dismiss();' "$SETTINGS" >/dev/null

# Local AI remains credential-free when the configuration is accepted.
grep -F 'new SecureSecretStore(this).remove("provider_api_key")' "$SETTINGS" >/dev/null

echo "Settings Local AI SAVE contract passed"
