# Service Dependency Tracker

A tool for on-call engineers to register microservices, define dependency edges between them, and query the full upstream/downstream chain of any service to assess blast radius during incidents.

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
