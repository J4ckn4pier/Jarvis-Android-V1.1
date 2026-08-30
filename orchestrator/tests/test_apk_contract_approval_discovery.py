from pathlib import Path


def test_apk_contract_documents_server_issued_approval_discovery():
    contract = (
        Path(__file__).parents[1] / "APK_INTEGRATION_CONTRACT.md"
    ).read_text(encoding="utf-8")

    assert "pending_approvals" in contract
    assert "approval.requested" in contract
    assert '"approval_id": "approval-9"' in contract
    assert "must never derive `approval_id` from `task_id`" in contract
