# JARVIS Android V1.1 — Prototype Release Scope

This branch is the installable Android prototype lane.

## Included

- JARVIS Android UI and assistant overlay.
- Shared prefrontal/executive brain used by the Android assistant.
- Conversation, memory, clarification, interruption, approval and safe recovery behavior that is already integrated into the app.
- Ordinary Android assistant actions such as launching apps, dialer/call handoff, navigation, timers/alarms, reminders, calendar, messaging/email composition, media, flashlight, notifications and configured research/provider bridges where available.
- Persisted application settings and integrated Android capability adapters already present in the product.

## Deferred from prototype acceptance

- Autonomous two-way conversational telephone-agent transport. Ordinary dialer/call handoff is still included.
- The separate JARVIS Orchestrator project/protocol and its worker-agent runtime. That project remains separate and can be connected to the assistant later through an explicit integration boundary.
- Future advanced backend agents and services that are not required for the Android assistant prototype.

## Non-destructive preservation policy

Nothing is permanently deleted to make this prototype. The full development history and deferred implementations remain preserved on their existing development branches/history. This branch changes what is considered a prototype release blocker; it does not destroy deferred work.

If a future cleanup would normally delete or replace an artifact, preserve the old artifact in an archive/quarantine or versioned branch/location and record why it was superseded before proceeding.

## Release meaning

“Final prototype” means a real installable, integrated Android beta suitable for hands-on use and testing. It does not mean the eventual commercial JARVIS feature set is complete. Deferred orchestration and autonomous telephone-agent capabilities are explicitly not blockers for this prototype.
