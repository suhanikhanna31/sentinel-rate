"""Sentinel Agent Service.

A small FastAPI service that sits alongside the Java rate-limiter and adds
an agentic AI layer on top of it: given a client's recent violation
history, a LangGraph workflow decides whether the case can be auto-resolved
or needs to be escalated, using an LLM (Anthropic) when configured and a
transparent offline heuristic otherwise.
"""
from fastapi import FastAPI

from .graph import get_graph
from .llm import llm_is_configured
from .models import InvestigateRequest, InvestigateResponse

app = FastAPI(
    title="Sentinel Agent Service",
    description="LangGraph-based agentic abuse-investigation service for Sentinel Rate Limiter",
    version="1.0.0",
)


@app.get("/health")
def health():
    return {"status": "UP", "llm_configured": llm_is_configured()}


@app.post("/agent/investigate", response_model=InvestigateResponse)
def investigate(payload: InvestigateRequest):
    graph = get_graph()
    result = graph.invoke(
        {
            "client_id": payload.client_id,
            "violation_count": payload.violation_count,
            "endpoint": payload.endpoint,
            "window_seconds": payload.window_seconds,
            "recent_events": payload.recent_events,
        }
    )

    return InvestigateResponse(
        client_id=payload.client_id,
        risk_score=result["risk_score"],
        risk_level=result["risk_level"],
        recommended_action=result["recommended_action"],
        requires_human_review=result["requires_human_review"],
        reasoning=result["reasoning"],
        used_llm=result["used_llm"],
    )
