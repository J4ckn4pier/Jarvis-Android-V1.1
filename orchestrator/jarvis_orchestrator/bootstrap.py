from __future__ import annotations

import base64
import hashlib
import secrets
from pathlib import Path

_REQUIRED_KEYS = (
    "JARVIS_API_TOKEN",
    "JARVIS_RUNTIME",
    "AGENT_ZERO_RUNTIME_ID",
    "AGENT_ZERO_API_KEY",
    "AGENT_ZERO_URL",
    "AGENT_ZERO_LIFETIME_HOURS",
)


def compute_agent_zero_api_key(runtime_id: str, username: str = "", password: str = "") -> str:
    """Match Agent Zero's current helpers.settings.create_auth_token() algorithm."""
    digest = hashlib.sha256(f"{runtime_id}:{username}:{password}".encode()).digest()
    return base64.urlsafe_b64encode(digest).decode().replace("=", "")[:16]


def _read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw_line in path.read_text().splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def ensure_env(path: Path = Path(".env")) -> dict[str, str]:
    """Create only missing prototype settings/secrets; never rotate existing values."""
    existing = _read_env(path)
    generated: dict[str, str] = {}

    if not existing.get("JARVIS_API_TOKEN"):
        generated["JARVIS_API_TOKEN"] = secrets.token_urlsafe(32)

    if not existing.get("JARVIS_RUNTIME"):
        generated["JARVIS_RUNTIME"] = "agent-zero"

    runtime_id = existing.get("AGENT_ZERO_RUNTIME_ID") or secrets.token_hex(16)
    if not existing.get("AGENT_ZERO_RUNTIME_ID"):
        generated["AGENT_ZERO_RUNTIME_ID"] = runtime_id

    if not existing.get("AGENT_ZERO_API_KEY"):
        generated["AGENT_ZERO_API_KEY"] = compute_agent_zero_api_key(runtime_id)

    if not existing.get("AGENT_ZERO_URL"):
        generated["AGENT_ZERO_URL"] = "http://agent-zero"

    if not existing.get("AGENT_ZERO_LIFETIME_HOURS"):
        generated["AGENT_ZERO_LIFETIME_HOURS"] = "24"

    if generated:
        previous = path.read_text() if path.exists() else ""
        separator = "" if not previous or previous.endswith("\n") else "\n"
        appended = "".join(f"{key}={value}\n" for key, value in generated.items())
        path.write_text(previous + separator + appended)

    # This file contains live JARVIS and worker credentials. Do not rely on the
    # caller's umask: make it owner-readable/writable only on POSIX hosts.
    path.chmod(0o600)

    final = {**existing, **generated}
    return {key: final[key] for key in _REQUIRED_KEYS}
