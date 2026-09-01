#!/usr/bin/env bash
set -euo pipefail

FILE="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"

python3 - "$FILE" <<'PY'
from pathlib import Path
import re
import sys

text = Path(sys.argv[1]).read_text()
match = re.search(r'private void showWidgetLockSettings\(\)\{(?P<body>.*?)\n    private void requestQuickAccessWidget', text, re.S)
if not match:
    raise SystemExit("showWidgetLockSettings() not found")
body = match.group("body")

if '.setPositiveButton("SAVE"' not in body:
    raise SystemExit("lock-screen dialog must expose SAVE")
if '.setNegativeButton("CANCEL",null)' not in body:
    raise SystemExit("lock-screen dialog must expose CANCEL")

listener = body.split('.setMultiChoiceItems', 1)[1].split('.setPositiveButton', 1)[0]
if 'putBoolean("lock_screen_assistant_enabled"' in listener:
    raise SystemExit("lock-screen preference must not persist from the selection listener")

positive = body.split('.setPositiveButton("SAVE"', 1)[1]
if 'putBoolean("lock_screen_assistant_enabled"' not in positive:
    raise SystemExit("SAVE must persist the staged lock-screen preference")

print("settings lock-screen SAVE/CANCEL contract: PASS")
PY
