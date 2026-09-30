# Backend

Java 21 + Spring Boot 4 REST API.

## Requirements

- JDK 21
- Docker (for the local database and for Testcontainers in tests)

## Run locally

```bash
# from the repository root
docker compose up -d postgres

# from backend/
./mvnw spring-boot:run
```

Health check: <http://localhost:8080/actuator/health>

Alternatively, `./mvnw spring-boot:test-run` starts the app with a throwaway
PostgreSQL container via Testcontainers (no compose needed).

## Test

```bash
./mvnw verify
```
