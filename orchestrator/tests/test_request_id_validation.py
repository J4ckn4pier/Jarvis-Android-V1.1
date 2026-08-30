from __future__ import annotations

import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module


def test_request_id_rejects_edge_whitespace_instead_of_normalizing():
    with pytest.raises(HTTPException) as exc:
        app_module._validated_request_id(" phone-42 ")

    assert exc.value.status_code == 422
    assert exc.value.detail == "request_id must not have leading or trailing whitespace"


def test_request_id_preserves_exact_valid_value():
    assert app_module._validated_request_id("phone-42") == "phone-42"
