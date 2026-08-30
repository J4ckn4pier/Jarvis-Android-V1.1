from __future__ import annotations

import math
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, Query, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, Field

from .core import (
    IdempotencyConflict,
    InMemoryEventBus,
    InMemoryIdempotencyStore,
    InMemorySessionLockManager,
    Orchestrator,
    ValkeyEventBus,
    ValkeyIdempotencyStore,
    ValkeySessionLockManager,
    brain_event_id,
)
from .identity import Authenticator, Principal, scope_session_id
from .runtime import InMemoryAgentContextStore, ValkeyAgentContextStore, build_runtime


def _authenticator() -> Authenticator:
    return Authenticator.from_env()


def _auth_mode() -> str:
    if os.getenv("JARVIS_API_KEYS_JSON", "").strip():
        return "multi-principal"
    if os.getenv("JARVIS_API_TOKEN", "").strip():
        return "single-token"
    return "open-development"


def _auth_required() -> bool:
    return os.getenv("JARVIS_REQUIRE_AUTH", "").strip().lower() in {"1", "true", "yes", "on"}


def _validate_auth_configuration() -> None:
    mode = _auth_mode()
    if _auth_required() and mode == "open-development":
        raise RuntimeError(
            "JARVIS authentication is required but no API token or principal keys are configured"
        )
    if mode != "open-development":
        _authenticator()


def _session_lock_timeout_seconds() -> int:
    """Return a distributed lock lease long enough to cover one worker turn.

    Agent Zero's request timeout is configurable because local inference speed
    varies dramatically by hardware. The Valkey lock must outlive that request
    or another process could enter the same conversation while the first turn
    is still executing. Keep a one-minute cleanup margin after the HTTP timeout.
    """
    runtime_mode = os.getenv("JARVIS_RUNTIME", "echo").strip().lower()
    minimum = 300
    if runtime_mode in {"agent-zero", "agent_zero"}:
        timeout_raw = os.getenv("AGENT_ZERO_TIMEOUT_SECONDS", "300").strip()
        try:
            inference_timeout = float(timeout_raw)
        except ValueError as exc:
            raise RuntimeError("AGENT_ZERO_TIMEOUT_SECONDS must be a number") from exc
        if inference_timeout <= 0:
            raise RuntimeError("AGENT_ZERO_TIMEOUT_SECONDS must be greater than zero")
        minimum = math.ceil(inference_timeout) + 60

    explicit_raw = os.getenv("JARVIS_SESSION_LOCK_TIMEOUT_SECONDS", "").strip()
    if not explicit_raw:
        return minimum
    try:
        explicit = int(explicit_raw)
    except ValueError as exc:
        raise RuntimeError("JARVIS_SESSION_LOCK_TIMEOUT_SECONDS must be an integer") from exc
    if explicit <= 0:
        raise RuntimeError("JARVIS_SESSION_LOCK_TIMEOUT_SECONDS must be greater than zero")
    if explicit < minimum:
        raise RuntimeError(
            f"JARVIS session lock timeout must be at least {minimum} seconds for the configured runtime"
        )
    return explicit


def _bearer_token(authorization: str | None) -> str | None:
    return authorization.removeprefix("Bearer ") if authorization else None


def _websocket_token(ws: WebSocket) -> str | None:
    """Prefer an Authorization header so credentials do not need to live in URLs."""
    headers = getattr(ws, "headers", None)
    authorization = headers.get("authorization") if headers is not None else None
    if authorization:
        return _bearer_token(str(authorization))
    return ws.query_params.get("token")


def _principal_for_token(token: str | None) -> Principal | None:
    if not os.getenv("JARVIS_API_KEYS_JSON") and not os.getenv("JARVIS_API_TOKEN"):
        if _auth_required():
            return None
        return Principal(principal_id="owner")
    return _authenticator().authenticate(token)


def _require_http_auth(authorization: str | None) -> Principal:
    principal = _principal_for_token(_bearer_token(authorization))
    if principal is None:
        raise HTTPException(status_code=401, detail="Unauthorized")
    return principal


def _validated_public_session_id(session_id: str) -> str:
    if not session_id or len(session_id) > 128:
        raise HTTPException(
            status_code=422,
            detail="session_id must be between 1 and 128 characters",
        )
    return session_id


def _validated_request_id(request_id: object | None) -> str | None:
    if request_id is None:
        return None
    value = str(request_id).strip()
    if not value or len(value) > 128:
        raise HTTPException(
            status_code=422,
            detail="request_id must be between 1 and 128 characters",
        )
    return value


def _scoped_session(principal: Principal, public_session_id: str) -> str:
    return scope_session_id(principal.principal_id, public_session_id)


def _event_payload(event, public_session_id: str) -> dict[str, object]:
    """Stable wire representation used by both history and live telemetry.

    event_id lets phone/desktop clients merge a history replay with an already-open
    WebSocket stream after reconnect without rendering the same brain event twice.
    """
    return {
        "event_id": brain_event_id(event),
        "session_id": public_session_id,
        "task_id": event.task_id,
        "active_layer": event.active_layer,
        "neurons_firing": event.neurons_firing,
        "agent_ops_status": event.agent_ops_status,
        "sequence": event.sequence,
        "timestamp": event.timestamp,
    }


async def _submit(
    text: str,
    internal_session_id: str,
    request_id: str | None,
) -> dict[str, str]:
    try:
        return await app.state.orchestrator.submit(
            text,
            internal_session_id,
            request_id=request_id,
        )
    except IdempotencyConflict as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc


async def _run_lifecycle_operation(operation: str, session_id: str) -> bool:
    handler = getattr(app.state.runtime, operation, None)
    if not callable(handler):
        raise HTTPException(
            status_code=501,
            detail=f"Configured runtime does not support session {operation}",
        )

    async def invoke() -> bool:
        return bool(await handler(session_id))

    orchestrator = getattr(app.state, "orchestrator", None)
    serializer = getattr(orchestrator, "run_session_operation", None)
    changed = bool(await serializer(session_id, invoke)) if callable(serializer) else await invoke()
    if not changed:
        raise HTTPException(status_code=404, detail="Worker session not found")
    return changed


class Command(BaseModel):
    text: str = Field(min_length=1, max_length=100_000)
    session_id: str = Field(default="primary", min_length=1, max_length=128)
    request_id: str | None = Field(default=None, min_length=1, max_length=128)


@asynccontextmanager
async def lifespan(app: FastAPI):
    _validate_auth_configuration()

    url = os.getenv("VALKEY_URL")
    if url:
        from redis.asyncio import Redis
        client = Redis.from_url(url)
        await client.ping()
        app.state.valkey = client
        app.state.bus = ValkeyEventBus(client)
        context_store = ValkeyAgentContextStore(client)
        session_locks = ValkeySessionLockManager(
            client,
            timeout_seconds=_session_lock_timeout_seconds(),
        )
        idempotency = ValkeyIdempotencyStore(client)
    else:
        app.state.valkey = None
        app.state.bus = InMemoryEventBus()
        context_store = InMemoryAgentContextStore()
        session_locks = InMemorySessionLockManager()
        idempotency = InMemoryIdempotencyStore()

    runtime = build_runtime(context_store)
    app.state.runtime = runtime
    app.state.orchestrator = Orchestrator(
        app.state.bus,
        runtime,
        session_locks=session_locks,
        idempotency=idempotency,
    )
    yield

    close_runtime = getattr(runtime, "aclose", None)
    if close_runtime:
        await close_runtime()
    if app.state.valkey:
        await app.state.valkey.aclose()


app = FastAPI(title="JARVIS Orchestrator", version="0.13.0", lifespan=lifespan)


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "state_backend": "valkey" if app.state.valkey else "memory",
        "runtime": os.getenv("JARVIS_RUNTIME", "echo"),
        "session_locking": "valkey" if app.state.valkey else "memory",
    }


@app.get("/ready")
async def ready():
    valkey = getattr(app.state, "valkey", None)
    if valkey is not None:
        try:
            await valkey.ping()
        except Exception as exc:
            raise HTTPException(status_code=503, detail="State backend unavailable") from exc

    runtime = getattr(app.state, "runtime", None)
    runtime_check = getattr(runtime, "check_ready", None)
    if callable(runtime_check):
        try:
            await runtime_check()
        except Exception as exc:
            raise HTTPException(status_code=503, detail="Worker runtime unavailable") from exc

    using_valkey = valkey is not None
    return {
        "status": "ready",
        "state_backend": "valkey" if using_valkey else "memory",
        "runtime": os.getenv("JARVIS_RUNTIME", "echo"),
        "session_locking": "valkey" if using_valkey else "memory",
        "auth_mode": _auth_mode(),
    }


@app.post("/v1/command")
async def command(body: Command, authorization: str | None = Header(default=None)):
    principal = _require_http_auth(authorization)
    internal_session_id = _scoped_session(principal, body.session_id)
    result = await _submit(body.text, internal_session_id, body.request_id)
    result["session_id"] = body.session_id
    return result


@app.get("/v1/sessions/{session_id}/events")
async def event_history(
    session_id: str,
    limit: int = Query(default=100, ge=1, le=1000),
    after_event_id: str | None = Query(default=None, min_length=1, max_length=128),
    authorization: str | None = Header(default=None),
):
    public_session_id = _validated_public_session_id(session_id)
    principal = _require_http_auth(authorization)
    internal_session_id = _scoped_session(principal, public_session_id)

    # FastAPI resolves Query(None) before HTTP invocation, but direct unit callers
    # see the Query metadata object. Normalize that path and preserve compatibility
    # with EventBus implementations that predate cursor support when no cursor is used.
    cursor = after_event_id if isinstance(after_event_id, str) else None
    if cursor is None:
        events = await app.state.bus.history(internal_session_id, limit)
        return {
            "session_id": public_session_id,
            "events": [_event_payload(event, public_session_id) for event in events],
        }

    contains_event = getattr(app.state.bus, "contains_event", None)
    if callable(contains_event) and not await contains_event(internal_session_id, cursor):
        raise HTTPException(status_code=410, detail="Recovery cursor is no longer available")

    recovered = await app.state.bus.history(
        internal_session_id,
        limit + 1,
        after_event_id=cursor,
    )
    has_more = len(recovered) > limit
    events = recovered[:limit]
    next_event_id = brain_event_id(events[-1]) if events else cursor
    return {
        "session_id": public_session_id,
        "events": [_event_payload(event, public_session_id) for event in events],
        "next_event_id": next_event_id,
        "has_more": has_more,
    }


@app.post("/v1/sessions/{session_id}/reset")
async def reset_session(
    session_id: str,
    authorization: str | None = Header(default=None),
):
    public_session_id = _validated_public_session_id(session_id)
    principal = _require_http_auth(authorization)
    await _run_lifecycle_operation("reset", _scoped_session(principal, public_session_id))
    return {"session_id": public_session_id, "reset": True}


@app.delete("/v1/sessions/{session_id}")
async def terminate_session(
    session_id: str,
    authorization: str | None = Header(default=None),
):
    public_session_id = _validated_public_session_id(session_id)
    principal = _require_http_auth(authorization)
    await _run_lifecycle_operation("terminate", _scoped_session(principal, public_session_id))
    return {"session_id": public_session_id, "terminated": True}


@app.websocket("/v1/events")
async def events(ws: WebSocket):
    principal = _principal_for_token(_websocket_token(ws))
    if principal is None:
        await ws.close(code=4401)
        return

    public_session_id = str(ws.query_params.get("session_id", "")).strip()
    if not public_session_id or len(public_session_id) > 128:
        await ws.close(code=4400)
        return
    internal_session_id = _scoped_session(principal, public_session_id)

    await ws.accept()
    try:
        async for event in app.state.bus.subscribe():
            if event.session_id != internal_session_id:
                continue
            await ws.send_json(_event_payload(event, public_session_id))
    except (WebSocketDisconnect, RuntimeError):
        return


@app.websocket("/v1/input")
async def input_socket(ws: WebSocket):
    principal = _principal_for_token(_websocket_token(ws))
    if principal is None:
        await ws.close(code=4401)
        return
    await ws.accept()
    try:
        while True:
            message = await ws.receive_json()
            text = str(message.get("text", "")).strip()
            if not text or len(text) > 100_000:
                await ws.send_json({"error": "text must be between 1 and 100000 characters"})
                continue
            public_session_id = str(message.get("session_id", "primary")).strip()
            if not public_session_id or len(public_session_id) > 128:
                await ws.send_json({"error": "session_id must be between 1 and 128 characters"})
                continue
            raw_request_id = message.get("request_id")
            if raw_request_id is not None:
                request_id = str(raw_request_id).strip()
                if not request_id or len(request_id) > 128:
                    await ws.send_json({"error": "request_id must be between 1 and 128 characters"})
                    continue
            else:
                request_id = None
            internal_session_id = _scoped_session(principal, public_session_id)
            try:
                result = await app.state.orchestrator.submit(
                    text,
                    internal_session_id,
                    request_id=request_id,
                )
            except IdempotencyConflict as exc:
                await ws.send_json({"error": str(exc), "code": "request_id_conflict"})
                continue
            result["session_id"] = public_session_id
            await ws.send_json(result)
    except WebSocketDisconnect:
        return
