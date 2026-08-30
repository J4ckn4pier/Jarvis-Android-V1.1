# JARVIS Android V1.1

Android 16 / Samsung Galaxy S26 baseline for the private JARVIS assistant.

The active integrated development line is `brain-first-v1`. The repository contains a normal checked-in Android project under `android/` plus the shared provider-independent brain under `brain-core/`; source is not generated from bootstrap payloads.

## Current integrated baseline

- Android Assistant role with a custom `VoiceInteractionSession`
- Voice input through Android `SpeechRecognizer` and spoken responses through Android `TextToSpeech`
- Original clean-room JARVIS interface, launcher/notification iconography, brand palette, overlays, and secondary screens
- Shared conversational brain with bounded dialogue/session context, clarification resume, approval/recovery state, and provider-independent reasoning contracts
- Local/provider reasoning support with typed tool plans, validation, fallback/recovery boundaries, and truthful tool outcomes
- Contact calling and arbitrary-number dialing, SMS, and email composition with ambiguity-safe named-target resolution
- Calendar event composition, reminders, alarms, timers, app launching, web search, navigation, flashlight, volume, and media controls
- Notification capture/context and summaries
- Durable settings, activity, device, routine, list, music-queue, connection, and long-term memory stores
- Optional Accessibility screen reading, semantic taps, typing, scrolling, Back, and Home
- Proactive/executive planning and outcome-follow-up foundations with explicit privacy/safety gates
- Wake/attention, barge-in, and adaptive endpointing contracts with Android integration coverage

## Verification

GitHub Actions runs the shared brain acceptance suite and a separate Android/APK pipeline for `brain-first-v1` changes. The Android pipeline compiles and lints the app, assembles the APK, validates clean-room/release-integrity contracts, installs on an Android 16 emulator, and runs runtime smoke coverage.

The authoritative brain acceptance workflow intentionally remains red after its independently reachable gates because one finished-product requirement is still unresolved: **fully autonomous conversational phone calling is blocked until production has a legitimate two-way PSTN audio transport**. Launching the normal dialer is not treated as a substitute for that missing listen/reason/speak transport.

## Build

```bash
cd android
gradle :app:assembleDebug --stacktrace
```

## Product constraints

- Clean-room implementation: removed licensed donor code/assets must not return to production.
- Consequential actions retain explicit approval boundaries.
- Reasoning/tool architecture remains provider-independent.
- Incremental recurring service cost remains `$0` unless Charles explicitly changes that requirement.
- A passing intermediate checkpoint is not the definition of finished; unresolved finished-product requirements remain visible rather than being bypassed to manufacture a green badge.
