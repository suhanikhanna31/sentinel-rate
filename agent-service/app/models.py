"""Request/response schemas for the abuse-investigation agent."""
from typing import List, Optional
from pydantic import BaseModel, Field


class RateLimitEvent(BaseModel):
    endpoint: str
    timestamp: str
    allowed: bool


class InvestigateRequest(BaseModel):
    client_id: str = Field(..., description="IP or API key identifying the caller")
    violation_count: int = Field(0, ge=0)
    endpoint: str = "unknown"
    window_seconds: int = 60
    recent_events: List[RateLimitEvent] = Field(default_factory=list)


class InvestigateResponse(BaseModel):
    client_id: str
    risk_score: float
    risk_level: str
    recommended_action: str
    requires_human_review: bool
    reasoning: str
    used_llm: bool
