#!/bin/sh
set -eu
RUNTIME="android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"
[ -f "$RUNTIME" ] || exit 1
# Terminal remote projects remain durable on the backend, but Android must stop treating them as
# active reconnect targets after the terminal outcome has been successfully surfaced to the user.
python3 - "$RUNTIME" <<'PY'
import sys
text=open(sys.argv[1],encoding='utf-8').read()
completed_start=text.index('if (snapshot.completed())')
failed_start=text.index('if ("failed".equalsIgnoreCase', completed_start)
completed=text[completed_start:failed_start]
if 'state.clearProject();' not in completed:
    raise SystemExit('Completed remote result is not consumed; it would be repeated on every foreground')
failed_end=text.index('remoteProjectVisible = true;', failed_start)
terminal=text[failed_start:failed_end]
if 'state.clearProject();' not in terminal:
    raise SystemExit('Failed/cancelled remote outcome is not consumed; it would be repeated on every foreground')
print('Remote goal terminal-result consumption contract GREEN')
PY
