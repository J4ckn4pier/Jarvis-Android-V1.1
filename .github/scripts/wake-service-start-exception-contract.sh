#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

path = Path('android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java')
source = path.read_text()
needle = 'wakeWordDetector.start(this::showWakeSession)'
if needle not in source:
    raise SystemExit('missing passive wake detector start call')
window = source[max(0, source.index(needle)-500):source.index(needle)+700]
if 'catch (RuntimeException' not in window:
    raise SystemExit('voice service must catch detector start RuntimeException so an OEM speech-stack failure cannot kill passive wake startup')
if 'main.postDelayed(passiveWakeRetry' not in window:
    raise SystemExit('voice service must schedule passive wake retry after detector start RuntimeException')
print('wake service start-exception recovery contract passed')
PY