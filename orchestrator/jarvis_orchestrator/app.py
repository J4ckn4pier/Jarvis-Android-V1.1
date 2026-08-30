from __future__ import annotations

import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, Query, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, Field

from .core import (
    InMemoryEventBus,
    InMemorySessionLockManager,
    Orchestrator,
    ValkeyEventBus,
    ValkeySessionLockManager,
)
from .identity import Authenticator, Principal, scope_session_id
from .runtime import InMemoryAgentContextStore, ValkeyAgentContextStore, build_runtime


def _authenticator() -> Authenticator:
    return Authenticator.from_env()


def _bearer_token(authorization: str | None) -> str | None:
    return authorization.removeprefix("Bearer ") if authorization else None


def _principal_for_token(token: str | None) -> Principal | None:
    # Zero-configuration developer mode remains usable for the standalone
    # prototype. Once any credential is configured, authentication is required.
    if not os.getenv("JARVIS_API_KEYS_JSON") and not os.getenv("JARVIS_API_TOKEN"):
        return Principal(principal_id="owner")
    return _authenticator().authenticate(token)


def _require_http_auth(authorization: str | None) -> Principal:
    principal = _principal_for_token(_bearer_token(authorization))
    if principal is None:
        raise HTTPException(status_code=401, detail="Unauthorized")
    return principal


def _scoped_session(principal: Principal, public_session_id: str) -> str:
    return scope_session_id(principal.principal_id, public_session_id)


async def _run_lifecycle_operation(operation: str, session_id: str) -> bool:
    handler = getattr(app.state.runtime, operation, None)
    if not callable(handler):
        raise HTTPException(
            status_code=501,
            detail=f"Configured runtime does not support session {operation}",
        )
    changed = bool(await handler(session_id))
    if not changed:
        raise HTTPException(status_code=404, detail="Worker session not found")
    return changed


class Command(BaseModel):
    text: str = Field(min_length=1, max_length=100_000)
    session_id: str = Field(default="primary", min_length=1, max_length=128)


@asynccontextmanager
async def lifespan(app: FastAPI):
    url = os.getenv("VALKEY_URL")
    if url:
        from redis.asyncio import Redis
        client = Redis.from_url(url)
        await client.ping()
        app.state.valkey = client
        app.state.bus = ValkeyEventBus(client)
        context_store = ValkeyAgentContextStore(client)
        session_locks = ValkeySessionLockManager(client)
    else:
        app.state.valkey = None
        app.state.bus = InMemoryEventBus()
        context_store = InMemoryAgentContextStore()
        session_locks = InMemorySessionLockManager()

    runtime = build_runtime(context_store)
    app.state.runtime = runtime
    app.state.orchestrator = Orchestrator(
        app.state.bus,
        runtime,
        session_locks=session_locks,
    )
    yield

    close_runtime = getattr(runtime, "aclose", None)
    if close_runtime:
        await close_runtime()
    if app.state.valkey:
        await app.state.valkey.aclose()


app = FastAPI(title="JARVIS Orchestrator", version="0.7.0", lifespan=lifespan)


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "state_backend": "valkey" if app.state.valkey else "memory",
        "runtime": os.getenv("JARVIS_RUNTIME", "echo"),
        "session_locking": "valkey" if app.state.valkey else "memory",
    }


@app.post("/v1/command")
async def command(body: Command, authorization: str | None = Header(default=None)):
    principal = _require_http_auth(authorization)
    internal_session_id = _scoped_session(principal, body.session_id)
    result = await app.state.orchestrator.submit(body.text, internal_session_id)
    result["session_id"] = body.session_id
    return result


@app.get("/v1/sessions/{session_id}/events")
async def event_history(
    session_id: str,
    limit: int = Query(default=100, ge=1, le=1000),
    authorization: str | None = Header(default=None),
):
    """Recover state missed while a phone/desktop client was disconnected."""
    principal = _require_http_auth(authorization)
    internal_session_id = _scoped_session(principal, session_id)
    events = await app.state.bus.history(internal_session_id, limit)
    return {"session_id": session_id, "events": [
        {
            "session_id": session_id,
            "task_id": event.task_id,
            "active_layer": event.active_layer,
            "neurons_firing": event.neurons_firing,
            "agent_ops_status": event.agent_ops_status,
            "sequence": event.sequence,
            "timestamp": event.timestamp,
        }
        for event in events
    ]}


@app.post("/v1/sessions/{session_id}/reset")
async def reset_session(
    session_id: str,
    authorization: str | None = Header(default=None),
):
    """Reset the configured worker's conversation while preserving JARVIS session identity."""
    principal = _require_http_auth(authorization)
    await _run_lifecycle_operation("reset", _scoped_session(principal, session_id))
    return {"session_id": session_id, "reset": True}


@app.delete("/v1/sessions/{session_id}")
async def terminate_session(
    session_id: str,
    authorization: str | None = Header(default=None),
):
    """Terminate the configured worker session and clear its runtime context mapping."""
    principal = _require_http_auth(authorization)
    await _run_lifecycle_operation("terminate", _scoped_session(principal, session_id))
    return {"session_id": session_id, "terminated": True}


@app.websocket("/v1/events")
async def events(ws: WebSocket):
    principal = _principal_for_token(ws.query_params.get("token"))
    if principal is None:
        await ws.close(code=4401)
        return

    public_session_id = str(ws.query_params.get("session_id", "")).strip()
    if not public_session_id:
        await ws.close(code=4400)
        return
    internal_session_id = _scoped_session(principal, public_session_id)

    await ws.accept()
    try:
        async for event in app.state.bus.subscribe():
            if event.session_id != internal_session_id:
                continue
            await ws.send_json({
                "session_id": public_session_id,
                "task_id": event.task_id,
                "active_layer": event.active_layer,
                "neurons_firing": event.neurons_firing,
                "agent_ops_status": event.agent_ops_status,
                "sequence": event.sequence,
                "timestamp": event.timestamp,
            })
    except (WebSocketDisconnect, RuntimeError):
        return


@app.websocket("/v1/input")
async def input_socket(ws: WebSocket):
    """Phone/desktop text stream. Audio/STT plugs into this same submit() path after transcription."""
    principal = _principal_for_token(ws.query_params.get("token"))
    if principal is None:
        await ws.close(code=4401)
        return
    await ws.accept()
    try:
        while True:
            message = await ws.receive_json()
            text = str(message.get("text", "")).strip()
            if not text:
                await ws.send_json({"error": "text is required"})
                continue
            public_session_id = str(message.get("session_id", "primary"))
            internal_session_id = _scoped_session(principal, public_session_id)
            result = await app.state.orchestrator.submit(text, internal_session_id)
            result["session_id"] = public_session_id
            await ws.send_json(result)
    except WebSocketDisconnect:
        return
