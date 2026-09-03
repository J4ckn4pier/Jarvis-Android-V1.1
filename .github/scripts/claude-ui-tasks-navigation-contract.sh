#!/bin/sh
set -eu

ROUTER="android/app/src/main/java/com/jarvis/mobile/ui/ClaudeUiActionRouter.java"

grep -q 'ACTION_TASKS_PROJECTS = "tasks_projects"' "$ROUTER"
grep -q 'case ACTION_TASKS_PROJECTS:' "$ROUTER"
grep -q 'new Intent(activity, TasksProjectsActivity.class)' "$ROUTER"
grep -q '\\"tasks_projects\\"' "$ROUTER"

echo "Claude UI tasks/projects navigation contract GREEN"
