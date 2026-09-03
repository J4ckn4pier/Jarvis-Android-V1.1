#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"

require() {
  pattern="$1"
  if ! grep -Fq "$pattern" "$ROUTER"; then
    echo "CLAUDE_UI_NAVIGATION_CONTRACT_FAIL missing: $pattern" >&2
    exit 1
  fi
}

# Canonical Claude navigation must route into existing production presentation surfaces.
require 'ACTION_MEMORY = "memory"'
require 'ACTION_ROUTINES = "routines"'
require 'ACTION_SKILLS = "skills"'
require 'ACTION_OVERLAYS = "overlays"'
require 'ACTION_ACTIVITY_FEED = "activity_feed"'
require 'ACTION_BROWSER = "browser"'
require 'ACTION_HUB = "hub"'

require 'new Intent(activity, MemoryActivity.class)'
require 'new Intent(activity, RoutinesActivity.class)'
require 'new Intent(activity, SkillsActivity.class)'
require 'new Intent(activity, OverlaysActivity.class)'
require 'new Intent(activity, ActivityFeedActivity.class)'
require 'new Intent(activity, BrowserActivity.class)'
require 'new Intent(activity, JarvisHubActivity.class)'

echo "CLAUDE_UI_NAVIGATION_CONTRACT_PASS"
