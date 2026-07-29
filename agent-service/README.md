# Sentinel Agent Service

A small FastAPI + [LangGraph](https://langchain-ai.github.io/langgraph/) service that adds an **agentic AI layer** on top of the Java rate-limiter's abuse-detection data. Instead of a single opaque "is this client bad?" model call, it runs a small explainable graph:

```
gather_context ──▶ assess_risk ──▶ policy_decision ──▶ escalate ──▶ END
                     (LLM or            │
                    heuristic)          └──────────▶ auto_resolve ──▶ END
```

* **gather_context** — derives features (blocked-request ratio) from recent events.
* **assess_risk** — asks an LLM (Anthropic Claude) for a risk score/level/action; if no `ANTHROPIC_API_KEY` is configured, falls back to a deterministic heuristic with the identical output shape, so the rest of the graph, tests, and CI never depend on a live model or secret.
* **policy_decision** — a deterministic guardrail on top of the model's opinion (e.g. 10+ violations is *always* escalated, regardless of what the LLM said).
* **escalate / auto_resolve** — terminal nodes producing the final recommendation.

## Run locally

```bash
cd agent-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # optionally set ANTHROPIC_API_KEY
uvicorn app.main:app --reload --port 8000
```

## API

```bash
curl -X POST http://localhost:8000/agent/investigate \
  -H "Content-Type: application/json" \
  -d '{
        "client_id": "10.0.0.5",
        "violation_count": 11,
        "endpoint": "/api/orders",
        "window_seconds": 60,
        "recent_events": [{"endpoint": "/api/orders", "timestamp": "2026-07-29T00:00:00Z", "allowed": false}]
      }'
```

```json
{
  "client_id": "10.0.0.5",
  "risk_score": 0.77,
  "risk_level": "high",
  "recommended_action": "extend_block",
  "requires_human_review": true,
  "reasoning": "Derived from violation_count=11 and blocked_ratio=1.00 using the offline heuristic (no LLM configured).",
  "used_llm": false
}
```

## Tests

```bash
pytest -q
```

## How this plugs into Sentinel

The Java `AbuseDetectionService` can call `POST /agent/investigate` whenever a client crosses a violation threshold, and use `requires_human_review` / `recommended_action` to decide whether to extend a block automatically or flag the case for a human — instead of relying solely on the fixed 3/6/10-violation tiers.
