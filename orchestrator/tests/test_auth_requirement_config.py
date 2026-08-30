from __future__ import annotations

import pytest

from jarvis_orchestrator.app import _validate_auth_configuration


def test_rejects_unrecognized_auth_requirement_value(monkeypatch):
    monkeypatch.setenv("JARVIS_REQUIRE_AUTH", "tru")
    monkeypatch.setenv("JARVIS_API_TOKEN", "configured-secret")
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)

    with pytest.raises(RuntimeError, match="JARVIS_REQUIRE_AUTH must be one of"):
        _validate_auth_configuration()
