import pytest

from jarvis_orchestrator.app import app


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
