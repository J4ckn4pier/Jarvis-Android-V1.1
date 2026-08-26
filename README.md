# JARVIS Android V1.1 Donor Beta

Android 16 / Samsung Galaxy S26 baseline for the private JARVIS assistant.

This repository contains a normal checked-in Android project under `android/`. It does not generate
source from shell scripts or compressed bootstrap payloads.

## Implemented V1 baseline

- Android Assistant role and a custom `VoiceInteractionSession`
- Voice input through Android `SpeechRecognizer`
- Spoken responses through Android `TextToSpeech`
- Donor-themed full interface and assistant popup
- Contact calls, arbitrary-number dialing, SMS and email composition
- Calendar event creation, natural day/time parsing and contact-email invite resolution
- Alarms, timers, app launching, web search and navigation
- Flashlight, volume and media controls
- Notification capture and summaries
- SQLite memories, tasks and event history
- Optional Accessibility screen reading, semantic taps, typing, scrolling, Back and Home
- Android 16 runtime permission and settings flows

## Build

```bash
cd android
gradle :app:assembleDebug --stacktrace
```

GitHub Actions builds, verifies and uploads `app-debug.apk` on every push to `main`.

## Scope

This is a deterministic assistant baseline, not the finished distributed JARVIS mind. Embedded local
LLM inference, ambient no-wake-word attention, owner voice identification, proactive routines and the
Windows body remain later milestones.

The legacy visual resources are temporary private-beta donor assets and must be replaced with original
commercial artwork before public distribution.
