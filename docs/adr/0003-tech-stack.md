# ADR 0003 — Tech stack

- Status: accepted
- Date: 2026-09-29

## Decision

| Layer        | Choice                                                      |
|--------------|-------------------------------------------------------------|
| Backend      | Java 21 (LTS) + Spring Boot 4, Maven wrapper                |
| Persistence  | PostgreSQL 16, Spring Data JPA, Flyway migrations           |
| HTML parsing | jsoup                                                       |
| Auth         | Spring Security + JWT (stateless)                           |
| API docs     | springdoc-openapi (Swagger UI)                              |
| Tests        | JUnit 5, AssertJ, Testcontainers (PostgreSQL)               |
| Mobile       | React Native + Expo + TypeScript (Expo Router, expo-camera) |
| Charts       | victory-native (mobile dashboards)                          |
| Server state | TanStack Query                                              |
| Local infra  | Docker Compose                                              |
| CI           | GitHub Actions                                              |

## Rationale
- **Expo + TypeScript** builds iOS and Android from one codebase, ships a QR/barcode
  scanner, and gives a path to the future web app (React Native Web, shared TS types).
- **Java 21** is an LTS release fully supported by Spring Boot 4 (the current major version).
- The backend is a **modular monolith** with one package per feature (`receipt`,
  `catalog`, `insight`, `user`). It is simple to run and can be split later if needed.
