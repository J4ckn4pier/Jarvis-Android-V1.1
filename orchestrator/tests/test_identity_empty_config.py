from __future__ import annotations

import pytest

from jarvis_orchestrator.identity import Authenticator


def test_multi_principal_configuration_rejects_empty_credentials(monkeypatch):
    """An explicitly configured multi-user mode must have at least one usable identity."""
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", "{}")
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)

    with pytest.raises(ValueError, match="at least one principal"):
        Authenticator.from_env()
