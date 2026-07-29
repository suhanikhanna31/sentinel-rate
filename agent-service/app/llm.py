"""Thin wrapper around the LLM call used by the LangGraph agent.

If ANTHROPIC_API_KEY is set, we call the real model. Otherwise we fall back
to a deterministic heuristic so the graph, tests, and CI pipeline all work
without any network access or secrets configured.
"""
import os
import re

MODEL = os.getenv("SENTINEL_AGENT_MODEL", "claude-sonnet-4-6")


def llm_is_configured() -> bool:
    return bool(os.getenv("ANTHROPIC_API_KEY"))


def assess_with_llm(prompt: str) -> str:
    """Calls the Anthropic API. Raises if the SDK/key isn't available;
    callers should catch and fall back to `assess_heuristically`."""
    from anthropic import Anthropic  # imported lazily so it's optional at runtime

    client = Anthropic()
    response = client.messages.create(
        model=MODEL,
        max_tokens=300,
        messages=[{"role": "user", "content": prompt}],
    )
    return "".join(block.text for block in response.content if block.type == "text")


def assess_heuristically(violation_count: int, recent_events) -> str:
    """Rule-based stand-in for the LLM, used offline/in CI.

    Mirrors the shape of an LLM response so downstream parsing logic is
    identical regardless of which path produced it.
    """
    blocked_ratio = 0.0
    if recent_events:
        blocked = sum(1 for e in recent_events if not e.allowed)
        blocked_ratio = blocked / len(recent_events)

    score = min(1.0, (violation_count / 10) * 0.7 + blocked_ratio * 0.3)

    if score >= 0.7:
        level, action = "high", "extend_block"
    elif score >= 0.35:
        level, action = "medium", "monitor"
    else:
        level, action = "low", "allow"

    return (
        f"risk_score: {score:.2f}\n"
        f"risk_level: {level}\n"
        f"recommended_action: {action}\n"
        f"reasoning: Derived from violation_count={violation_count} and "
        f"blocked_ratio={blocked_ratio:.2f} using the offline heuristic "
        f"(no LLM configured)."
    )


def parse_assessment(text: str):
    """Parses the loosely-structured 'key: value' text produced by either
    the LLM (prompted to answer in this format) or the heuristic fallback."""
    fields = {"risk_score": 0.0, "risk_level": "low", "recommended_action": "allow", "reasoning": text.strip()}
    for line in text.splitlines():
        m = re.match(r"\s*risk_score\s*:\s*([0-9.]+)", line, re.I)
        if m:
            fields["risk_score"] = float(m.group(1))
            continue
        m = re.match(r"\s*risk_level\s*:\s*(\w+)", line, re.I)
        if m:
            fields["risk_level"] = m.group(1).lower()
            continue
        m = re.match(r"\s*recommended_action\s*:\s*(\w+)", line, re.I)
        if m:
            fields["recommended_action"] = m.group(1).lower()
            continue
        m = re.match(r"\s*reasoning\s*:\s*(.+)", line, re.I)
        if m:
            fields["reasoning"] = m.group(1).strip()
    return fields
