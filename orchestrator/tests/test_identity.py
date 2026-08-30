from __future__ import annotations

import pytest

from jarvis_orchestrator.identity import Authenticator, scope_session_id


def test_multi_principal_tokens_resolve_to_distinct_principals(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a","bob":"token-b"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)

    auth = Authenticator.from_env()

    assert auth.authenticate("token-a").principal_id == "alice"
    assert auth.authenticate("token-b").principal_id == "bob"
    assert auth.authenticate("wrong") is None


def test_duplicate_multi_principal_tokens_are_rejected(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"shared-token","bob":"shared-token"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)

    with pytest.raises(ValueError, match="API tokens must be unique"):
        Authenticator.from_env()


def test_multi_principal_tokens_with_edge_whitespace_are_rejected(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":" token-a"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)

    with pytest.raises(ValueError, match="API tokens must not have leading or trailing whitespace"):
        Authenticator.from_env()


def test_legacy_single_token_maps_to_owner_principal(monkeypatch):
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)
    monkeypatch.setenv("JARVIS_API_TOKEN", "legacy-secret")

    auth = Authenticator.from_env()

    principal = auth.authenticate("legacy-secret")
    assert principal is not None
    assert principal.principal_id == "owner"


def test_scope_session_id_is_stable_and_principal_specific():
    alice_primary = scope_session_id("alice", "primary")
    bob_primary = scope_session_id("bob", "primary")

    assert alice_primary == scope_session_id("alice", "primary")
    assert alice_primary != bob_primary
    assert alice_primary.endswith(":primary")
    assert "alice" not in alice_primary
