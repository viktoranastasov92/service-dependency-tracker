# Architecture Decision Records

This directory contains the Architecture Decision Records (ADRs) for the **Service Dependency Tracker** — a centralized, API-driven system for tracking and querying microservice dependencies, built to help GlobalCorp on-call engineers quickly assess the blast radius of a failing component.

ADRs document the significant architectural choices made during the design and development of this system. Each record captures the context, the options considered, the recommendation, and the consequences of accepting that recommendation.

## How to Read These ADRs

Each ADR follows a standard structure:
- **Context** — why this decision needed to be made
- **Decision Drivers** — the forces shaping the choice
- **Options Considered** — at least three concrete alternatives with advantages, trade-offs, and guidance on when to pick each
- **Recommendation** — the preferred option and the primary reason for it
- **Consequences** — what changes if accepted, and what to watch out for

## Index

| ID | Title | Status | Summary |
|----|-------|--------|---------|
| [ADR-001](ADR-001-backend-framework.md) | Backend Framework Choice | **Accepted** — Spring Boot 4.x | Adopt Spring Boot 4.x (already scaffolded) for its zero-migration cost, rich ecosystem, and first-class JPA/test support. |
| [ADR-002](ADR-002-build-tool.md) | Build Tool | **Accepted** — Maven with Maven Wrapper | Use Maven via the committed Maven Wrapper (`mvnw`); no migration to Gradle is warranted at this project size. |
| [ADR-003](ADR-003-persistence-strategy.md) | Persistence Strategy | Proposed | Six options across relational (PostgreSQL, H2, SQLite), document (MongoDB), and native graph (Neo4j, ArangoDB) — recommendation: Neo4j. |
| [ADR-004](ADR-004-graph-data-model.md) | Graph Data Model | Proposed | Model the dependency graph as an adjacency list — two relational tables (`services` + `dependencies`) mapped via JPA. |
| [ADR-005](ADR-005-graph-traversal-algorithm.md) | Graph Traversal Algorithm | Proposed | Use iterative BFS with a visited set for safe, cycle-resistant traversal that returns results in level order. |
| [ADR-006](ADR-006-cycle-detection-strategy.md) | Cycle Detection Strategy | Proposed | Prevent cycles at write time via a pre-insert reachability check; reject cycle-creating edges with HTTP 409. |
| [ADR-007](ADR-007-api-design-conventions.md) | API Design Conventions | Proposed | Resource-oriented REST under `/api/v1/` with `upstream` and `downstream` sub-resources for blast-radius queries. |
| [ADR-008](ADR-008-error-handling-strategy.md) | Error Handling Strategy | Proposed | Centralize error handling in a single `@RestControllerAdvice` that maps domain exceptions to a uniform `ApiError` DTO. |
| [ADR-009](ADR-009-testing-strategy.md) | Testing Strategy | Proposed | Three-tier test pyramid: unit tests (Mockito), `@WebMvcTest` slices for controllers, and `@SpringBootTest` integration tests. |
| [ADR-010](ADR-010-ui-frontend-approach.md) | UI / Frontend Approach | Proposed | React (Vite) with `react-flow` for interactive graph visualization; built output served as Spring Boot static files. |
| [ADR-011](ADR-011-containerization.md) | Containerization | Proposed | Multi-stage Dockerfile for the backend JAR with optional `docker-compose.yml`; H2 file volume for persistent data. |
| [ADR-012](ADR-012-application-architecture-layering.md) | Application Architecture Layering | Proposed | Classic three-layer architecture (`rest/` → `service/` → `repository/domain/`) with constructor injection throughout. |
| [ADR-013](ADR-013-api-documentation.md) | API Documentation | Proposed | Auto-generate OpenAPI 3.1 docs and interactive Swagger UI via `springdoc-openapi-starter-webmvc-ui`. |
| [ADR-014](ADR-014-service-identity-and-naming.md) | Service Identity and Naming | Proposed | Use human-readable kebab-case slugs as external service identifiers in API paths and JSON responses. |
| [ADR-015](ADR-015-data-access-pattern.md) | Data Access Pattern | Proposed | Spring Data JPA `JpaRepository` interfaces with `@Query` JPQL for adjacency list lookups; lazy fetch types throughout. |
| [ADR-016](ADR-016-cors-and-api-integration.md) | CORS and API Integration | Proposed | Vite dev proxy for development; Spring Boot serves the React build as static files in production — single artifact, zero CORS config. |

## Dependency Map

The ADRs are not independent — key decisions constrain later ones:

```
ADR-001 (Spring Boot)
  ├── ADR-002 (Maven — already committed)
  ├── ADR-003 (H2 persistence)
  │     └── ADR-015 (Spring Data JPA)
  ├── ADR-004 (Adjacency list model)
  │     ├── ADR-005 (BFS traversal)
  │     └── ADR-006 (Cycle detection)
  ├── ADR-007 (REST conventions)
  │     ├── ADR-008 (Error handling)
  │     └── ADR-013 (API docs — OpenAPI)
  ├── ADR-009 (Test strategy)
  ├── ADR-012 (Layering)
  └── ADR-014 (Service identity)

ADR-010 (React/Vite frontend)
  └── ADR-016 (CORS + Vite proxy)

ADR-011 (Docker)
  └── ADR-003 (H2 volume mount)
```

## Superseding an ADR

When a decision changes, do not delete or edit the original ADR. Create a new ADR that references the superseded one, and update the `Status` field of the superseded ADR to `Superseded by ADR-XXX`.
