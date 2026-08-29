from __future__ import annotations

import asyncio
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, Field

from .core import EchoRuntime, InMemoryEventBus, Orchestrator, ValkeyEventBus


def _authorized(token: str | None) -> bool:
    expected = os.getenv("JARVIS_API_TOKEN")
    return not expected or token == expected


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
    else:
        app.state.valkey = None
        app.state.bus = InMemoryEventBus()
    app.state.orchestrator = Orchestrator(app.state.bus, EchoRuntime())
    yield
    if app.state.valkey:
        await app.state.valkey.aclose()


app = FastAPI(title="JARVIS Orchestrator", version="0.1.0", lifespan=lifespan)


@app.get("/health")
async def health():
    return {"status": "ok", "state_backend": "valkey" if app.state.valkey else "memory"}


@app.post("/v1/command")
async def command(body: Command, authorization: str | None = Header(default=None)):
    token = authorization.removeprefix("Bearer ") if authorization else None
    if not _authorized(token):
        raise HTTPException(status_code=401, detail="Unauthorized")
    return await app.state.orchestrator.submit(body.text, body.session_id)


@app.websocket("/v1/events")
async def events(ws: WebSocket):
    token = ws.query_params.get("token")
    if not _authorized(token):
        await ws.close(code=4401)
        return
    await ws.accept()
    try:
        async for event in app.state.bus.subscribe():
            await ws.send_json({
                "session_id": event.session_id,
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
    token = ws.query_params.get("token")
    if not _authorized(token):
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
            session_id = str(message.get("session_id", "primary"))
            result = await app.state.orchestrator.submit(text, session_id)
            await ws.send_json(result)
    except WebSocketDisconnect:
        return
