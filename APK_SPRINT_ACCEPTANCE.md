# JARVIS Android APK — fixed sprint acceptance checklist

This file freezes the denominator used for Charles-facing completion percentages during the APK sprint.
A box may be checked only after behavior is evidenced through a freshly compiled APK / Android emulator, except where the item explicitly requires Samsung hardware. Source or unit-test existence alone does not count.

1. [x] Canonical JARVIS Live visual identity is present in the compiled home UI; the old single-dot HUD is not the visible core.
2. [x] Shipped APK contains no Scripted Scenarios / Demo / Sandbox developer controls; assistant visuals react to real runtime state.
3. [x] Existing shared JARVIS brain is the production command path behind the live UI.
4. [x] Exact regression `I'm good. Can you do me a favor and open settings, please?` opens JARVIS Settings from the compiled APK.
5. [x] Generic `open <app>` routing works without guessing ambiguous app names.
6. [ ] Phone/dialer and approved contact-call handoff work; autonomous remote phone-agent conversation is excluded from APK runtime.
7. [x] Text-message approval flow and email-draft flow work through Android surfaces.
8. [ ] Calendar reading and calendar-event draft flow work.
9. [ ] Timer, alarm, and reminder-draft flows work.
10. [ ] Navigation, web search, and place-discovery routing work.
11. [ ] Flashlight command path works on a compatible Android device/emulator capability boundary without fabricated success.
12. [ ] Volume and media command paths work without fabricated resulting-state claims.
13. [ ] Notification-awareness path works when Android notification access is enabled.
14. [ ] Accessibility/screen-read/navigation controls work when Android accessibility access is enabled; consequential click/type remain approval-gated.
15. [ ] Persistent memory/context and multi-turn memory ingestion work through the Android runtime.
16. [x] Assistant-role onboarding/invocation path is present and can launch JARVIS through Android Assistant intents.
17. [ ] Multi-turn clarification/approval/recovery continuity works through the compiled Android surface.
18. [ ] `Jarvis` / `Hey Jarvis` passive wake path uses an enabled real detector/model path rather than a disabled detector stub; final acoustic behavior is Samsung-hardware validation when emulator audio cannot prove it.
19. [ ] Separate orchestration protocol and autonomous two-way remote phone-agent runtime are not wired into the shipped APK; source/history remain preserved.
20. [ ] One final APK installs, launches, passes the fixed smoke matrix, is staged as a single artifact, and has an exact branch/commit/SHA-256 recorded.

**Completion percentage = checked items / 20 × 100.**

Current compiled-proof checkpoint: **35% (7/20)** at Android code head `e82ed2c224875d417413c9c77a69d21d032711c4`, Android build run `33337321067`. Item 4 is evidenced by the exact runtime input `I'm good. Can you do me a favor and open settings, please?`, `ACTION_DONE`, `Opened JARVIS settings.`, and a stable resumed `SettingsActivity` on Android 16. Item 5 is evidenced by the compiled command `open JARVIS` returning `Opened Jarvis.` with `com.jarvis.mobile/.MainActivity` resumed; shared duplicate-label contracts separately require ambiguity to fail closed rather than guess. Item 7 is evidenced by the installed APK reaching `AWAITING_APPROVAL` for a text request, rendering APPROVE/CANCEL controls, safely cancelling back to IDLE without execution, plus the production email compose adapter producing and capturing the expected encoded recipient, decoded recipient, subject and body review draft on Android 16. Item 16 is evidenced by assigning the Android ASSISTANT role to `com.jarvis.mobile`, verifying the bound `VoiceInteractionService`, requesting a real JARVIS voice session, observing `JARVIS_SESSION_SERVICE_NEW_SESSION` and `JARVIS_ASSISTANT_READY`, and capturing the shown assistant UI. The same exact-head run also passed compile/lint/signature/integrity checks, Android 16 install/launch, real dialer handoff, physical media-stream volume change, timer, alarm, remote submit/reconnect/approval/result/cancel emulator checks, evidence upload, and single-APK staging; those partial proofs do not check broader checklist items until every clause of each item is evidenced. Exact staged APK SHA-256 from that run: `e109afb2ea8c90fd7f5a0571238c48754b9cbbca36fc1f6e307f5281978c5727`. This records only items with complete exact fresh emulator evidence; it is not a claim that the remaining behavior is absent or broken.
