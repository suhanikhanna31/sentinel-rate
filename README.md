<div align="center">

# 🛡️ Sentinel Rate Limiter

### Production-grade distributed rate limiting for Spring Boot microservices

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=java)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-Sorted%20Sets-red?style=flat-square&logo=redis)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue?style=flat-square&logo=docker)](https://www.docker.com/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Deployed-326CE5?style=flat-square&logo=kubernetes)](https://kubernetes.io/)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-black?style=flat-square&logo=githubactions)](https://github.com/features/actions)
[![Coverage](https://img.shields.io/badge/Coverage-80%25%2B-success?style=flat-square)](https://www.jacoco.org/jacoco/)

</div>

---

## 🚀 What is Sentinel?

Sentinel is a **production-ready, Redis-backed rate limiter** built from scratch on **Spring Boot 3** and **Java 17**. It goes far beyond a basic token bucket — implementing a true **sliding window algorithm** via atomic Lua scripting, tiered abuse detection, annotation-driven per-endpoint configuration, real-time observability via Micrometer + Actuator, and full Kubernetes deployment manifests.

This isn't a tutorial project. It's engineered the way a senior backend engineer would build it for production.

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
| **CI/CD Pipeline** | GitHub Actions — Redis service container, JUnit 5, JaCoCo 80%+ coverage gate, Docker build + smoke test |
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

## 🔄 CI/CD Pipeline

GitHub Actions pipeline (`.github/workflows/ci.yml`) runs on every push:

```
Push → Spin up Redis service container
     → Run JUnit 5 test suite
     → Enforce JaCoCo 80%+ line coverage gate
     → Build Docker image
     → Smoke-test container via /health endpoint
```

---

## 🛠️ Tech Stack

- **Java 17** + **Spring Boot 3**
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
