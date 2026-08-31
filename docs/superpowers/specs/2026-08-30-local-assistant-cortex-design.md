# Free Local Assistant Cortex Design

## Goal
Build the first useful JARVIS as a Google-Assistant-class phone assistant with general natural-language understanding, continued conversation, long-term memory, and existing Android actions, with no metered/token-billed AI API requirement.

## Architecture
JARVIS uses a local/self-hosted language model as the default general reasoning cortex. The first production target is Ollama (or another OpenAI-compatible local server) running on user-owned hardware, reached through the existing provider-neutral OpenAI-compatible boundary. Deterministic Android reflexes remain only for high-confidence low-latency actions; they are not the language-understanding boundary.

Flow: speech recognition -> active conversation session -> deterministic high-confidence reflex when applicable, otherwise local AI cortex -> schema-valid answer or tool plan -> shared plan validation/approval -> Android action -> spoken result -> automatically reopen listening while the conversation window remains active.

## Requirements
- No required OpenAI, Gemini, Anthropic, or other metered token API.
- Default recommended model is a locally hosted open-weight model; initial reference is Ollama with `gpt-oss:20b` or another compatible model selected by the user.
- The Android client must support an OpenAI-compatible local endpoint without an API key.
- Arbitrary natural-language requests must reach the general cortex instead of being rejected because they do not match a phrase list.
- Existing Android tools remain the assistant's hands. Tool names do not constrain the natural language a user may speak.
- Provider output must remain schema validated; approval stays outside the model.
- Conversation context and durable memory are supplied to the cortex.
- After JARVIS answers or completes an action, listening automatically resumes while conversation mode is active. A new wake word is not required for each follow-up.
- Explicit end phrases or conversation timeout close the continued-conversation window.
- Long-running remote orchestration must not steal ordinary conversational requests from the local cortex.
- Existing work is preserved non-destructively.
- Claude-owned frontend work is not modified in this branch; the brain exposes the same runtime presentation states for frontend integration.

## Initial Scope
Included: ordinary conversation, contextual follow-ups, natural phrasing of existing Android actions, local model configuration, local model connection diagnostics, memory/context injection, continued conversation.

Deferred: autonomous phone-agent conversations, multi-agent business orchestration, smart-home expansion, proactive life-management expansion, and redesign of Claude's frontend.

## Acceptance
1. A natural phrase not present in deterministic regexes can be interpreted by the local cortex into an existing Android action.
2. A conversation-only question gets a model-generated answer rather than `I need a connected reasoning cortex` when local AI is configured.
3. A follow-up turn is sent with prior conversation context and does not require another wake word.
4. The local endpoint requires no API key and incurs no per-token provider charge when backed by user-owned Ollama/local inference.
5. Remote-goal configuration does not override ordinary local conversational reasoning.
6. Existing approval and Android-action safety gates still apply.