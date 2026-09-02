#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
ROUTER="$ROOT/android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"
PREVIEW="$ROOT/android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiPreviewActivity.java"

[ -f "$ROUTER" ] || { echo "FAIL: ClaudeUiActionRouter.java is missing" >&2; exit 1; }
[ -f "$PREVIEW" ] || { echo "FAIL: ClaudeUiPreviewActivity.java is missing" >&2; exit 1; }

require() {
  pattern="$1"
  file="$2"
  message="$3"
  grep -Fq "$pattern" "$file" || { echo "FAIL: $message" >&2; exit 1; }
}

# Stable presentation-only actions exposed to the canonical Claude HTML.
require 'ACTION_LISTEN = "listen"' "$ROUTER" "listen action is not defined"
require 'ACTION_SETTINGS = "settings"' "$ROUTER" "settings action is not defined"
require 'ACTION_DEVELOPER_OPTIONS = "developer_options"' "$ROUTER" "developer options action is not defined"
require 'ACTION_HELP = "help"' "$ROUTER" "help action is not defined"
require 'ACTION_NOTES = "notes"' "$ROUTER" "notes action is not defined"
require 'ACTION_DEFAULT_ASSISTANT = "default_assistant"' "$ROUTER" "default-assistant action is not defined"
require 'ACTION_NOTIFICATION_ACCESS = "notification_access"' "$ROUTER" "notification-access action is not defined"
require 'ACTION_ACCESSIBILITY = "accessibility"' "$ROUTER" "accessibility action is not defined"

# The bridge must dispatch through existing Android surfaces rather than duplicate backend logic.
require 'SettingsActivity.class' "$ROUTER" "settings does not route to the existing SettingsActivity"
require 'DeveloperSettingsActivity.class' "$ROUTER" "developer options do not route to the existing DeveloperSettingsActivity"
require 'CommandsActivity.class' "$ROUTER" "help does not route to the existing CommandsActivity"
require 'NotesActivity.class' "$ROUTER" "notes do not route to the existing NotesActivity"
require 'Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS' "$ROUTER" "notification access is not routed through Android settings"
require 'Settings.ACTION_ACCESSIBILITY_SETTINGS' "$ROUTER" "accessibility is not routed through Android settings"
require 'Intent.ACTION_ASSIST' "$ROUTER" "listen does not reuse the existing assistant invocation path"

# Preview is allowed to expose the bridge only to the exact packaged canonical asset.
require 'addJavascriptInterface' "$PREVIEW" "preview does not attach the action bridge"
require 'ClaudeUiActionRouter' "$PREVIEW" "preview does not use the shared Claude UI action router"

# No arbitrary web navigation: exact packaged source only.
require 'setAllowUniversalAccessFromFileURLs(false)' "$PREVIEW" "preview permits universal file URL access"
require 'setAllowFileAccessFromFileURLs(false)' "$PREVIEW" "preview permits file-to-file URL access"

echo "CLAUDE_UI_ACTION_BRIDGE_CONTRACT_PASS"
