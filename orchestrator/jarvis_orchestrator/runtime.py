from __future__ import annotations

import os
from typing import Protocol

import httpx

from .core import AgentRuntime, EchoRuntime


class AgentContextStore(Protocol):
    async def get(self, session_id: str) -> str | None: ...
    async def set(self, session_id: str, context_id: str, ttl_seconds: int | None = None) -> None: ...


class InMemoryAgentContextStore:
    def __init__(self) -> None:
        self._contexts: dict[str, str] = {}

    async def get(self, session_id: str) -> str | None:
        return self._contexts.get(session_id)

    async def set(self, session_id: str, context_id: str, ttl_seconds: int | None = None) -> None:
        self._contexts[session_id] = context_id


class ValkeyAgentContextStore:
    KEY_PREFIX = "brain:agent-zero:context:"

    def __init__(self, client) -> None:
        self.client = client

    async def get(self, session_id: str) -> str | None:
        value = await self.client.get(f"{self.KEY_PREFIX}{session_id}")
        if isinstance(value, bytes):
            return value.decode()
        return value

    async def set(self, session_id: str, context_id: str, ttl_seconds: int | None = None) -> None:
        kwargs = {"ex": ttl_seconds} if ttl_seconds else {}
        await self.client.set(f"{self.KEY_PREFIX}{session_id}", context_id, **kwargs)


class AgentZeroRuntime:
    """Thin adapter over Agent Zero's supported external HTTP API.

    JARVIS owns stable session identity. Agent Zero owns worker chat context. The
    mapping between those two identifiers lives in an injected context store so
    phone/desktop clients never depend on Agent Zero internals.
    """

    MESSAGE_PATH = "/api/api_message"

    def __init__(
        self,
        base_url: str,
        api_key: str,
        context_store: AgentContextStore,
        *,
        lifetime_hours: int = 24,
        project_name: str | None = None,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.context_store = context_store
        self.lifetime_hours = lifetime_hours
        self.project_name = project_name
        self._owns_client = client is None
        self.client = client or httpx.AsyncClient(base_url=self.base_url, timeout=300.0)

    async def execute(self, text: str, session_id: str) -> str:
        context_id = await self.context_store.get(session_id)
        payload: dict[str, object] = {
            "message": text,
            "lifetime_hours": self.lifetime_hours,
        }
        if context_id:
            payload["context_id"] = context_id
        elif self.project_name:
            payload["project_name"] = self.project_name

        response = await self.client.post(
            self.MESSAGE_PATH,
            headers={"X-API-KEY": self.api_key},
            json=payload,
        )
        response.raise_for_status()
        body = response.json()

        new_context_id = body.get("context_id")
        if new_context_id:
            await self.context_store.set(
                session_id,
                str(new_context_id),
                ttl_seconds=self.lifetime_hours * 3600,
            )

        answer = body.get("response")
        if answer is None:
            raise RuntimeError("Agent Zero response did not include 'response'")
        return str(answer)

    async def aclose(self) -> None:
        if self._owns_client:
            await self.client.aclose()


def build_runtime(context_store: AgentContextStore) -> AgentRuntime:
    mode = os.getenv("JARVIS_RUNTIME", "echo").strip().lower()
    if mode == "echo":
        return EchoRuntime()
    if mode not in {"agent-zero", "agent_zero"}:
        raise RuntimeError(f"Unsupported JARVIS_RUNTIME: {mode}")

    base_url = os.getenv("AGENT_ZERO_URL", "").strip()
    api_key = os.getenv("AGENT_ZERO_API_KEY", "").strip()
    if not base_url:
        raise RuntimeError("AGENT_ZERO_URL is required when JARVIS_RUNTIME=agent-zero")
    if not api_key:
        raise RuntimeError("AGENT_ZERO_API_KEY is required when JARVIS_RUNTIME=agent-zero")

    lifetime_hours = int(os.getenv("AGENT_ZERO_LIFETIME_HOURS", "24"))
    project_name = os.getenv("AGENT_ZERO_PROJECT") or None
    return AgentZeroRuntime(
        base_url=base_url,
        api_key=api_key,
        context_store=context_store,
        lifetime_hours=lifetime_hours,
        project_name=project_name,
    )
