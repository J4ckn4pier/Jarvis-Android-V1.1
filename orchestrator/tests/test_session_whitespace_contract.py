from __future__ import annotations

import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module


class RecordingOrchestrator:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str, str | None]] = []

    async def submit(self, text: str, session_id: str, request_id: str | None = None):
        self.calls.append((text, session_id, request_id))
        return {"task_id": "task-1", "response": "ok"}


@pytest.mark.asyncio
async def test_command_rejects_session_id_with_edge_whitespace_before_storage(monkeypatch):
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    orchestrator = RecordingOrchestrator()
    monkeypatch.setattr(app_module.app.state, "orchestrator", orchestrator, raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.command(
            app_module.Command(text="hello", session_id=" primary "),
            authorization=None,
        )

    assert exc.value.status_code == 422
    assert exc.value.detail == "session_id must not have leading or trailing whitespace"
    assert orchestrator.calls == []


def test_public_session_validator_rejects_edge_whitespace():
    with pytest.raises(HTTPException) as exc:
        app_module._validated_public_session_id(" primary ")

    assert exc.value.status_code == 422
    assert exc.value.detail == "session_id must not have leading or trailing whitespace"
