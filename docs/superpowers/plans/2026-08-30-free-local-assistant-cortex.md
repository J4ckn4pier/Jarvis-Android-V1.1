# Free Local Assistant Cortex Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make JARVIS understand arbitrary natural language with a zero-metered-cost local model, retain the existing Android action/approval layer, and expose a continued-conversation lifecycle for Claude's frontend.

**Architecture:** Keep deterministic reflexes only as a latency optimization. Any utterance not confidently handled locally goes to a local OpenAI-compatible cortex (Ollama/llama.cpp), which receives conversation context and the full tool catalog and returns either a natural reply or a validated structured plan. The shared runtime remains the only component allowed to execute tools or approvals. Conversation mode remains active after each response/action and exposes whether the surface should reopen listening.

**Tech Stack:** Java, Android VoiceInteractionService, existing brain-core, Ollama/llama.cpp OpenAI-compatible HTTP APIs, JSON-schema plan proposals.

**Spec:** Approved in conversation: zero token/API billing, general natural-language understanding, existing Android hands, long-term memory, continued conversation.

## Global Constraints

- No paid or metered AI API is required for the default path.
- Preserve provider-neutral boundaries so hosted providers can be added later.
- Do not replace Android action safety/approval gates with model decisions.
- Preserve existing work non-destructively.
- Claude owns frontend/UI work; backend exposes stable state only.

---

### Task 1: Local cortex defaults and discovery

**Files:**
- Modify: `android/app/src/main/java/com/jarvis/mobile/brain/providers/CortexProviderFactory.java`
- Modify: `android/app/src/main/java/com/jarvis/mobile/brain/providers/OpenAiCompatibleChatProvider.java`
- Test: `android/app/src/test/java/com/jarvis/mobile/brain/providers/LocalCortexConfigurationTest.java`

**Interfaces:**
- Consumes: `CortexProviderFactory.create(Context)` and existing `OpenAiCompatibleChatProvider`.
- Produces: a local no-key OpenAI-compatible configuration that can target Ollama/llama.cpp without token billing.

- [ ] Write a failing test proving a local-compatible provider can be configured without an API key and that localhost/LAN endpoints normalize correctly.
- [ ] Run the provider test and confirm RED for the missing behavior.
- [ ] Implement local-compatible defaults without changing hosted-provider behavior.
- [ ] Run provider tests and confirm GREEN.
- [ ] Commit.

### Task 2: Model-first general language fallback

**Files:**
- Modify: `android/app/src/main/java/com/jarvis/mobile/brain/providers/ProviderSharedPlanSchema.java`
- Modify: `android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java`
- Test: `brain-core/src/test/java/com/jarvis/brain/GeneralLanguageRoutingTest.java`

**Interfaces:**
- Consumes: `ReasoningRequest(utterance, context, tools.specs())`.
- Produces: free-form conversation answers or shared-tool plans; deterministic reflexes remain fast-path only.

- [ ] Write failing tests for a natural wrapped request and a conversation-only request reaching the reasoning cortex instead of returning a missing-cortex canned response when local cortex is configured.
- [ ] Run tests and confirm RED.
- [ ] Strengthen the provider system prompt so the model interprets ordinary conversational language, uses tools only when useful, and never treats the tool list as the universe of understandable requests.
- [ ] Keep runtime plan validation and approvals unchanged.
- [ ] Run brain/provider tests and confirm GREEN.
- [ ] Commit.

### Task 3: Continued-conversation backend state

**Files:**
- Modify: `brain-core/src/main/java/com/jarvis/brain/ConversationSession.java`
- Modify: `brain-core/src/main/java/com/jarvis/brain/BrainResponse.java` or add a focused presentation/session-state type if needed.
- Modify: `android/app/src/main/java/com/jarvis/mobile/brain/RuntimeSurfacePresentation.java`
- Test: `brain-core/src/test/java/com/jarvis/brain/ContinuedConversationTest.java`

**Interfaces:**
- Consumes: completed assistant turn/action and existing `ConversationSession` activity.
- Produces: explicit `continueListening`/session-active state for the frontend/voice surface.

- [ ] Write failing tests proving a wake/invocation opens a conversation, normal follow-ups do not require another wake word, completion requests continued listening, and `stop listening`/`that's all` closes it.
- [ ] Run tests and confirm RED.
- [ ] Implement the smallest explicit session-state signal needed by Claude's frontend and voice layer.
- [ ] Run tests and confirm GREEN.
- [ ] Commit.

### Task 4: Release-path verification

**Files:**
- Modify only if a failing verification identifies a concrete defect.

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces: evidence that arbitrary conversation, natural commands, approvals, and follow-up listening all traverse one runtime.

- [ ] Run brain-core tests.
- [ ] Run Android compile/lint/unit tests.
- [ ] Run an integration scenario: wake/invoke -> conversational question -> natural wrapped device action -> follow-up modification without wake word -> end conversation.
- [ ] Verify no paid API key is required for the local-compatible path.
- [ ] Commit only fixes demonstrated by failing verification.
