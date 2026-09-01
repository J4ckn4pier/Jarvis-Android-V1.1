#!/bin/sh
set -eu

DEV="android/app/src/main/java/com/jarvis/mobile/DeveloperSettingsActivity.java"

[ -f "$DEV" ] || { echo "RED: missing developer settings" >&2; exit 1; }

require() {
  needle="$1"
  grep -F "$needle" "$DEV" >/dev/null || { echo "Missing provider disconnect contract token '$needle'" >&2; exit 1; }
}

# Selecting deterministic local mode is a disconnect operation. An old/stale provider
# endpoint is not consumed in that mode and therefore must not be able to block SAVE.
require 'String mode = providers.getCheckedRadioButtonId() == compatible.getId()'
require 'if (!CortexProviderFactory.MODE_LOCAL.equals(mode) && !endpointValue.isEmpty() && !EndpointTransportPolicy.allows(endpointValue))'

# The transport policy remains mandatory whenever a network provider is selected.
require 'EndpointTransportPolicy.allows(endpointValue)'

# The ordinary user-facing disconnect path must still remove the saved provider key.
USER="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
grep -F 'new SecureSecretStore(this).remove("provider_api_key")' "$USER" >/dev/null || {
  echo "Normal provider disconnect no longer removes the saved API key" >&2
  exit 1
}

echo "Provider local disconnect contract GREEN"