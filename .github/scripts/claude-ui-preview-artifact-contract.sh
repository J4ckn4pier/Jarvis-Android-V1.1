#!/bin/sh
set -eu

workflow=.github/workflows/claude-ui-preview-contract.yml

# A successful preview build is only useful to Charles if CI preserves the APK.
grep -q 'actions/upload-artifact@' "$workflow"
grep -q 'name: JARVIS-Claude-UI-Preview' "$workflow"
grep -q 'android/app/build/outputs/apk/debug/.*\.apk' "$workflow"
