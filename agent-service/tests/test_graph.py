from app.graph import get_graph
from app.models import RateLimitEvent


def _invoke(violation_count, events=None):
    graph = get_graph()
    return graph.invoke(
        {
            "client_id": "1.2.3.4",
            "violation_count": violation_count,
            "endpoint": "/api/data",
            "window_seconds": 60,
            "recent_events": events or [],
        }
    )


def test_low_violation_count_auto_resolves():
    result = _invoke(1)
    assert result["requires_human_review"] is False
    assert result["recommended_action"] in {"allow", "monitor"}


def test_high_violation_count_escalates():
    result = _invoke(12)
    assert result["requires_human_review"] is True
    assert result["recommended_action"] == "extend_block"


def test_blocked_ratio_feeds_risk_score():
    events = [RateLimitEvent(endpoint="/x", timestamp="t", allowed=False) for _ in range(5)]
    result = _invoke(4, events)
    assert 0.0 <= result["risk_score"] <= 1.0
    assert result["used_llm"] is False  # no ANTHROPIC_API_KEY in CI


def test_offline_mode_never_calls_real_llm():
    result = _invoke(3)
    assert result["used_llm"] is False
    assert "heuristic" in result["reasoning"].lower()
