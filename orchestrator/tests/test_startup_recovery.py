from __future__ import annotations

import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module


class OfflineValkey:
    def __init__(self) -> None:
        self.ping_calls = 0
        self.closed = False

    async def ping(self) -> bool:
        self.ping_calls += 1
        raise ConnectionError("valkey unavailable")

    async def aclose(self) -> None:
        self.closed = True


def _offline_client(monkeypatch) -> OfflineValkey:
    client = OfflineValkey()
    monkeypatch.setattr("redis.asyncio.Redis.from_url", lambda _url: client)
    return client


@pytest.mark.asyncio
async def test_configured_valkey_can_be_offline_during_startup_and_recover_via_readiness(monkeypatch):
    """A transient state-backend outage must not kill the API process at startup.

    The configured Valkey backend remains authoritative; JARVIS should start with
    that client attached, report liveness normally, and expose the outage through
    /ready until Valkey returns rather than silently falling back to memory.
    """
    monkeypatch.setenv("VALKEY_URL", "redis://valkey:6379/0")
    monkeypatch.setenv("JARVIS_RUNTIME", "echo")
    monkeypatch.delenv("JARVIS_REQUIRE_AUTH", raising=False)
    client = _offline_client(monkeypatch)

    async with app_module.lifespan(app_module.app):
        assert app_module.app.state.valkey is client
        assert client.ping_calls == 0

        live = await app_module.health()
        assert live["status"] == "ok"
        assert live["state_backend"] == "valkey"

        with pytest.raises(HTTPException) as exc:
            await app_module.ready()
        assert exc.value.status_code == 503
        assert exc.value.detail == "State backend unavailable"
        assert client.ping_calls == 1

    assert client.closed is True
