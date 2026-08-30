from pathlib import Path


def test_apk_integration_contract_is_explicit_and_provider_neutral():
    contract = Path(__file__).parents[1] / "APK_INTEGRATION_CONTRACT.md"
    text = contract.read_text(encoding="utf-8")

    assert "APK INTEGRATION CONTRACT GREEN" in text

    for endpoint in (
        "POST /v1/goals",
        "GET /v1/projects/{project_id}",
        "GET /v1/projects/{project_id}/events",
        "POST /v1/projects/{project_id}/approvals/{approval_id}",
        "POST /v1/projects/{project_id}/cancel",
        "GET /v1/projects/{project_id}/result",
    ):
        assert endpoint in text

    for field in (
        "project_id",
        "session_id",
        "state",
        "goal",
        "task_count",
        "task_states",
        "last_progress_at",
        "event_id",
        "kind",
        "task_id",
        "timestamp",
        "next_event_id",
        "has_more",
        "result",
    ):
        assert f"`{field}`" in text

    assert "Authorization: Bearer <token>" in text
    assert "after_event_id" in text
    assert "provider details are never part of the apk contract" in text.lower()
