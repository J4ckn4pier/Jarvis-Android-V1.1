import asyncio

import pytest

from jarvis_orchestrator.app import app
from jarvis_orchestrator.goal_planner import GoalRequest


def test_packaged_fastapi_app_mounts_apk_management_contract():
    routes = {
        (route.path, method)
        for route in app.routes
        for method in getattr(route, "methods", ())
    }

    assert ("/v1/goals", "POST") in routes
    assert ("/v1/projects/{project_id}", "GET") in routes
    assert ("/v1/projects/{project_id}/events", "GET") in routes
    assert ("/v1/projects/{project_id}/approvals/{approval_id}", "POST") in routes
    assert ("/v1/projects/{project_id}/cancel", "POST") in routes
    assert ("/v1/projects/{project_id}/result", "GET") in routes


@pytest.mark.asyncio
async def test_packaged_process_initializes_management_service_without_external_setup(monkeypatch):
    monkeypatch.delenv("VALKEY_URL", raising=False)
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.delenv("JARVIS_REQUIRE_AUTH", raising=False)
    monkeypatch.setenv("JARVIS_RUNTIME", "echo")

    async with app.router.lifespan_context(app):
        service = getattr(app.state, "goal_service", None)
        assert service is not None
        assert service.store is not None
        assert service.registry is not None


@pytest.mark.asyncio
async def test_packaged_goal_continues_without_client_connection_and_finishes_simple_work(monkeypatch):
    monkeypatch.delenv("VALKEY_URL", raising=False)
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.delenv("JARVIS_REQUIRE_AUTH", raising=False)
    monkeypatch.setenv("JARVIS_RUNTIME", "echo")

    async with app.router.lifespan_context(app):
        service = app.state.goal_service
        submitted = await service.submit(
            "owner-a",
            "primary",
            GoalRequest(
                owner_id="owner-a",
                session_id="primary",
                goal="prepare a short morning summary",
            ),
        )
        project_id = submitted["project_id"]

        # Submission is allowed to return before execution. The server, not the
        # phone connection, owns continuing the project after that point.
        for _ in range(20):
            status = await service.status("owner-a", project_id)
            if status["state"] == "complete":
                break
            await asyncio.sleep(0)

        assert status["state"] == "complete"
        result = await service.result("owner-a", project_id)
        assert "prepare a short morning summary" in result["result"]
