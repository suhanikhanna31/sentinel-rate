# Sentinel Rate Limiter

A scalable, distributed rate limiting microservice built with **Spring Boot** and **Redis**. Designed to protect high-throughput APIs from abuse using a token bucket algorithm and adaptive anomaly detection.

---

## Features

- **Token Bucket Algorithm** — Each client gets a burst capacity of 10 tokens, refilling at 2 tokens/second. Provides smooth, low-latency rate limiting without fixed windows.
- **Centralized Redis Cache** — All rate-limiting state lives in Redis, enabling horizontal scaling with no coordination between instances.
- **Adaptive Abuse Detection** — Clients that repeatedly exceed limits are dynamically blocked. Block duration doubles with each offence (10 min → 20 min → 40 min, capped at 24 hours).
- **Stateless Architecture** — No in-memory state. Every instance derives client state entirely from Redis, making the service fully scalable.
- **Standard API Contract** — Responses include `X-RateLimit-Limit` and `X-RateLimit-Remaining` headers so downstream microservices can integrate consistently.

---

## Architecture

```
Incoming Request
      │
      ▼
RateLimiterController  (stateless, resolves client ID from X-Forwarded-For)
      │
      ├──▶ AbuseDetectionService  →  Redis: abuse:block:<clientId>
      │         (is client hard-blocked?)
      │
      └──▶ RateLimiterService     →  Redis: rate_limit:tokens:<clientId>
                (token bucket check)             rate_limit:last_refill:<clientId>
```

---

## Redis Key Namespace

| Key | Description | TTL |
|-----|-------------|-----|
| `rate_limit:tokens:<clientId>` | Remaining tokens in the bucket | 10 min (idle) |
| `rate_limit:last_refill:<clientId>` | Timestamp of last token refill | 10 min (idle) |
| `abuse:count:<clientId>` | Violation count in rolling window | 5 min |
| `abuse:block:<clientId>` | Present when client is hard-blocked | Adaptive (10 min–24 hr) |
| `abuse:offence_count:<clientId>` | Lifetime block count for adaptive TTL | 7 days |

---

## API Endpoints

### `GET /check`
Evaluate a request against rate-limit and abuse policies. Call this from any downstream microservice before processing an API request.

**Request Headers**

| Header | Required | Description |
|--------|----------|-------------|
| `X-Forwarded-For` | No | Client IP set by API gateway. Falls back to TCP remote addr. |

**Response**

```json
{
  "allowed": true,
  "remainingTokens": 7,
  "message": "Request allowed."
}
```

**Response Headers**

| Header | Description |
|--------|-------------|
| `X-RateLimit-Limit` | Max tokens (burst capacity) |
| `X-RateLimit-Remaining` | Tokens left after this request |

**Status Codes**

| Code | Meaning |
|------|---------|
| `200 OK` | Request allowed |
| `429 Too Many Requests` | Rate limit exceeded (temporary) |
| `403 Forbidden` | Client is hard-blocked due to repeated abuse |

---

### `GET /health`
Liveness probe. Returns `200 OK` with no rate limiting applied.

---

## Getting Started

### Prerequisites
- Java 17+
- Maven
- Redis running on `localhost:6379`

### Run Redis (via Docker)
```bash
docker run -p 6379:6379 redis
```

### Run the Service
```bash
./mvnw spring-boot:run
```

### Test It
```bash
# Single request
curl -i http://localhost:8080/check

# Spam to trigger rate limiting
for i in {1..15}; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/check; done
```

---

## Configuration

Set via `application.properties` or environment variables:

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` | Redis host |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` | Redis port |
| `server.port` | — | `8080` | HTTP port |

---

## Project Structure

```
src/main/java/com/sentinel/ratelimiter/
├── RateLimiterApplication.java       # Spring Boot entry point
├── config/
│   └── RedisConfig.java              # Centralized Redis bean + policy documentation
├── controller/
│   └── RateLimiterController.java    # Stateless REST endpoints (/check, /health)
├── service/
│   ├── RateLimiterService.java       # Token bucket algorithm
│   └── AbuseDetectionService.java    # Adaptive anomaly detection + blocking
└── dto/
    └── RateLimitResponse.java        # Shared API response contract
```

---

## Integration Guide for Downstream Microservices

Before processing any API request, call:

```
GET http://<sentinel-host>:8080/check
Header: X-Forwarded-For: <client-ip>
```

If `allowed` is `false` or status is `429`/`403`, reject the request immediately. Use the `X-RateLimit-Remaining` header to surface quota info to your clients.

---

## Tech Stack

- **Java 17**
- **Spring Boot 4.0**
- **Spring Data Redis**
- **Redis** (centralized state store)
- **Maven**