from pathlib import Path


def test_ci_proves_authenticated_management_request_across_non_loopback_boundary():
    root = Path(__file__).parents[2]
    workflow = (root / ".github" / "workflows" / "orchestrator-tests.yml").read_text(
        encoding="utf-8"
    )

    assert "JARVIS_BIND_ADDRESS=0.0.0.0" in workflow
    assert "host.docker.internal:host-gateway" in workflow
    assert "http://host.docker.internal:8000/v1/goals" in workflow
    assert "Authorization: Bearer ci-token" in workflow
    assert "remote boundary smoke test" in workflow
