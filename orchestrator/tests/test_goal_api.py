from __future__ import annotations

import pytest
from fastapi import HTTPException

from jarvis_orchestrator import app as app_module


class RecordingGoalService:
    def __init__(self) -> None:
        self.calls: list[tuple] = []

    async def submit(self, owner_id, session_id, request):
        self.calls.append(("submit", owner_id, session_id, request.goal))
        return {
            "project_id": "project-1",
            "session_id": session_id,
            "state": "active",
            "goal": request.goal,
        }

    async def status(self, owner_id, project_id):
        self.calls.append(("status", owner_id, project_id))
        return {
            "project_id": project_id,
            "state": "active",
            "progress": {"complete": 1, "total": 3},
        }

    async def events(self, owner_id, project_id, after_event_id, limit):
        self.calls.append(("events", owner_id, project_id, after_event_id, limit))
        return {
            "project_id": project_id,
            "events": [
                {
                    "event_id": "2-0",
                    "kind": "task.complete",
                    "task_id": "task-1",
                    "timestamp": "2026-08-30T19:00:00+00:00",
                }
            ],
            "next_event_id": "2-0",
            "has_more": False,
        }

    async def approve(self, owner_id, project_id, approval_id, approved, response):
        self.calls.append(("approve", owner_id, project_id, approval_id, approved, response))
        return {"project_id": project_id, "approval_id": approval_id, "accepted": approved}

    async def cancel(self, owner_id, project_id):
        self.calls.append(("cancel", owner_id, project_id))
        return {"project_id": project_id, "state": "cancelled"}

    async def result(self, owner_id, project_id):
        self.calls.append(("result", owner_id, project_id))
        return {
            "project_id": project_id,
            "state": "complete",
            "result": "JARVIS synthesized answer",
            "evidence_ids": ["e-1"],
        }


@pytest.fixture

def goal_service(monkeypatch):
    monkeypatch.setenv("JARVIS_API_KEYS_JSON", '{"alice":"token-a","bob":"token-b"}')
    monkeypatch.delenv("JARVIS_API_TOKEN", raising=False)
    service = RecordingGoalService()
    monkeypatch.setattr(app_module.app.state, "goal_service", service, raising=False)
    return service


@pytest.mark.asyncio
async def test_phone_goal_contract_is_authenticated_owner_scoped_and_provider_neutral(goal_service):
    submitted = await app_module.submit_goal(
        app_module.GoalSubmission(
            goal="Research dinner options",
            session_id="primary",
            constraints=("under $50",),
            acceptance_criteria=("independently verified",),
        ),
        authorization="Bearer token-a",
    )
    status = await app_module.project_status("project-1", authorization="Bearer token-a")
    result = await app_module.project_result("project-1", authorization="Bearer token-a")

    assert submitted["project_id"] == "project-1"
    assert status["progress"] == {"complete": 1, "total": 3}
    assert result["result"] == "JARVIS synthesized answer"
    assert goal_service.calls[0][:3] == ("submit", "alice", "primary")
    assert all("agent" not in str(payload).lower() for payload in (submitted, status, result))
    assert all("provider" not in str(payload).lower() for payload in (submitted, status, result))


@pytest.mark.asyncio
async def test_phone_can_disconnect_then_resume_progress_from_cursor(goal_service):
    page = await app_module.project_event_history(
        "project-1",
        limit=100,
        after_event_id="1-0",
        authorization="Bearer token-a",
    )

    assert page["events"][0]["event_id"] == "2-0"
    assert page["next_event_id"] == "2-0"
    assert page["has_more"] is False
    assert goal_service.calls[-1] == ("events", "alice", "project-1", "1-0", 100)


@pytest.mark.asyncio
async def test_approval_and_cancel_are_owner_scoped(goal_service):
    approval = await app_module.project_approval(
        "project-1",
        "approval-1",
        app_module.ApprovalResponse(approved=True, response="Proceed"),
        authorization="Bearer token-a",
    )
    cancelled = await app_module.cancel_project("project-1", authorization="Bearer token-a")

    assert approval["accepted"] is True
    assert cancelled["state"] == "cancelled"
    assert ("cancel", "alice", "project-1") in goal_service.calls


@pytest.mark.asyncio
async def test_goal_api_rejects_unauthenticated_request_when_auth_configured(goal_service):
    with pytest.raises(HTTPException) as exc:
        await app_module.project_status("project-1", authorization=None)

    assert exc.value.status_code == 401
