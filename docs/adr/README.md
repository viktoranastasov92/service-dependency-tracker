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
| [ADR-003](ADR-003-persistence-strategy.md) | Persistence Strategy | **Accepted** — Neo4j | Six options across relational, document, and native graph categories; Neo4j chosen for native graph traversal and zero impedance mismatch. |
| [ADR-004](ADR-004-graph-data-model.md) | Graph Data Model | **Accepted** — `@RelationshipProperties` | Neo4j property graph: `@Node("Service")` with `@RelationshipProperties` on `DEPENDS_ON` edges to capture type and timestamps. |
| [ADR-005](ADR-005-graph-traversal-algorithm.md) | Graph Traversal Algorithm | **Accepted** — Cypher variable-length + path-collecting | Option 1 for upstream/downstream traversal; Option 3 (path-collecting Cypher) for the UI graph visualization endpoint. |
| [ADR-006](ADR-006-cycle-detection-strategy.md) | Cycle Detection Strategy | **Accepted** — Allow cycles; report at read time | Cycles are valid data; no insert validation. Traversal responses include a `cycles` annotation when cycles are detected via Cypher scan. |
| [ADR-007](ADR-007-api-design-conventions.md) | API Design Conventions | **Accepted** — Resource-oriented REST with nested sub-resources | REST under `/api/v1/services` with `/dependencies`, `/upstream`, and `/downstream` sub-resources. |
| [ADR-008](ADR-008-error-handling-strategy.md) | Error Handling Strategy | **Accepted** — `@RestControllerAdvice` / `GlobalExceptionHandler` | Centralized `GlobalExceptionHandler` maps domain exceptions to a uniform `ApiError` DTO; no `CycleDetectedException` (cycles are valid data per ADR-006). |
| [ADR-009](ADR-009-testing-strategy.md) | Testing Strategy | **Accepted** — Three-tier pyramid | Mockito unit tests + `@WebMvcTest` slices + `@DataNeo4jTest` / `@SpringBootTest` with Testcontainers Neo4j for integration tests. |
| [ADR-010](ADR-010-ui-frontend-approach.md) | UI / Frontend Approach | **Accepted** — React (Vite) + `react-flow` | React (Vite) with `react-flow` for interactive graph visualization; built output served as Spring Boot static files. |
| [ADR-011](ADR-011-containerization.md) | Containerization | **Accepted** — Full Docker Compose stack with healthcheck | Multi-stage Dockerfile + Compose with Neo4j healthcheck and `depends_on: condition: service_healthy` to eliminate startup race condition. |
| [ADR-012](ADR-012-application-architecture-layering.md) | Application Architecture Layering | **Accepted** — Classic three-layer (Controller → Service → Repository) | `rest/` → `service/` → `repository/domain/` with constructor injection; one-directional dependencies, independently testable per layer. |
| [ADR-013](ADR-013-api-documentation.md) | API Documentation | **Accepted** — API-first with `openapi-generator-maven-plugin` | Spec (`api.yaml`) is written first; plugin generates Spring MVC interfaces and DTOs; controllers implement generated interfaces; compile-time contract enforcement. |
| [ADR-014](ADR-014-service-identity-and-naming.md) | Service Identity and Naming | **Accepted** — Human-readable kebab-case slugs | Slugs (`payment-service`, `user-auth-db`) as external identifiers in URLs and JSON; surrogate UUID kept internal to the database. |
| [ADR-015](ADR-015-data-access-pattern.md) | Data Access Pattern | **Accepted** — `Neo4jRepository` with `@Query` Cypher | `Neo4jRepository` for CRUD; `@Query` Cypher annotations for traversal, cycle reporting, and direct-neighbour queries; `ServiceWithDepthDTO` projection for depth-aware results. |
| [ADR-016](ADR-016-cors-and-api-integration.md) | CORS and API Integration | **Accepted** — Vite proxy + Spring Boot static files | Vite proxies `/api/**` to `:8080` in dev (zero CORS config); Spring Boot serves React build from `static/` in production — single JAR artifact. |

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
