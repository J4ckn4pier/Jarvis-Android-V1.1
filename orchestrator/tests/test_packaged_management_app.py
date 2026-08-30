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
