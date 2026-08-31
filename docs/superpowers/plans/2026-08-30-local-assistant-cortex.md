# Local Assistant Cortex Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make JARVIS understand arbitrary natural speech through a free local/self-hosted AI cortex, preserve Android action safety, and maintain continued conversation without repeated wake words.

**Architecture:** Keep the existing shared brain and Android tool registry. Promote the OpenAI-compatible local provider (Ollama-compatible) into a first-class local AI mode, route unhandled natural language through it before remote-goal delegation, and verify the complete path with an emulator-local OpenAI-compatible stub. The voice session already owns a timed continued-conversation loop; preserve and test that behavior rather than replacing it.

**Tech Stack:** Java, Android VoiceInteractionSession/SpeechRecognizer/TTS, existing brain-core plan runtime, HTTP JSON OpenAI-compatible provider, Ollama local inference, GitHub Actions emulator smoke tests.

**Spec:** `docs/superpowers/specs/2026-08-30-local-assistant-cortex-design.md`

## Global Constraints
- No required metered/token-billed API.
- Existing approval gates remain authoritative.
- Existing Android actions are reused, not duplicated.
- Claude frontend files are not modified.
- No destructive deletion of existing work.

---

### Task 1: Prove arbitrary natural language reaches a local cortex and executes a real Android tool

**Files:**
- Create: `.github/scripts/local-assistant-intelligence-smoke.sh`
- Create: `android/app/src/debug/java/com/jarvis/mobile/brain/providers/LocalAssistantIntelligenceTestReceiver.java`
- Modify: `android/app/src/debug/AndroidManifest.xml`
- Modify: `.github/workflows/build-apk.yml`

**Interfaces:**
- Consumes: `AndroidBrainRuntime.handlePresentation(String,double)` and the OpenAI-compatible provider boundary.
- Produces: emulator evidence that a non-regex natural phrase is converted by the cortex into `open_jarvis_settings` and executed through the Android tool registry.

- [ ] Write the failing emulator smoke script first; it starts a local OpenAI-compatible stub and broadcasts a natural-language request.
- [ ] Run CI and verify RED because the new debug receiver/broadcast path does not exist.
- [ ] Add the debug receiver and manifest entry, configuring the temporary local endpoint/model before invoking the real Android brain runtime.
- [ ] Run CI and verify the stub receives tool schema + conversation envelope and Android logs `LOCAL_ASSISTANT_INTELLIGENCE_PASS` only after the real settings tool succeeds.
- [ ] Commit.

### Task 2: Make local AI a first-class zero-token configuration

**Files:**
- Modify: `android/app/src/main/java/com/jarvis/mobile/brain/providers/CortexProviderFactory.java`
- Modify: `android/app/src/main/java/com/jarvis/mobile/DeveloperSettingsActivity.java`
- Modify: `android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java`
- Modify/Test: `.github/scripts/cortex-settings-smoke.sh`

**Interfaces:**
- Produces: `MODE_LOCAL_AI = "local_ai"`, default local model suggestion `gpt-oss:20b`, OpenAI-compatible endpoint behavior with no API key requirement.

- [ ] Extend settings smoke expectations first so CI fails until `Local AI (Ollama-compatible)` exists.
- [ ] Add local-AI mode in factory and settings; instantiate `OpenAiCompatibleChatProvider` with empty key.
- [ ] Show honest status: configured only when model + allowed endpoint are present; do not label deterministic mode as AI.
- [ ] Verify settings smoke and compile.
- [ ] Commit.

### Task 3: Stop remote orchestration from hijacking ordinary conversation

**Files:**
- Modify: `android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java`
- Create/Modify: `.github/scripts/local-cortex-smoke.sh`

**Interfaces:**
- Local reasoning must be attempted for normal `REASONING_REQUIRED` turns when local AI is configured.
- Remote goals remain separately resumable but are not the default conversational reasoning router.

- [ ] Add a failing smoke assertion that a configured local cortex is used even if a remote connection exists.
- [ ] Replace `remoteReasoningOrLocal` as the default reasoning router with local cortex reasoning; retain remote-goal coordinator APIs for explicit long-running work.
- [ ] Verify local cortex smoke and remote-goal contract tests both pass.
- [ ] Commit.

### Task 4: Route generic conversation to the cortex instead of canned phrase fallbacks

**Files:**
- Modify: `brain-core/src/main/java/com/jarvis/brain/BrainEngine.java`
- Modify/Create: `brain-core/src/test/java/com/jarvis/brain/BrainEngineTest.java` or the repository's existing equivalent test file.

**Interfaces:**
- High-confidence device reflexes remain deterministic.
- Generic conversation/follow-ups return `REASONING_REQUIRED` so the cortex gets full context.

- [ ] Write failing unit tests for a generic conversational follow-up and an indirect request that should require reasoning.
- [ ] Remove canned generic follow-up interception while retaining wake/sleep/session control and fast deterministic commands.
- [ ] Run brain-core tests.
- [ ] Commit.

### Task 5: Verify continued conversation lifecycle

**Files:**
- Modify: `.github/scripts/overlay-decision-smoke.sh` or create `.github/scripts/continued-conversation-smoke.sh`
- Modify only if required: `android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java`

**Interfaces:**
- After response/TTS completion, `scheduleNextListen()` reopens recognition while the conversation window is open.
- No second wake word is required for the follow-up.

- [ ] Add failing/explicit smoke evidence for two sequential assistant turns in one visible voice session.
- [ ] Change production code only if the evidence shows a lifecycle defect.
- [ ] Verify conversation end phrases stop automatic relistening.
- [ ] Commit.

### Task 6: Full verification and handoff

**Files:**
- No feature expansion.

- [ ] Run brain-core tests.
- [ ] Run Android compile/lint.
- [ ] Run local cortex intelligence smoke.
- [ ] Run continued conversation smoke.
- [ ] Run existing Android action/approval/remote-goal contract gates.
- [ ] Build and stage one APK only after all above pass.
- [ ] Record exact commit/run/artifact evidence; do not call the product complete until physical Samsung acceptance follows.