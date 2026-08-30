"""Stable provider-neutral HTTP contracts for the JARVIS APK management plane."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from fastapi import Header, HTTPException, Query
from pydantic import BaseModel, Field


class GoalSubmission(BaseModel):
    goal: str = Field(min_length=1, max_length=100_000)
    session_id: str = Field(default="primary", min_length=1, max_length=128)
    constraints: tuple[str, ...] = ()
    acceptance_criteria: tuple[str, ...] = ()
    deadline: datetime | None = None


class ApprovalResponse(BaseModel):
    approved: bool
    response: str | None = Field(default=None, max_length=10_000)


_INTERNAL_KEYS = {
    "provider",
    "provider_id",
    "provider_name",
    "worker",
    "worker_id",
    "worker_ids",
    "workers",
    "agent",
    "agent_id",
    "agent_zero",
}


def _public_payload(value: Any) -> Any:
    """Keep worker/provider implementation details behind the JARVIS facade."""

    if isinstance(value, dict):
        return {
            key: _public_payload(item)
            for key, item in value.items()
            if str(key).lower() not in _INTERNAL_KEYS
        }
    if isinstance(value, list):
        return [_public_payload(item) for item in value]
    if isinstance(value, tuple):
        return tuple(_public_payload(item) for item in value)
    return value


def _validated_id(value: str, name: str) -> str:
    if not value or len(value) > 128:
        raise HTTPException(status_code=422, detail=f"{name} must be between 1 and 128 characters")
    if value != value.strip():
        raise HTTPException(status_code=422, detail=f"{name} must not have leading or trailing whitespace")
    return value


def install_goal_api(app_module) -> None:
    """Attach the management API to the existing FastAPI app exactly once."""

    if getattr(app_module, "_goal_api_installed", False):
        return

    async def _service():
        service = getattr(app_module.app.state, "goal_service", None)
        if service is None:
            raise HTTPException(status_code=503, detail="Management service unavailable")
        return service

    async def submit_goal(
        body: GoalSubmission,
        authorization: str | None = Header(default=None),
    ):
        principal = app_module._require_http_auth(authorization)
        session_id = app_module._validated_public_session_id(body.session_id)
        service = await _service()
        return _public_payload(await service.submit(principal.principal_id, session_id, body))

    async def project_status(
        project_id: str,
        authorization: str | None = Header(default=None),
    ):
        principal = app_module._require_http_auth(authorization)
        project_id = _validated_id(project_id, "project_id")
        service = await _service()
        return _public_payload(await service.status(principal.principal_id, project_id))

    async def project_event_history(
        project_id: str,
        limit: int = Query(default=100, ge=1, le=1000),
        after_event_id: str | None = Query(default=None, min_length=1, max_length=128),
        authorization: str | None = Header(default=None),
    ):
        principal = app_module._require_http_auth(authorization)
        project_id = _validated_id(project_id, "project_id")
        cursor = app_module._validated_after_event_id(after_event_id)
        service = await _service()
        return _public_payload(
            await service.events(principal.principal_id, project_id, cursor, limit)
        )

    async def project_approval(
        project_id: str,
        approval_id: str,
        body: ApprovalResponse,
        authorization: str | None = Header(default=None),
    ):
        principal = app_module._require_http_auth(authorization)
        project_id = _validated_id(project_id, "project_id")
        approval_id = _validated_id(approval_id, "approval_id")
        service = await _service()
        return _public_payload(
            await service.approve(
                principal.principal_id,
                project_id,
                approval_id,
                body.approved,
                body.response,
            )
        )

    async def cancel_project(
        project_id: str,
        authorization: str | None = Header(default=None),
    ):
        principal = app_module._require_http_auth(authorization)
        project_id = _validated_id(project_id, "project_id")
        service = await _service()
        return _public_payload(await service.cancel(principal.principal_id, project_id))

    async def project_result(
        project_id: str,
        authorization: str | None = Header(default=None),
    ):
        principal = app_module._require_http_auth(authorization)
        project_id = _validated_id(project_id, "project_id")
        service = await _service()
        return _public_payload(await service.result(principal.principal_id, project_id))

    # Expose callables on jarvis_orchestrator.app as well as HTTP routes so unit
    # tests and internal adapters exercise the exact same contracts.
    app_module.GoalSubmission = GoalSubmission
    app_module.ApprovalResponse = ApprovalResponse
    app_module.submit_goal = submit_goal
    app_module.project_status = project_status
    app_module.project_event_history = project_event_history
    app_module.project_approval = project_approval
    app_module.cancel_project = cancel_project
    app_module.project_result = project_result

    app_module.app.add_api_route("/v1/goals", submit_goal, methods=["POST"])
    app_module.app.add_api_route("/v1/projects/{project_id}", project_status, methods=["GET"])
    app_module.app.add_api_route(
        "/v1/projects/{project_id}/events",
        project_event_history,
        methods=["GET"],
    )
    app_module.app.add_api_route(
        "/v1/projects/{project_id}/approvals/{approval_id}",
        project_approval,
        methods=["POST"],
    )
    app_module.app.add_api_route(
        "/v1/projects/{project_id}/cancel",
        cancel_project,
        methods=["POST"],
    )
    app_module.app.add_api_route(
        "/v1/projects/{project_id}/result",
        project_result,
        methods=["GET"],
    )
    app_module._goal_api_installed = True
