from __future__ import annotations

import pytest

from jarvis_orchestrator import app as app_module


class LifecycleRuntime:
    def __init__(self) -> None:
        self.reset_calls: list[str] = []

    async def reset(self, session_id: str) -> bool:
        self.reset_calls.append(session_id)
        return True


class RecordingLifecycleOrchestrator:
    def __init__(self) -> None:
        self.calls: list[str] = []

    async def run_session_operation(self, session_id: str, operation):
        self.calls.append(session_id)
        return await operation()


@pytest.mark.asyncio
async def test_lifecycle_endpoint_uses_orchestrator_session_serialization(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    runtime = LifecycleRuntime()
    orchestrator = RecordingLifecycleOrchestrator()
    monkeypatch.setattr(app_module.app.state, "runtime", runtime, raising=False)
    monkeypatch.setattr(app_module.app.state, "orchestrator", orchestrator, raising=False)

    result = await app_module.reset_session("primary", authorization=None)

    assert result == {"session_id": "primary", "reset": True}
    assert len(orchestrator.calls) == 1
    assert runtime.reset_calls == orchestrator.calls
