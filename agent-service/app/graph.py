"""Agentic abuse-investigation workflow, built with LangGraph.

Graph shape:

    gather_context --> assess_risk --> policy_decision --(high risk)--> escalate --> END
                                              \\--(low/medium risk)--> auto_resolve --> END

Each request to the rate-limiter's abuse pipeline can be routed through this
graph to get an explainable, multi-step recommendation instead of a single
opaque model call: we gather derived signal, get a risk assessment (LLM or
offline heuristic), then apply a deterministic policy layer on top before
deciding whether the case needs a human, rather than trusting the model
output blindly.
"""
from typing import List, TypedDict

from langgraph.graph import StateGraph, END

from .llm import assess_with_llm, assess_heuristically, parse_assessment, llm_is_configured
from .models import RateLimitEvent


class AgentState(TypedDict, total=False):
    client_id: str
    violation_count: int
    endpoint: str
    window_seconds: int
    recent_events: List[RateLimitEvent]
    blocked_ratio: float
    risk_score: float
    risk_level: str
    recommended_action: str
    requires_human_review: bool
    reasoning: str
    used_llm: bool


def gather_context(state: AgentState) -> AgentState:
    events = state.get("recent_events", [])
    blocked = sum(1 for e in events if not e.allowed)
    state["blocked_ratio"] = (blocked / len(events)) if events else 0.0
    return state


def assess_risk(state: AgentState) -> AgentState:
    prompt = (
        "You are an abuse-detection analyst for a rate-limiting service. "
        f"Client '{state['client_id']}' has {state['violation_count']} rate-limit "
        f"violations in the last {state['window_seconds']}s on endpoint "
        f"'{state['endpoint']}', with a blocked-request ratio of "
        f"{state['blocked_ratio']:.2f}. Respond ONLY in this exact format:\n"
        "risk_score: <0.0-1.0>\nrisk_level: <low|medium|high>\n"
        "recommended_action: <allow|monitor|extend_block>\nreasoning: <one sentence>"
    )

    used_llm = False
    if llm_is_configured():
        try:
            raw = assess_with_llm(prompt)
            used_llm = True
        except Exception:
            raw = assess_heuristically(state["violation_count"], state.get("recent_events", []))
    else:
        raw = assess_heuristically(state["violation_count"], state.get("recent_events", []))

    parsed = parse_assessment(raw)
    state["risk_score"] = parsed["risk_score"]
    state["risk_level"] = parsed["risk_level"]
    state["recommended_action"] = parsed["recommended_action"]
    state["reasoning"] = parsed["reasoning"]
    state["used_llm"] = used_llm
    return state


def policy_decision(state: AgentState) -> AgentState:
    # Deterministic guardrail layered on top of the model's opinion: a
    # client with 10+ violations is always escalated, regardless of what
    # the assessment step returned.
    if state["violation_count"] >= 10 or state["risk_score"] >= 0.7:
        state["requires_human_review"] = True
    else:
        state["requires_human_review"] = False
    return state


def route_after_policy(state: AgentState) -> str:
    return "escalate" if state["requires_human_review"] else "auto_resolve"


def escalate(state: AgentState) -> AgentState:
    state["recommended_action"] = "extend_block"
    return state


def auto_resolve(state: AgentState) -> AgentState:
    return state


def build_graph():
    graph = StateGraph(AgentState)
    graph.add_node("gather_context", gather_context)
    graph.add_node("assess_risk", assess_risk)
    graph.add_node("policy_decision", policy_decision)
    graph.add_node("escalate", escalate)
    graph.add_node("auto_resolve", auto_resolve)

    graph.set_entry_point("gather_context")
    graph.add_edge("gather_context", "assess_risk")
    graph.add_edge("assess_risk", "policy_decision")
    graph.add_conditional_edges(
        "policy_decision",
        route_after_policy,
        {"escalate": "escalate", "auto_resolve": "auto_resolve"},
    )
    graph.add_edge("escalate", END)
    graph.add_edge("auto_resolve", END)
    return graph.compile()


_compiled_graph = None


def get_graph():
    global _compiled_graph
    if _compiled_graph is None:
        _compiled_graph = build_graph()
    return _compiled_graph
