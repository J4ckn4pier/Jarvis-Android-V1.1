#!/bin/sh
set -eu

SOURCE="android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"
python3 - "$SOURCE" <<'PY'
from pathlib import Path
import sys

text = Path(sys.argv[1]).read_text()
start = text.index("private void showWidgetLockSettings()")
end = text.index("private void requestQuickAccessWidget()", start)
method = text[start:end]

save = method.find('.setPositiveButton("SAVE"')
cancel = method.find('.setNegativeButton("CANCEL",null)')
write = method.find('putBoolean("lock_screen_assistant_enabled"')

assert save >= 0, "Widgets & Lock Screen must expose SAVE"
assert cancel >= 0, "Widgets & Lock Screen must expose CANCEL"
assert write >= 0, "SAVE must persist lock_screen_assistant_enabled"
assert write > save, "lock-screen runtime preference must not be written before SAVE"
assert method.count('putBoolean("lock_screen_assistant_enabled"') == 1, "lock-screen preference must have one staged SAVE write"
print("lock-screen SAVE/CANCEL contract: PASS")
PY
