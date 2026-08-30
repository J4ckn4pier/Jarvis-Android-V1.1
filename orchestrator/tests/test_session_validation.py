from __future__ import annotations

import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module


class RecordingHistoryBus:
    def __init__(self) -> None:
        self.calls: list[tuple[str, int]] = []

    async def history(self, session_id: str, limit: int):
        self.calls.append((session_id, limit))
        return []


class RecordingLifecycleRuntime:
    def __init__(self) -> None:
        self.reset_calls: list[str] = []
        self.terminate_calls: list[str] = []

    async def reset(self, session_id: str) -> bool:
        self.reset_calls.append(session_id)
        return True

    async def terminate(self, session_id: str) -> bool:
        self.terminate_calls.append(session_id)
        return True


@pytest.mark.asyncio
async def test_event_history_rejects_oversized_public_session_before_storage(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    bus = RecordingHistoryBus()
    monkeypatch.setattr(app_module.app.state, "bus", bus, raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.event_history("s" * 129, limit=100, authorization=None)

    assert exc.value.status_code == 422
    assert exc.value.detail == "session_id must be between 1 and 128 characters"
    assert bus.calls == []


@pytest.mark.asyncio
async def test_reset_rejects_oversized_public_session_before_runtime(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    runtime = RecordingLifecycleRuntime()
    monkeypatch.setattr(app_module.app.state, "runtime", runtime, raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.reset_session("s" * 129, authorization=None)

    assert exc.value.status_code == 422
    assert runtime.reset_calls == []


@pytest.mark.asyncio
async def test_terminate_rejects_oversized_public_session_before_runtime(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    runtime = RecordingLifecycleRuntime()
    monkeypatch.setattr(app_module.app.state, "runtime", runtime, raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.terminate_session("s" * 129, authorization=None)

    assert exc.value.status_code == 422
    assert runtime.terminate_calls == []
