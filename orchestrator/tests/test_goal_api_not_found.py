from __future__ import annotations

import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module


class MissingProjectService:
    async def status(self, owner_id, project_id):
        raise KeyError(project_id)

    async def events(self, owner_id, project_id, after_event_id, limit):
        raise KeyError(project_id)

    async def cancel(self, owner_id, project_id):
        raise KeyError(project_id)

    async def result(self, owner_id, project_id):
        raise KeyError(project_id)


@pytest.fixture
def missing_project_service(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    service = MissingProjectService()
    monkeypatch.setattr(app_module.app.state, "goal_service", service, raising=False)
    return service


@pytest.mark.asyncio
@pytest.mark.parametrize("route", ["status", "events", "cancel", "result"])
async def test_missing_or_other_owner_project_is_consistently_not_found(
    missing_project_service,
    route,
):
    calls = {
        "status": lambda: app_module.project_status(
            "project-secret", authorization="Bearer token-a"
        ),
        "events": lambda: app_module.project_event_history(
            "project-secret",
            limit=100,
            after_event_id=None,
            authorization="Bearer token-a",
        ),
        "cancel": lambda: app_module.cancel_project(
            "project-secret", authorization="Bearer token-a"
        ),
        "result": lambda: app_module.project_result(
            "project-secret", authorization="Bearer token-a"
        ),
    }

    with pytest.raises(HTTPException) as exc:
        await calls[route]()

    assert exc.value.status_code == 404
    assert exc.value.detail == "Project not found"
