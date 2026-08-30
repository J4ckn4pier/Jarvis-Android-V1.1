from __future__ import annotations

import os
from typing import Protocol
from urllib.parse import urlparse

import httpx

from .core import AgentRuntime, EchoRuntime


class WorkerUnavailableError(RuntimeError):
    """Stable runtime boundary for temporary worker transport/server failures."""


class AgentContextStore(Protocol):
    async def get(self, session_id: str) -> str | None: ...
    async def set(self, session_id: str, context_id: str, ttl_seconds: int | None = None) -> None: ...
    async def delete(self, session_id: str) -> None: ...


class InMemoryAgentContextStore:
    def __init__(self) -> None:
        self._contexts: dict[str, str] = {}

    async def get(self, session_id: str) -> str | None:
        return self._contexts.get(session_id)

    async def set(self, session_id: str, context_id: str, ttl_seconds: int | None = None) -> None:
        self._contexts[session_id] = context_id

    async def delete(self, session_id: str) -> None:
        self._contexts.pop(session_id, None)


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

    async def delete(self, session_id: str) -> None:
        await self.client.delete(f"{self.KEY_PREFIX}{session_id}")


class AgentZeroRuntime:
    """Thin adapter over Agent Zero's supported external HTTP API.

    JARVIS owns stable session identity. Agent Zero owns worker chat context. The
    mapping between those two identifiers lives in an injected context store so
    phone/desktop clients never depend on Agent Zero internals.
    """

    MESSAGE_PATH = "/api/api_message"
    RESET_PATH = "/api/api_reset_chat"
    TERMINATE_PATH = "/api/api_terminate_chat"
    HEALTH_PATH = "/api/health"

    def __init__(
        self,
        base_url: str,
        api_key: str,
        context_store: AgentContextStore,
        *,
        lifetime_hours: int = 24,
        project_name: str | None = None,
        timeout_seconds: float = 300.0,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.context_store = context_store
        self.lifetime_hours = lifetime_hours
        self.project_name = project_name
        self.timeout_seconds = timeout_seconds
        self._owns_client = client is None
        self.client = client or httpx.AsyncClient(base_url=self.base_url, timeout=timeout_seconds)

    @property
    def _headers(self) -> dict[str, str]:
        return {"X-API-KEY": self.api_key}

    def _payload(self, text: str, context_id: str | None) -> dict[str, object]:
        payload: dict[str, object] = {
            "message": text,
            "lifetime_hours": self.lifetime_hours,
        }
        if context_id:
            payload["context_id"] = context_id
        elif self.project_name:
            payload["project_name"] = self.project_name
        return payload

    async def _send(self, text: str, context_id: str | None) -> httpx.Response:
        try:
            return await self.client.post(
                self.MESSAGE_PATH,
                headers=self._headers,
                json=self._payload(text, context_id),
            )
        except httpx.RequestError as exc:
            raise WorkerUnavailableError("Agent Zero unavailable") from exc

    async def _lifecycle_post(self, path: str, context_id: str) -> httpx.Response:
        try:
            response = await self.client.post(
                path,
                headers=self._headers,
                json={"context_id": context_id},
            )
        except httpx.RequestError as exc:
            raise WorkerUnavailableError("Agent Zero unavailable") from exc
        self._raise_for_worker_status(response)
        return response

    @staticmethod
    def _context_not_found(response: httpx.Response) -> bool:
        if response.status_code != 404:
            return False
        try:
            return response.json().get("error") == "Context not found"
        except (ValueError, AttributeError):
            return False

    @staticmethod
    def _raise_for_worker_status(response: httpx.Response) -> None:
        if response.status_code >= 500:
            raise WorkerUnavailableError("Agent Zero unavailable")
        response.raise_for_status()

    async def check_ready(self) -> bool:
        response = await self.client.get(self.HEALTH_PATH, timeout=5.0)
        response.raise_for_status()
        return True

    async def execute(self, text: str, session_id: str) -> str:
        context_id = await self.context_store.get(session_id)
        response = await self._send(text, context_id)

        # Agent Zero contexts have finite lifetimes. If our persisted mapping
        # outlives the worker context, clear only that stale mapping and retry
        # the same request once as a fresh chat. Other HTTP failures propagate.
        if context_id and self._context_not_found(response):
            await self.context_store.delete(session_id)
            response = await self._send(text, None)

        self._raise_for_worker_status(response)
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

    async def reset(self, session_id: str) -> bool:
        context_id = await self.context_store.get(session_id)
        if not context_id:
            return False
        response = await self._lifecycle_post(self.RESET_PATH, context_id)
        if response.status_code == 404:
            await self.context_store.delete(session_id)
            return False
        return bool(response.json().get("success"))

    async def terminate(self, session_id: str) -> bool:
        context_id = await self.context_store.get(session_id)
        if not context_id:
            return False
        response = await self._lifecycle_post(self.TERMINATE_PATH, context_id)
        if response.status_code == 404:
            await self.context_store.delete(session_id)
            return False
        success = bool(response.json().get("success"))
        if success:
            await self.context_store.delete(session_id)
        return success

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
    parsed_url = urlparse(base_url)
    if parsed_url.scheme not in {"http", "https"} or not parsed_url.netloc:
        raise RuntimeError("AGENT_ZERO_URL must use http or https and include a host")
    if not api_key:
        raise RuntimeError("AGENT_ZERO_API_KEY is required when JARVIS_RUNTIME=agent-zero")

    lifetime_raw = os.getenv("AGENT_ZERO_LIFETIME_HOURS", "24").strip()
    try:
        lifetime_hours = int(lifetime_raw)
    except ValueError as exc:
        raise RuntimeError("AGENT_ZERO_LIFETIME_HOURS must be an integer") from exc
    if lifetime_hours <= 0:
        raise RuntimeError("AGENT_ZERO_LIFETIME_HOURS must be greater than zero")

    timeout_raw = os.getenv("AGENT_ZERO_TIMEOUT_SECONDS", "300").strip()
    try:
        timeout_seconds = float(timeout_raw)
    except ValueError as exc:
        raise RuntimeError("AGENT_ZERO_TIMEOUT_SECONDS must be a number") from exc
    if timeout_seconds <= 0:
        raise RuntimeError("AGENT_ZERO_TIMEOUT_SECONDS must be greater than zero")

    project_name = os.getenv("AGENT_ZERO_PROJECT") or None
    return AgentZeroRuntime(
        base_url=base_url,
        api_key=api_key,
        context_store=context_store,
        lifetime_hours=lifetime_hours,
        project_name=project_name,
        timeout_seconds=timeout_seconds,
    )
