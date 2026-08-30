from __future__ import annotations

import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module


@pytest.mark.parametrize("authorization", ["secret", "Basic secret"])
def test_http_auth_requires_explicit_bearer_scheme(monkeypatch, authorization):
    monkeypatch.setenv("JARVIS_API_TOKEN", "secret")
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)

    with pytest.raises(HTTPException) as exc:
        app_module._require_http_auth(authorization)

    assert exc.value.status_code == 401


def test_http_auth_accepts_bearer_scheme_case_insensitively(monkeypatch):
    monkeypatch.setenv("JARVIS_API_TOKEN", "secret")
    monkeypatch.delenv("JARVIS_API_KEYS_JSON", raising=False)

    principal = app_module._require_http_auth("bearer secret")

    assert principal.principal_id == "owner"
