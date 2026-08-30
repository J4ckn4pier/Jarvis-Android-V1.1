from __future__ import annotations

import pytest

from jarvis_orchestrator.identity import Authenticator


def test_multi_principal_configuration_rejects_empty_credentials(monkeypatch):
    """An explicitly configured multi-user mode must have at least one usable identity."""
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", "{}")
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)

    with pytest.raises(ValueError, match="at least one principal"):
        Authenticator.from_env()


def test_multi_principal_configuration_rejects_whitespace_padded_principal(monkeypatch):
    """Ownership namespaces must not depend on invisible whitespace in config."""
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{" alice ":"token-a"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)

    with pytest.raises(ValueError, match="leading or trailing whitespace"):
        Authenticator.from_env()


def test_multi_principal_configuration_rejects_malformed_json_cleanly(monkeypatch):
    """Deployment errors should identify the bad setting rather than leak JSON parser detail."""
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", "{broken")
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)

    with pytest.raises(ValueError, match="JARVIS_API_KEYS_JSON must be valid JSON"):
        Authenticator.from_env()
