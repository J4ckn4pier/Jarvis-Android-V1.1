# JARVIS Brain Core

This directory is the standalone, Android-independent executive core for JARVIS. APK/UI work is intentionally frozen while the brain is developed and verified.

## Clean-room rule

Google Assistant is a behavioral benchmark only. This core contains no Google code, assets, strings, binaries, models, or implementation details.

## Responsibilities

- persistent conversation sessions after a wake phrase
- semantic intent/goal interpretation instead of literal command matching
- typed tool registry and aliases
- multi-step planning
- approval gates for consequential actions
- execution with bounded retry/recovery
- context carryover across turns
- provider-independent reasoning fallback
- future memory, prediction/attention, and telephony adapters

## Current acceptance gates

The executable acceptance suite verifies these product failures no longer map to the old `no framework` behavior:

- `Hey Jarvis, how are you?` is conversational
- follow-up turns work without repeating `Jarvis`
- `phone app` resolves to the dialer capability
- `find me a place to eat for dinner tonight` becomes a discovery/ranking plan
- restaurant reservation phone requests decompose into business resolution, conversational call, and report-back, with an approval stop before external speech
- unknown open-ended goals route to reasoning rather than hard failure
- recent conversational context carries into follow-ups
- explicit sleep ends the active conversation window
- semantic tool aliases resolve consistently
- consequential tools cannot run without approval
- approval is single-use
- retryable tool failures are retried once

Run locally with Java 17+:

```bash
./run-tests.sh
```

This is not yet the finished assistant brain. Remaining major gates include a real local reasoning provider, durable/temporal memory, richer dialogue state, predictive attention, live place/search adapters, passive audio wake/VAD, and a production telephony-agent transport.
