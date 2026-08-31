#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

path = Path('android/app/src/main/java/com/jarvis/mobile/assistant/AndroidOnDeviceWakeWordDetector.java')
source = path.read_text()
marker = '@Override public void onEndOfSpeech() {'
if marker not in source:
    raise SystemExit('missing onEndOfSpeech callback')
body = source.split(marker, 1)[1].split('}', 1)[0]
if 'scheduleRestart(RESTART_DELAY_MS);' not in body:
    raise SystemExit('onEndOfSpeech must schedule passive-wake restart in case OEM recognition never delivers results/error')
print('wake end-of-speech recovery contract passed')
PY