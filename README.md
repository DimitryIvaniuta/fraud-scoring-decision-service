# fraud-scoring-decision-service

Production-grade real-time fraud scoring and decision service built with Java 25, Spring Boot 4.1, WebFlux, R2DBC, PostgreSQL 18.4, Flyway, Redis 8.2, Kafka KRaft 4.2, Gradle 9.6.1, and Lombok.

## GitHub repository metadata

- Repository name: `fraud-scoring-decision-service`
- Description: `Real-time fraud scoring and decision service with explainable deterministic decisions, Redis feature cache, R2DBC PostgreSQL persistence, Flyway migrations, Kafka KRaft outbox events, and WebFlux APIs.`
- Main branch: `main`
- Suggested topics: `java-25`, `spring-boot-4`, `webflux`, `r2dbc`, `postgresql`, `flyway`, `redis`, `kafka`, `fraud-detection`, `outbox-pattern`

## Why this stack

- Java 25 is used as the production JDK target.
- Spring Boot 4.1 is used for the newest stable Spring runtime line compatible with Java 25.
- Gradle 9.6.1 is used because Gradle 9.x has full Java 25 support.
- WebFlux keeps the synchronous API non-blocking under load.
- R2DBC avoids blocking JDBC calls on the request path.
- PostgreSQL 18.4 stores decisions as the source of truth and supports row locking for multi-instance outbox claims.
- Flyway makes database schema changes repeatable.
- Redis 8.2 serves low-latency feature reads and permits bounded fallback when cache data is missing.
- Kafka KRaft 4.2 receives domain events without ZooKeeper. Docker Compose exposes Kafka on `localhost:29092` for host tools and `kafka:9092` for the app container.
- A transactional outbox ensures the API records a decision before event publishing and retries Kafka failures safely.

## Implemented requirements

| Requirement | Implementation |
| --- | --- |
| Sync decision API | `POST /api/v1/fraud/decisions` returns after persistence. |
| Score each transaction under 100ms | Request path uses Redis-first feature lookup, bounded timeouts, deterministic in-memory rules, non-blocking R2DBC, and Kafka publishing outside the request path. |
| Async enrichment pipeline | Decision transaction writes `fraud.enrichment.requested` to outbox; Kafka consumer refreshes PostgreSQL + Redis features. |
| Cache feature data in Redis | `FeatureCacheService` reads and writes SHA-256-hashed feature keys instead of raw customer and merchant identifiers. |
| Fallback if missing | Missing/failed feature lookup falls back to deterministic safe defaults and still records the decision. |
| Log decision reasons | Decision reasons are logged and stored as JSONB. |
| Publish `FraudDecisionMade` | `FraudDecisionMadeEvent` is stored in outbox and published to Kafka. |
| Explainability | Every score has stable reason codes, messages, contribution values, and a persisted scoring `modelVersion`. |
| Consistent decisions under retries | `transaction_id` is unique; SHA-256 request fingerprint returns same decision for exact retry and rejects changed payloads with HTTP 409. |
| Decision always recorded | API success is returned only after decision and outbox events are committed. |
| Horizontal outbox safety | Publisher claims events with PostgreSQL `FOR UPDATE SKIP LOCKED`, `PROCESSING` TTL recovery, retry backoff, and `DEAD_LETTER` state. |
| Operational traceability | `X-Correlation-Id` is generated or echoed and persisted with the decision. |

## Architecture

```text
Client
  |
  | POST /api/v1/fraud/decisions
  v
CorrelationIdWebFilter
  |
  v
WebFlux Controller
  |
  v
FraudDecisionService
  |-- find existing decision by transaction_id for idempotent retry
  |-- resolve features: Redis -> PostgreSQL -> deterministic fallback
  |-- run deterministic scoring rules with modelVersion
  |-- transactionally insert fraud_decisions + outbox_events
  v
PostgreSQL
  |
  v
OutboxPublisher -- claim with FOR UPDATE SKIP LOCKED --> Kafka KRaft
  |
  v
FraudEnrichmentConsumer -> FeatureCacheService -> PostgreSQL + Redis
```

## API

### Score a transaction

```http
POST /api/v1/fraud/decisions
X-Correlation-Id: optional-correlation-id
Content-Type: application/json
```

```json
{
  "transactionId": "tx-1001",
  "customerId": "customer-1",
  "merchantId": "merchant-1",
  "amount": 400.00,
  "currency": "PLN",
  "country": "PL",
  "channel": "ECOMMERCE",
  "deviceId": "device-1",
  "ipAddress": "203.0.113.20",
  "occurredAt": "2026-07-04T10:00:00Z"
}
```

Example response:

```json
{
  "decisionId": "a7ddc021-9f46-4ab5-a651-31cc3a2ef240",
  "transactionId": "tx-1001",
  "modelVersion": "rules-2026-07-04",
  "verdict": "REVIEW",
  "score": 55,
  "reasons": [
    {
      "code": "UNUSUAL_AMOUNT",
      "message": "Amount is above 4.0x historical average for this customer and merchant",
      "contribution": 28
    }
  ],
  "featureSource": "REDIS",
  "cacheHit": true,
  "idempotentReplay": false,
  "decisionTimeMs": 8,
  "correlationId": "optional-correlation-id",
  "createdAt": "2026-07-04T10:00:00Z"
}
```

### Get recorded decision

```http
GET /api/v1/fraud/decisions/{transactionId}
```

### Seed feature data for local testing

```http
PUT /api/v1/fraud/features
Content-Type: application/json
```

```json
{
  "customerId": "customer-1",
  "merchantId": "merchant-1",
  "averageAmount": 80.00,
  "chargebackRate": 0.015,
  "highRiskCountry": false,
  "accountAgeDays": 360,
  "velocity24h": 3,
  "priorDeclines24h": 0,
  "source": "REDIS",
  "refreshedAt": "2026-07-04T10:00:00Z"
}
```

## Run locally

### Full stack with Docker Compose

```bash
docker compose up --build
```

Application:

```text
http://localhost:8080
```

Health:

```bash
curl http://localhost:8080/actuator/health
```

### Run tests

```bash
./gradlew clean test
```

The project includes:

- unit tests for scoring determinism and fingerprints;
- integration tests for WebFlux + R2DBC + Flyway + PostgreSQL + Redis;
- retry and conflict tests for idempotency;
- invalid-input tests for API validation;
- a performance smoke test for pure scoring p95;
- Postman collection with success, fallback, retry, and conflict scenarios.

## Performance notes

The synchronous path is designed for p95 under 100ms with local infrastructure:

1. Redis feature lookup is bounded by `fraud.cache.operation-timeout`.
2. PostgreSQL fallback lookup is bounded by the same timeout.
3. If feature data is unavailable or slow, deterministic fallback is used.
4. Scoring is CPU-local and deterministic.
5. Kafka publishing is outside the request path through the outbox publisher.
6. The API returns only after PostgreSQL has recorded the decision.

Prometheus-compatible metrics are available through Actuator. The main timer is:

```text
fraud.decision.latency
```

## Postman

Import this file:

```text
postman/fraud-scoring-decision-service.postman_collection.json
```

Run the requests in this order:

1. Health
2. Seed Redis/PostgreSQL Features
3. Score Transaction - Cached Features
4. Retry Same Transaction - Consistent Decision
5. Get Recorded Decision
6. Conflict - Same Transaction Different Payload
7. Score Transaction - Fallback Features
8. Validation - Invalid Currency And Country

## Database objects

Flyway migration `V1__init.sql` creates:

- `fraud_decisions` for durable decisions, request fingerprint, model version, and explainability JSON;
- `transaction_features` for durable feature fallback;
- `outbox_events` for reliable Kafka publishing with `PENDING`, `PROCESSING`, `FAILED`, `DEAD_LETTER`, and `PUBLISHED` states.

## Kafka topics

- `fraud.decision.made`
- `fraud.enrichment.requested`

Topics are auto-created by Spring Kafka admin in local development. For production, create topics through infrastructure-as-code with replication factor >= 3.

## Production hardening checklist

- Add authentication and service-to-service authorization at the API gateway or resource-server layer.
- Use managed PostgreSQL with tuned R2DBC pool limits.
- Use managed Redis with TLS and eviction policy aligned to feature TTL.
- Use Kafka replication factor >= 3 and min ISR >= 2.
- Add an external feature provider instead of the deterministic enrichment stub.
- Configure dashboards for p50/p95/p99 decision latency, fallback rate, outbox lag, dead-letter count, and Kafka publish failures.
- Store high-risk country and rule thresholds in versioned configuration with approval workflow.
- Add blue/green or canary deployment with backward-compatible Flyway migrations.

## Push to GitHub

After creating an empty repository on GitHub:

```bash
git init
git add .
git commit -m "Initial production-grade fraud scoring service"
git branch -M main
git remote add origin git@github.com:<your-org>/fraud-scoring-decision-service.git
git push -u origin main
```
