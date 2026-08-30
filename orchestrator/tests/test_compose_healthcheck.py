from pathlib import Path


def test_orchestrator_container_healthcheck_uses_liveness_not_readiness():
    compose = (Path(__file__).parents[1] / "compose.yaml").read_text()
    orchestrator_service = compose.split("  orchestrator:\n", 1)[1].split("\nvolumes:\n", 1)[0]

    assert "    healthcheck:\n" in orchestrator_service
    assert "/health" in orchestrator_service
    assert "/ready" not in orchestrator_service
