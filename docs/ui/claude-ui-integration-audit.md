# Claude UI integration audit

Baseline: `feature/product-local-cortex` at `a5f866202fe75ff8ce242801f98e863a895e511e`.

## Canonical source

Claude's Drive README identifies the authoritative UI/UX as the published Claude Artifact `a7271792-25ce-4ebe-a24c-ffbd65680e4d` (`jarvis-live.html`, ~220 KB), with the Drive `app-icon/` vector assets and `jarvis_brand_colors.xml` as production-safe Android resources.

Charles has additionally supplied a ZIP named `JarvisAndroidV1.1withclaudeui (1)(1).zip` / equivalent. This ZIP is the preferred canonical source for implementation because it is intended to contain the complete UI package.

Current retrieval audit: the ZIP is not discoverable through File Library/current-conversation file search, the connected Google Drive project folder, Slack file search, or the GitHub repository. The Claude Artifact URL is documented but cannot be fetched through the available public web route in this runtime. Do not substitute an approximation.

## Current Android presentation boundary

`MainActivity.buildCurrentShell()` still constructs the full-screen UI programmatically. It owns:

- brand header
- central speak control
- status/response panel
- decision/approval buttons
- media controls
- overflow menu
- typed-command dialog
- runtime presentation delivery

The presentation shell is therefore separable from the existing brain/runtime behavior. Claude UI integration should replace only the visual composition and event binding while retaining the existing `AndroidBrainRuntime`, speech/TTS lifecycle, `RuntimeSurfacePresentation`, approval actions, media actions, Settings navigation, Assistant-role onboarding, notification-awareness entry point, accessibility entry point, and typed-command fallback.

## Required control mapping

Every canonical visible control must map to a real Android handler or be truthfully disabled. At minimum preserve mappings for:

- primary microphone/listen action -> `listen()`
- textual command fallback -> existing typed-command path
- Settings -> `SettingsActivity`
- AI Providers -> existing Settings provider configuration, including Free Local AI/Ollama
- Developer Options -> `DeveloperSettingsActivity`
- Notes/Memory -> `NotesActivity`
- Help/Features -> `CommandsActivity`
- Assistant role -> existing role request flow
- Notification awareness -> Android notification-listener settings
- Device control -> Android accessibility settings
- approval/reject/retry/cancel actions -> current `RuntimeSurfaceAction` dispatch
- media previous/play-pause/next -> current media key dispatch

## State mapping

The canonical visual states must be driven by real runtime state, not timers or decorative animation only:

- idle
- listening
- transcribing/heard
- thinking/processing
- responding/speaking
- approval required
- action executing
- success
- error
- remote-goal progress/cancel/retry where present

## Integration rule

Do not modify passive-wake internals or orchestrator code from this branch. Preserve the existing UI implementation in Git history and replace the live presentation only after Charles has been given a previewable canonical rendering when technically possible.
