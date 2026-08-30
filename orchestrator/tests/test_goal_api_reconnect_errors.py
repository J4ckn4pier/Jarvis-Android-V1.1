from __future__ import annotations

import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module


class ExpiredCursorService:
    async def events(self, owner_id, project_id, after_event_id, limit):
        raise KeyError(after_event_id)


@pytest.mark.asyncio
async def test_management_reconnect_reports_expired_cursor_as_gone(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.setattr(
        app_module.app.state,
        "goal_service",
        ExpiredCursorService(),
        raising=False,
    )

    with pytest.raises(HTTPException) as exc:
        await app_module.project_event_history(
            "project-1",
            limit=100,
            after_event_id="000000000007",
            authorization="Bearer token-a",
        )

    assert exc.value.status_code == 410
    assert exc.value.detail == "Recovery cursor is no longer available"
