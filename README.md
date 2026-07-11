<div align="center">

# 🛡️ Sentinel Rate Limiter

### A Redis-backed distributed rate limiter for Spring Boot services

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=java)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-Sorted%20Sets-red?style=flat-square&logo=redis)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue?style=flat-square&logo=docker)](https://www.docker.com/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Deployed-326CE5?style=flat-square&logo=kubernetes)](https://kubernetes.io/)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-black?style=flat-square&logo=githubactions)](https://github.com/features/actions)

</div>

---

## 🚀 What is Sentinel?

Sentinel is a Redis-backed rate limiter built on **Spring Boot 4** and **Java 17**, written to work through the pieces a real rate limiter needs rather than stopping at a basic token bucket: a **sliding window algorithm** via atomic Lua scripting, tiered abuse detection, annotation-driven per-endpoint configuration, observability via Micrometer + Actuator, and Kubernetes deployment manifests to go with it.

It's a portfolio/practice project — built solo, not battle-tested in production traffic — but the design decisions below (atomicity, per-client isolation, tiered blocking) are the same ones you'd reach for in a real system, and I can walk through the tradeoffs behind each one.

---

## ✨ Key Features

| Feature | Details |
|---|---|
| **Sliding Window Algorithm** | Sorted-set based; eliminates burst spikes at window boundaries unlike fixed-window |
| **Atomic Lua Scripting** | ZREMRANGEBYSCORE + ZADD + ZCARD in a single Redis script — zero TOCTOU race conditions |
| **Tiered Abuse Detection** | 3 / 6 / 10 violations → 10 min / 1 hr / 24 hr progressive blocks |
| **`@RateLimit` Annotation** | Per-endpoint limits via Spring AOP — zero boilerplate in controllers |
| **Micrometer Metrics** | Allowed/blocked request counters exposed via Actuator for Prometheus/Grafana |
| **Hibernate Persistence** | Rate limit events stored in DB for audit trails and analytics |
| **Kubernetes Ready** | Full deployment, service, and ConfigMap manifests for rate-limiter + Redis |
| **CI/CD Pipeline** | GitHub Actions — Redis service container, JUnit 5, an enforced JaCoCo minimum-coverage gate, Docker build + smoke test |
| **Smart Client ID Resolution** | `X-Forwarded-For` → remote address fallback, works transparently behind load balancers |

---

## 🏗️ Architecture

```
Client Request
      │
      ▼
┌──────────────────────────┐
│     RateLimitFilter      │  ← Runs before every controller
│    (Servlet Filter)      │  ← Sliding-window check via atomic Lua
│                          │  ← Tiered abuse detection + blocking
│                          │  ← Injects X-RateLimit-* response headers
└───────────┬──────────────┘
            │ allowed
            ▼
┌──────────────────────────┐
│  @RateLimit AOP Advice   │  ← Per-endpoint overrides via annotation
│  (Spring AOP)            │  ← Delegates to same core limiter
└───────────┬──────────────┘
            │
            ▼
┌──────────────────────────┐
│      Controller          │  ← Thin; zero rate-limit logic here
└───────────┬──────────────┘
            │
            ▼
┌──────────────────────────┐
│   Redis (Sorted Set)     │  ← Single atomic Lua script
│                          │  ← Key: client_ip:endpoint
│                          │  ← Score: timestamp (ms)
└──────────────────────────┘
            │
            ▼
┌──────────────────────────┐
│  Hibernate / JPA         │  ← Persists rate limit events for audit
└──────────────────────────┘
```

---

## 🔑 Design Decisions

| Concern | Decision | Rationale |
|---|---|---|
| **Algorithm** | Sliding window (sorted set) | No burst spike at window boundary vs. fixed-window |
| **Atomicity** | Lua script | Single round-trip; eliminates check-then-act race condition |
| **Enforcement layer** | Servlet filter | Runs before controllers; one centralised place to change |
| **Per-endpoint config** | `@RateLimit` + Spring AOP | Zero controller coupling; declarative and composable |
| **Client identity** | `X-Forwarded-For` → remote addr | Transparent behind reverse proxies and load balancers |
| **Abuse deterrence** | 3-tier progressive blocks | Proportional escalation; resists retry storms |
| **Observability** | Micrometer + Actuator | Drop-in Prometheus/Grafana integration |
| **Audit trail** | Hibernate persistence | Rate limit events queryable for analytics and compliance |

---

## 📡 Response Headers

Every response includes rate limit metadata for clients:

| Header | Description |
|---|---|
| `X-RateLimit-Limit` | Maximum requests allowed per window |
| `X-RateLimit-Remaining` | Requests left in the current window |
| `X-RateLimit-Reset` | Unix epoch timestamp when the window resets |
| `Retry-After` | Seconds until next allowed request (only on `429`) |

---

## ⚡ Quick Start

```bash
# Clone the repo
git clone https://github.com/suhanikhanna31/sentinel-rate.git
cd sentinel-rate

# Start Redis + app with Docker Compose
docker-compose up --build

# Hit the health endpoint
curl -i http://localhost:8080/health

# Trigger rate limiting (run repeatedly)
curl -i http://localhost:8080/api/your-endpoint

# Unblock a client (admin endpoint)
curl -X DELETE http://localhost:8080/admin/block/127.0.0.1
```

---

## 🧪 Running Tests

```bash
cd rate-limiter-service/rate-limiter

# Run tests with coverage report
./mvnw verify

# JaCoCo report generated at:
# target/site/jacoco/index.html
```

**What's covered:**
- `RateLimiterService` and `AbuseDetectionService` — integration tests against a real Redis instance (`RateLimiterIntegrationTest`), plus isolated unit tests for `AbuseDetectionService`'s tier-boundary logic (`AbuseDetectionServiceTest`)
- `RateLimitFilter` — MockMvc tests covering allowed/blocked/rate-limited paths and header injection (`RateLimitFilterTest`)
- `EventPersistenceService` — unit tests with mocked repositories covering tier mapping, delegation, and the pruning cutoff (`EventPersistenceServiceTest`)

**What's still thin:** `RateLimitAspect` (the `@RateLimit` annotation's AOP advice) and the admin endpoints on `RateLimiterController` (`/admin/audit/*`, `/admin/block/*`) don't have dedicated tests yet — they're exercised only indirectly through the context-load test. That's the honest gap right now.

---

## 📊 Load Testing

```bash
# Requires k6 — https://k6.io/docs/getting-started/installation/
k6 run scripts/load-test.js
```

---

## ☸️ Kubernetes Deployment

Full manifests provided for both the rate-limiter service and Redis:

```bash
# Apply all manifests
kubectl apply -f k8s/

# Verify pods are running
kubectl get pods
```

Manifests include:
- **Deployment** — rate-limiter service + Redis
- **Service** — ClusterIP for internal routing
- **ConfigMap** — externalised configuration (no secrets hardcoded)

---

## ▲ Deploying to Render

For a simpler, no-Kubernetes deployment, `render.yaml` at the repo root is a [Render Blueprint](https://render.com/docs/infrastructure-as-code) that provisions and wires together everything the app needs — no other infrastructure required:

| Service | What it is |
|---|---|
| `sentinel-rate` | The Java app, built from the repo's existing `Dockerfile` |
| `sentinel-redis` | A Render Key Value (Redis-compatible) instance for rate-limit/abuse state |
| `sentinel-db` | A Render-managed Postgres instance for the durable audit trail |

**To deploy:** push this repo to GitHub/GitLab, then in the Render Dashboard choose **New → Blueprint** and point it at the repo. Render reads `render.yaml`, provisions all three services, and wires the env vars between them automatically.

A couple of things worth knowing:
- The app runs with `SPRING_PROFILES_ACTIVE=postgres` on Render (see `application-postgres.properties`), so the audit trail actually persists across deploys — unlike the in-memory H2 default used for local dev and CI.
- Render's free Postgres tier expires after 30 days; the free Key Value tier has no disk persistence (fine here, since rate-limit counters are meant to be ephemeral and self-expire anyway).
- The `k8s/` manifests above are unrelated to this path — use one or the other, not both.

---

## 🔄 CI/CD Pipeline

GitHub Actions pipeline (`.github/workflows/ci.yml`) runs on every push:

```
Push → Spin up Redis service container
     → Run JUnit 5 test suite
     → Enforce JaCoCo minimum line-coverage gate (see pom.xml for the current threshold)
     → Build Docker image
     → Smoke-test container via /health endpoint
```

---

## 🛠️ Tech Stack

- **Java 17** + **Spring Boot 4**
- **Redis** (sorted sets + Lua scripting)
- **Spring AOP** (annotation-based rate limiting)
- **Micrometer + Actuator** (metrics + observability)
- **Hibernate / JPA** (event persistence)
- **Docker + Docker Compose**
- **Kubernetes** (deployment manifests)
- **GitHub Actions** (CI/CD)
- **JUnit 5 + JaCoCo** (testing + coverage)
- **k6** (load testing)

---

## 👩‍💻 Author

**Suhani Khanna**
[GitHub](https://github.com/suhanikhanna31)

---

<div align="center">
  <sub>Built with ❤️ and a lot of Redis sorted sets.</sub>
</div>
