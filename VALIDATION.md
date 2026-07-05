# Validation report

## Environment researched and retained

- Java target: 25.
- Spring Boot: 4.1.0.
- Gradle wrapper bootstrap: 9.6.1.
- PostgreSQL container: 18.4 Alpine.
- Redis container: 8.2 Alpine.
- Kafka container: Apache Kafka 4.2.0 in KRaft mode.

## Implemented production-grade updates

- Added persisted `model_version` to every fraud decision, response, and `FraudDecisionMade` event.
- Added `idempotentReplay` response metadata so exact retries are visible to clients and tests.
- Added `CorrelationIdWebFilter` to generate or echo `X-Correlation-Id` consistently.
- Hardened request validation for identifier length, ISO-like currency/country format, amount precision, and feature chargeback bounds.
- Changed Redis feature keys to SHA-256 hashes so raw customer and merchant identifiers are not exposed in key names.
- Hardened Kafka producer settings with idempotence, `acks=all`, retries, bounded request/delivery timeouts, compression, and controlled in-flight requests.
- Reworked the outbox publisher to claim rows atomically with PostgreSQL `FOR UPDATE SKIP LOCKED`, `PROCESSING` state, processing TTL recovery, exponential backoff, maximum attempts, and `DEAD_LETTER` state.
- Added race-safe idempotent duplicate handling around concurrent inserts.
- Expanded integration tests for idempotent replay metadata, concurrent same-payload retries, validation failures, and fallback behavior.
- Expanded Postman tests for model version, replay flag, fallback source, conflict, and invalid payload scenarios.
- Updated Docker Compose and Testcontainers image versions to current researched stable lines.

## Checks performed in this sandbox

| Check | Result |
| --- | --- |
| ZIP extracted successfully | Passed |
| Postman collection JSON syntax | Passed |
| `application.yml` syntax with PyYAML | Passed |
| `docker-compose.yml` syntax with PyYAML | Passed |
| Java source package/braces static check | Passed |
| Javadocs present in main Java sources | Passed |
| Old unstable image references removed | Passed |
| Gradle invocation | Blocked by sandbox DNS and Java version |

## Gradle/test limitation

The sandbox runtime has Java 21 and no preinstalled Gradle. The project targets Java 25 and bootstraps Gradle 9.6.1 from `services.gradle.org`. Running `./gradlew --version` attempted to download Gradle but failed because DNS/network access is blocked in this environment:

```text
Downloading Gradle 9.6.1...
curl: (6) Could not resolve host: services.gradle.org
```

Because of that, `./gradlew clean test` could not be executed here. On a developer machine with Java 25 and internet access for the first Gradle bootstrap, run:

```bash
./gradlew clean test
```

For full infrastructure validation, run:

```bash
docker compose up --build
```

Then import `postman/fraud-scoring-decision-service.postman_collection.json` and run the collection in order.
