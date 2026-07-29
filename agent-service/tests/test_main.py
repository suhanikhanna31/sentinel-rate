from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "UP"


def test_investigate_endpoint():
    resp = client.post(
        "/agent/investigate",
        json={
            "client_id": "10.0.0.5",
            "violation_count": 11,
            "endpoint": "/api/orders",
            "window_seconds": 60,
            "recent_events": [
                {"endpoint": "/api/orders", "timestamp": "2026-07-29T00:00:00Z", "allowed": False}
            ],
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["client_id"] == "10.0.0.5"
    assert body["requires_human_review"] is True
    assert 0.0 <= body["risk_score"] <= 1.0
