#!/bin/sh
set -eu

SETTINGS="android/app/src/main/java/com/jarvis/mobile/DeveloperSettingsActivity.java"

test -f "$SETTINGS"

python3 - "$SETTINGS" <<'PY'
from pathlib import Path
import re
import sys

source = Path(sys.argv[1]).read_text()
match = re.search(
    r'button\("CLEAR SAVED API KEY",\s*\(\)\s*->\s*\{(?P<body>.*?)\}\)\)',
    source,
    re.S,
)
if not match:
    raise SystemExit("CLEAR SAVED API KEY handler not found")
body = match.group("body")

remove = body.find('secrets.remove("provider_api_key")')
refresh = body.find('status.setText(CortexProviderFactory.status(this))')
if remove < 0:
    raise SystemExit("CLEAR SAVED API KEY no longer removes provider_api_key")
if refresh < 0 or refresh < remove:
    raise SystemExit(
        "CLEAR SAVED API KEY changes runtime credential state without refreshing visible provider status"
    )

print("Provider-key status runtime-truth contract satisfied")
PY
