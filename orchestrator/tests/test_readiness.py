import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module


class HealthyValkey:
    async def ping(self):
        return True


class BrokenValkey:
    async def ping(self):
        raise ConnectionError("valkey unavailable")


@pytest.mark.asyncio
async def test_ready_checks_live_valkey_and_reports_safe_runtime_configuration(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a","bob":"token-b"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.setenv("JARVIS_RUNTIME", "echo")
    monkeypatch.setattr(app_module.app.state, "valkey", HealthyValkey(), raising=False)

    result = await app_module.ready()

    assert result == {
        "status": "ready",
        "state_backend": "valkey",
        "runtime": "echo",
        "session_locking": "valkey",
        "auth_mode": "multi-principal",
    }


@pytest.mark.asyncio
async def test_ready_fails_when_configured_valkey_is_not_reachable(monkeypatch):
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.setattr(app_module.app.state, "valkey", BrokenValkey(), raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.ready()

    assert exc.value.status_code == 503
    assert exc.value.detail == "State backend unavailable"


@pytest.mark.asyncio
async def test_ready_reports_open_development_mode_without_credentials(monkeypatch):
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.delenv("JARVIS_RUNTIME", raising=False)
    monkeypatch.setattr(app_module.app.state, "valkey", None, raising=False)

    result = await app_module.ready()

    assert result["status"] == "ready"
    assert result["state_backend"] == "memory"
    assert result["session_locking"] == "memory"
    assert result["auth_mode"] == "open-development"


@pytest.mark.asyncio
async def test_health_distinguishes_liveness_from_dependency_readiness(monkeypatch):
    monkeypatch.setenv("JARVIS_RUNTIME", "agent-zero")
    monkeypatch.setattr(app_module.app.state, "valkey", BrokenValkey(), raising=False)

    result = await app_module.health()

    assert result["status"] == "ok"
    assert result["state_backend"] == "valkey"
    assert result["runtime"] == "agent-zero"
    assert result["session_locking"] == "valkey"
    assert result["checks_dependencies"] is False


@pytest.mark.asyncio
async def test_ready_reports_dependency_check_contract(monkeypatch):
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    monkeypatch.setattr(app_module.app.state, "valkey", HealthyValkey(), raising=False)
    monkeypatch.setattr(app_module.app.state, "runtime", object(), raising=False)

    result = await app_module.ready()

    assert result["checks_dependencies"] is True
