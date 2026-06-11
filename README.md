# Service Dependency Tracker

A tool for on-call engineers to register microservices, define dependency edges between them, and query the full upstream/downstream chain of any service to assess blast radius during incidents.

## Architecture & Design Decisions

- **Neo4j for Storage with Cypher as graph traversal implementation**
    - I preferred using neo4j as the system core operation is graph traversal and although I do not have
      real experience with that database, I chose it becase of the built-in support for graph-specific
      relationship definitions. As it stores edges with physical pointers to their endpoints, so that
      traversal can be a single Cypher query and one database round-trip regardless of depth. That additional
      complexity in PostgreSQL for example was not worth that much the effort with these requirements.
    - Trade-offs - smaller support community, can be challenging to deploy to production, will
      be challenging to introduce the team to Cypher as database querying approach

- **Spring boot with Maven**
    - I am familiar and have most experience with them. The ecosystem for Spring Boot provides pretty much
      everything needed and the team can start development immediately.
    - Trade-offs - Quarkus will provide faster startup with lower memory

- **Cycles handling**
    - As per requirements cyclic dependencies should be allowed. That is why
      I have allowed them. There is a bit of a more complex approach to handle those
      dependencies but we will definitely have a support for legacy data that needs to
      be imported if it also has such cyclic dependencies inside.
    - Trade-offs - complexity as every consumer of the traversal results must handle
      those cyclic dependencies (if such are present in the system). Detection queries
      can also be expensive.

- **Resource-oriented REST with nested sub-resources**
    - I chose it because it is easily human-readable if for some reason the on-call
      engineers need to directly hit the APIs instead of using the frontend.
    - Trade-offs: Strict REST conventions like using only nouns and also not having a
      flat URLs (e.g. "/services/{id}/dependencies/{depId}" is three levels deep).

- **Error handling strategy with @RestControllerAdvice**
    - Single audit point and clean controllers with no try/catch repeatable sections in them.
    - Trade-offs: Throwing generic RuntimeException will bypass all typed handlers and hits
      the catch-all 500 Http status code.

- **Three-layer architecture (Controller -> Service -> Repository)**
    - Intuitive for backend developers, easy to debug. A new member of the team can
      locate the right class quickly. As it is a single-module application the architecture
      should not be more complex than the problem it solves.
    - Trade-offs: More complex to swap the persistence layer with a different database.
      If no architecture tests are added then a developer can accidentally return a
      @Node entity directly from a controller.

- **API-first design approach**
    - Better when aligning the API contracts between frontend and backend developers so that
      there are no blockers or issues of something is implemented differently, breaking
      changes will be caught much easier. We also have compile-time contract enforcement
      and no hand-writing of boilerplate records is necessary - request/response model
      classes are generated from the spec
    - Trade-offs: We must be careful when upgrading Spring Boot as the generator version
        must be compatible and mismatch can break the generated code. There is also
        learning curve for the spec format using Cypher for the schemas. Specific
        configurations might also be needed.

## Implementation Note

This project was implemented entirely using [Claude Code](https://claude.ai/code) with the **Claude Sonnet** model. The prompts used throughout the implementation are recorded in [PROMPTS-HISTORY.md](PROMPTS-HISTORY.md) — new joiners can read through them to understand the sequence of steps taken to build the project from scratch.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 4.0.6, Spring Data Neo4j |
| Graph DB | Neo4j 5 |
| API spec | OpenAPI 3.1 (API-first, openapi-generator) |
| Frontend | React 18, TypeScript, Vite 5 |
| Graph UI | React Flow 11 + Dagre layout |
| Build | Maven Wrapper + frontend-maven-plugin |
| Tests | JUnit 5, Mockito, Testcontainers |

## Prerequisites

| Tool | Version | Required for |
|------|---------|-------------|
| Docker + Docker Compose | any recent | Running Neo4j; Tier 3 tests |
| Java 17 | exactly 17 | Building and running the backend |
| Node.js 20 | ≥ 20 | Frontend dev server (Maven downloads it automatically for `mvn package`) |

---

## IDE Setup (after a fresh checkout)

The OpenAPI generator runs during the Maven build and writes controller interfaces and DTO classes to `target/generated-sources/`. Run this once so the IDE can resolve them:

```bash
./mvnw generate-sources -Dskip.frontend.build=true
```

In IntelliJ IDEA, right-click `target/generated-sources/openapi/src/main/java` → **Mark Directory as → Generated Sources Root**.

---

## Quick Start — Docker Compose

The simplest way to run the full stack. Builds the JAR (including the frontend) and starts both Neo4j and the application.

```bash
# Build the JAR (downloads Node 20 automatically, runs npm build)
./mvnw package -DskipTests

# Start Neo4j + application
docker compose up
```

Open **http://localhost:8080/api/v1/** in a browser.  
Swagger UI is at **http://localhost:8080/api/v1/swagger-ui.html**.

To stop:

```bash
docker compose down          # keep Neo4j data
docker compose down -v       # also delete the Neo4j data volume
```

---

## Development Setup

Run the frontend and backend separately so you get hot-reload on both sides.

### 1. Start Neo4j

```bash
docker compose up -d neo4j
```

Wait until it is healthy (about 15 seconds):

```bash
docker compose ps neo4j      # Status should show "(healthy)"
```

### 2. Start the Backend

```bash
./mvnw spring-boot:run -Dskip.frontend.build=true
```

The API is available at **http://localhost:8080/api/v1/**.

To connect to a Neo4j instance other than the one started by Docker Compose, override these environment variables:

```bash
SPRING_NEO4J_URI=bolt://localhost:7687 \
SPRING_NEO4J_AUTHENTICATION_USERNAME=neo4j \
SPRING_NEO4J_AUTHENTICATION_PASSWORD=trackerpassword \
./mvnw spring-boot:run -Dskip.frontend.build=true
```

### 3. Start the Frontend Dev Server

```bash
cd frontend
npm install          # first time only
npm run dev
```

Open **http://localhost:5173/** in a browser. All `/api/*` requests are proxied to the Spring Boot backend on port 8080.

---

## Building

### Full build (backend + frontend)

```bash
./mvnw package -DskipTests
```

This:
1. Downloads Node 20.11.0 (first run only, cached in `target/`)
2. Runs `npm install` and `npm run build` inside `frontend/`
3. Copies `frontend/dist/` into `target/classes/static/` so Spring Boot serves it
4. Compiles and packages the JAR

The resulting JAR at `target/service-dependency-tracker-*.jar` is a self-contained artifact that serves both the API and the React SPA.

### Backend only (skip frontend)

```bash
./mvnw package -DskipTests -Dskip.frontend.build=true
```

Useful during backend development to avoid running `npm` on every build.

---

## Running Tests

### Tier 1 — Unit tests (Mockito, no I/O)

```bash
./mvnw test -Dskip.frontend.build=true -Dtest="*ServiceTest,*ControllerTest" -DfailIfNoTests=false
```

### Tier 2 — Controller slice tests (MockMvc standaloneSetup, no Spring context)

These are included in the default `mvn test` run alongside Tier 1.

### Tier 3 — Integration tests (Testcontainers + real Neo4j)

Requires Docker.

```bash
./mvnw test -Dskip.frontend.build=true
```

Testcontainers pulls `neo4j:5` and starts a throwaway container for the duration of the test run. Tests are grouped into:

- `ServiceRepositoryTest` — Cypher query correctness against Neo4j
- `ServiceDependencyTrackerIT` — full Spring context end-to-end through the HTTP layer

### All tests with coverage report

```bash
./mvnw verify -Dskip.frontend.build=true
```

---

## API Reference

The hand-authored OpenAPI spec lives at `src/main/resources/openapi/api.yaml`.

When the application is running, Swagger UI is available at:

```
http://localhost:8080/api/v1/swagger-ui.html
```

### Key endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/services` | Register a new service |
| `GET` | `/api/v1/services` | List all services |
| `DELETE` | `/api/v1/services/{name}` | Delete a service and all its edges |
| `POST` | `/api/v1/services/{name}/dependencies` | Add a dependency edge |
| `GET` | `/api/v1/services/{name}/dependencies` | List direct dependencies |
| `DELETE` | `/api/v1/services/{name}/dependencies/{depName}` | Remove a dependency edge |
| `GET` | `/api/v1/services/{name}/downstream` | Full downstream chain + cycle report |
| `GET` | `/api/v1/services/{name}/upstream` | Full upstream chain (blast radius) |

Service names must match `^[a-z0-9-]+$` (kebab-case, max 100 characters).

### Dependency types

`RUNTIME` (default) · `BUILD` · `OPTIONAL`

### Cycles

Cycles are **stored, not rejected**. Adding an edge that creates a cycle returns `201`. Cycles are detected at read time and reported in the `cycles` field of traversal responses as ordered sequences of service names where the last element equals the first.

---

## Project Structure

```
.
├── src/
│   ├── main/
│   │   ├── java/com/example/service_dependency_tracker/
│   │   │   ├── config/          # SPA fallback controller
│   │   │   ├── domain/          # ServiceNode, DependsOnRelationship (@Node, @RelationshipProperties)
│   │   │   ├── exception/       # ServiceNotFoundException, DependencyNotFoundException, DuplicateServiceException
│   │   │   ├── repository/      # ServiceRepository (all Cypher queries)
│   │   │   ├── rest/            # Controllers implementing generated OpenAPI interfaces
│   │   │   └── service/         # ServiceManagementService, GraphTraversalService, CycleReportingService
│   │   └── resources/
│   │       └── openapi/api.yaml # Hand-authored OpenAPI 3.1 spec (source of truth)
│   └── test/
│       └── java/.../
│           ├── service/         # Tier 1: Mockito unit tests
│           ├── rest/            # Tier 2: MockMvc controller slice tests
│           └── integration/     # Tier 3: full Spring context + Testcontainers
├── frontend/
│   ├── src/
│   │   ├── api/client.ts        # Fetch wrapper for all API calls
│   │   ├── components/
│   │   │   └── DependencyGraph.tsx  # React Flow canvas with Dagre layout
│   │   ├── types/index.ts       # TypeScript interfaces matching OpenAPI DTOs
│   │   └── App.tsx              # Single-page app — service list, graph, dependency panel
│   ├── vite.config.ts           # Dev proxy (/api → localhost:8080); base=/api/v1/ for prod build
│   └── package.json
├── docs/                        # Architecture Decision Records (ADR-005 … ADR-016)
├── Dockerfile                   # Two-stage build: eclipse-temurin:17-jdk → 17-jre
├── docker-compose.yml           # Neo4j (with healthcheck) + application
└── pom.xml
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_NEO4J_URI` | `bolt://localhost:7687` | Neo4j Bolt URI |
| `SPRING_NEO4J_AUTHENTICATION_USERNAME` | `neo4j` | Neo4j username |
| `SPRING_NEO4J_AUTHENTICATION_PASSWORD` | `password` | Neo4j password |

The Docker Compose setup sets all three automatically.

---

## Remaining Work

The items below are out of scope for this prototype but required before the system could be considered production-ready.

### Security

- **Authentication & authorisation** — the API is currently open with no authentication. Add Spring Security with OAuth2 / OpenID Connect (e.g. Keycloak, Auth0, or an internal IdP) so that every API request carries a verified identity. Introduce role-based access control: a read-only `viewer` role for on-call engineers querying blast radius, and a `maintainer` role for registering or deleting services and dependency edges.
- **TLS / HTTPS** — traffic between the browser and the server and between the application and Neo4j is currently unencrypted. Terminate TLS at a reverse proxy (nginx, a cloud load balancer) in front of Spring Boot, and enable the Neo4j Bolt+TLS protocol (`bolt+s://`) for the database connection.
- **Swagger UI protection** — the interactive API documentation at `/api/v1/swagger-ui.html` is publicly accessible. In production, restrict it to authenticated users or disable it entirely via `springdoc.swagger-ui.enabled=false` behind an environment flag.
- **Rate limiting** — the API has no rate limiting. A malicious or misconfigured client could flood the traversal endpoints with deep queries. Add a rate-limiting filter (Spring Cloud Gateway, Bucket4j, or a reverse-proxy policy) and cap individual request depth via the `tracker.traversal.max-depth` property.
- **Secrets management** — Neo4j credentials are currently passed as plain-text environment variables. In a production deployment, source secrets from a dedicated secrets manager (HashiCorp Vault, AWS Secrets Manager, Kubernetes Secrets with encryption at rest) and rotate them on a schedule.
- **Audit logging** — there is no record of who registered, modified, or deleted a service or dependency edge. Add a structured audit log (who, what, when, from which IP) written to a separate append-only sink so changes can be reviewed after an incident.
- **Input sanitisation hardening** — service names are validated against `^[a-z0-9-]+$`. Review all other free-text inputs (description field) for maximum length enforcement and ensure they are never interpolated into Cypher outside of parameterised queries.

### Observability

- **Metrics** — add Spring Boot Actuator and Micrometer with a Prometheus scrape endpoint (`/actuator/prometheus`). Expose custom metrics: traversal query duration, cycle detection count, number of registered services, and dependency edge count.
- **Distributed tracing** — integrate OpenTelemetry (Micrometer Tracing + an OTLP exporter) to produce trace spans for each API request, including the Neo4j Cypher execution time. Export to Jaeger, Zipkin, or a cloud APM (Datadog, Honeycomb).
- **Structured logging** — switch from plain-text log lines to structured JSON (Logback with `logstash-logback-encoder`) so logs are parseable by log aggregation tools (ELK stack, Loki, CloudWatch Logs Insights).
- **Health checks** — expose a detailed `/actuator/health` endpoint that includes Neo4j connectivity status, and wire it into the Docker Compose `healthcheck` for the application container (currently only Neo4j has one).

### API & Data Model

- **Service update endpoint** — there is no `PATCH /services/{name}` endpoint to update a service's description after registration. Add one.
- **Pagination on `GET /services`** — the list-all endpoint returns every service in one response. Add cursor- or page-based pagination before the service registry grows large.
- **Per-request depth override** — the traversal depth cap (`tracker.traversal.max-depth`) is a server-wide setting. Expose an optional `?maxDepth=N` query parameter on the downstream and upstream endpoints so callers can request a shallower traversal for low-latency use cases.
- **Bulk import endpoint** — provide a `POST /import` endpoint that accepts a JSON graph (services + edges) and loads it transactionally, for migrating an existing registry or seeding from a configuration file.
- **API versioning strategy** — the current `/api/v1/` prefix reserves the option for a `v2` but there is no documented strategy for introducing breaking changes. Define a deprecation and migration policy before the first external consumer is onboarded.

### Testing

- **Frontend unit & component tests** — there are no automated tests for the React application. Add Vitest + React Testing Library for component logic and API client tests.
- **End-to-end browser tests** — add Playwright or Cypress tests that drive the full UI against a running backend to catch regressions in the graph visualisation and form interactions.
- **Performance tests** — verify traversal query performance at realistic graph sizes (1 000+ services, 5 000+ edges) with a load test (k6, Gatling) targeting the downstream and upstream endpoints.
- **Security scanning** — integrate OWASP Dependency-Check into the Maven build to surface known CVEs in third-party dependencies. Add a SAST scan (SpotBugs, Semgrep) to the CI pipeline.
- **Architecture tests** - ensure architecture tests verify that there are strict rules for access between the different architecture layers - for example a service model must not be returned from the rest layer.

### CI / CD

- **CI pipeline** — there is no continuous integration pipeline. Add a GitHub Actions (or equivalent) workflow that runs on every pull request: compile, run all three test tiers (Testcontainers requires a Docker daemon in the runner), and report coverage.
- **Container image publishing** — the Dockerfile is present but no pipeline publishes images to a container registry. Add a CD step that builds, tags, and pushes the image on merge to main.
- **Infrastructure as code** — the Docker Compose file is suitable for local development only. For a real deployment, define the infrastructure (Kubernetes manifests, Helm chart, or a cloud-provider IaC template) so environments are reproducible and version-controlled.

### Neo4j Production Concerns

- **Neo4j authentication** — the Docker Compose configuration uses `NEO4J_AUTH: neo4j/trackerpassword`. Production instances must use a strong, rotated password sourced from a secrets manager.
- **Backup & recovery** — there is no backup strategy for the Neo4j data volume. Define a backup schedule (Neo4j's `neo4j-admin dump` or a cloud snapshot) and test the restore procedure.
- **Neo4j clustering / high availability** — the current setup is a single Neo4j instance with no replication. For a production deployment that must survive node failures, use Neo4j's Causal Cluster or Aura Enterprise.
- **Database schema constraints** — the Neo4j schema relies on application-level uniqueness enforcement (duplicate service name → 409 from the service layer). Add a native Neo4j uniqueness constraint (`CREATE CONSTRAINT FOR (s:Service) REQUIRE s.name IS UNIQUE`) so the guarantee holds even if the database is written to directly outside the application.
