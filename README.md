# Sentinel Rate — Distributed Rate Limiter

A production-grade, Redis-backed rate limiter built with **Spring Boot 3** and **Java 17**.

## Architecture

```
Client Request
      │
      ▼
┌─────────────────────┐
│  RateLimitFilter    │  ← Sliding-window check via atomic Lua script
│  (Servlet Filter)   │  ← Abuse detection (tiered block durations)
│                     │  ← Injects X-RateLimit-* headers
└────────┬────────────┘
         │ allowed
         ▼
┌─────────────────────┐
│  Controller         │  ← Thin; business logic lives in services
└─────────────────────┘
         │
         ▼
┌─────────────────────┐
│  Redis (Sorted Set) │  ← Atomic Lua script: ZREMRANGEBYSCORE + ZADD + ZCARD
└─────────────────────┘
```

## Key Design Decisions

| Concern | Choice | Why |
|---|---|---|
| Algorithm | Sliding window (sorted set) | No burst spike at window boundary vs fixed window |
| Atomicity | Lua script | Eliminates TOCTOU race between count-check and increment |
| Enforcement layer | Servlet filter | Rate-limit logic runs before controllers; one place to change |
| Client ID | `X-Forwarded-For` → remote addr | Works behind load balancers |
| Abuse tiers | 3 / 6 / 10 violations → 10m / 1h / 24h | Proportional deterrence |

## Response Headers

| Header | Description |
|---|---|
| `X-RateLimit-Limit` | Max requests allowed per window |
| `X-RateLimit-Remaining` | Requests remaining in current window |
| `X-RateLimit-Reset` | Unix epoch when the window resets |
| `Retry-After` | Seconds until next allowed request (429 only) |

## Quick Start

```bash
# Start Redis + app
docker-compose up --build

# Hit the health endpoint
curl -i http://localhost:8080/health

# Unblock a client (admin)
curl -X DELETE http://localhost:8080/admin/block/127.0.0.1
```

## Running Tests

```bash
cd rate-limiter-service/rate-limiter
./mvnw verify
```

## Load Test

```bash
# Requires k6 (https://k6.io)
k6 run scripts/load-test.js
```

## CI/CD

GitHub Actions pipeline (`.github/workflows/ci.yml`):
1. Spins up a Redis service container
2. Runs all JUnit 5 tests with JaCoCo coverage enforcement (80%+ line coverage)
3. Builds Docker image
4. Smoke-tests the container by hitting `/health`
