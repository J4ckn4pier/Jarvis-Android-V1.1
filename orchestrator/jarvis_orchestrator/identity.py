from __future__ import annotations

import hashlib
import hmac
import json
import os
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class Principal:
    principal_id: str


class Authenticator:
    """Resolve API bearer tokens to stable JARVIS principals."""

    def __init__(self, credentials: dict[str, str]):
        self._credentials = dict(credentials)

    @classmethod
    def from_env(cls) -> "Authenticator":
        raw_multi = os.getenv("JARVIS_API_KEYS_JSON", "").strip()
        if raw_multi:
            parsed = json.loads(raw_multi)
            if not isinstance(parsed, dict):
                raise ValueError("JARVIS_API_KEYS_JSON must be a JSON object")
            credentials: dict[str, str] = {}
            for principal_id, token in parsed.items():
                if not isinstance(principal_id, str) or not principal_id.strip():
                    raise ValueError("principal ids must be non-empty strings")
                if not isinstance(token, str) or not token:
                    raise ValueError("API tokens must be non-empty strings")
                credentials[principal_id] = token
            return cls(credentials)

        legacy_token = os.getenv("JARVIS_API_TOKEN", "").strip()
        return cls({"owner": legacy_token} if legacy_token else {})

    def authenticate(self, token: str | None) -> Principal | None:
        if not token:
            return None
        for principal_id, expected_token in self._credentials.items():
            if hmac.compare_digest(token, expected_token):
                return Principal(principal_id=principal_id)
        return None


def scope_session_id(principal_id: str, session_id: str) -> str:
    """Namespace a client session without exposing the principal id in storage keys."""

    if not principal_id:
        raise ValueError("principal_id must not be empty")
    if not session_id:
        raise ValueError("session_id must not be empty")
    principal_scope = hashlib.sha256(principal_id.encode("utf-8")).hexdigest()[:24]
    return f"p:{principal_scope}:{session_id}"
