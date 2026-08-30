# JARVIS Android APK — fixed sprint acceptance checklist

This file freezes the denominator used for Charles-facing completion percentages during the APK sprint.
A box may be checked only after behavior is evidenced through a freshly compiled APK / Android emulator, except where the item explicitly requires Samsung hardware. Source or unit-test existence alone does not count.

1. [ ] Canonical JARVIS Live visual identity is present in the compiled home UI; the old single-dot HUD is not the visible core.
2. [ ] Shipped APK contains no Scripted Scenarios / Demo / Sandbox developer controls; assistant visuals react to real runtime state.
3. [ ] Existing shared JARVIS brain is the production command path behind the live UI.
4. [ ] Exact regression `I'm good. Can you do me a favor and open settings, please?` opens JARVIS Settings from the compiled APK.
5. [ ] Generic `open <app>` routing works without guessing ambiguous app names.
6. [ ] Phone/dialer and approved contact-call handoff work; autonomous remote phone-agent conversation is excluded from APK runtime.
7. [ ] Text-message approval flow and email-draft flow work through Android surfaces.
8. [ ] Calendar reading and calendar-event draft flow work.
9. [ ] Timer, alarm, and reminder-draft flows work.
10. [ ] Navigation, web search, and place-discovery routing work.
11. [ ] Flashlight command path works on a compatible Android device/emulator capability boundary without fabricated success.
12. [ ] Volume and media command paths work without fabricated resulting-state claims.
13. [ ] Notification-awareness path works when Android notification access is enabled.
14. [ ] Accessibility/screen-read/navigation controls work when Android accessibility access is enabled; consequential click/type remain approval-gated.
15. [ ] Persistent memory/context and multi-turn memory ingestion work through the Android runtime.
16. [ ] Assistant-role onboarding/invocation path is present and can launch JARVIS through Android Assistant intents.
17. [ ] Multi-turn clarification/approval/recovery continuity works through the compiled Android surface.
18. [ ] `Jarvis` / `Hey Jarvis` passive wake path uses an enabled real detector/model path rather than a disabled detector stub; final acoustic behavior is Samsung-hardware validation when emulator audio cannot prove it.
19. [ ] Separate orchestration protocol and autonomous two-way remote phone-agent runtime are not wired into the shipped APK; source/history remain preserved.
20. [ ] One final APK installs, launches, passes the fixed smoke matrix, is staged as a single artifact, and has an exact branch/commit/SHA-256 recorded.

**Completion percentage = checked items / 20 × 100.**
