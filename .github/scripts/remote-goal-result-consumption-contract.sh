#!/bin/sh
set -eu
RUNTIME="android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"
[ -f "$RUNTIME" ] || exit 1
# A completed remote project remains durable on the backend, but Android must stop treating it as an active reconnect target after successfully fetching its result.
python3 - "$RUNTIME" <<'PY'
import sys
text=open(sys.argv[1],encoding='utf-8').read()
start=text.index('if (snapshot.completed())')
end=text.index('if ("failed".equalsIgnoreCase', start)
block=text[start:end]
if 'state.clearProject();' not in block:
    raise SystemExit('Completed remote result is not consumed; it would be repeated on every foreground')
print('Remote goal result consumption contract GREEN')
PY
