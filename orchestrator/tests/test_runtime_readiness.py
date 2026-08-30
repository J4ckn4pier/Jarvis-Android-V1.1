from __future__ import annotations

import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module


class FailingRuntimeReadiness:
    async def check_ready(self) -> bool:
        raise RuntimeError("worker offline")


@pytest.mark.asyncio
async def test_ready_returns_503_when_configured_runtime_is_unavailable(monkeypatch):
    monkeypatch.setattr(app_module.app.state, "valkey", None, raising=False)
    monkeypatch.setattr(app_module.app.state, "runtime", FailingRuntimeReadiness(), raising=False)

    with pytest.raises(HTTPException) as exc:
        await app_module.ready()

    assert exc.value.status_code == 503
    assert exc.value.detail == "Worker runtime unavailable"
