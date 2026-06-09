# ADR-011: Containerization

## Status
Accepted — Option 1: Multi-stage Dockerfile + Docker Compose with Neo4j healthcheck and `depends_on: condition: service_healthy`

## Context
With Neo4j chosen as the persistence layer (ADR-003), local development now requires a running Neo4j instance — there is no embedded fallback equivalent to H2. This makes Docker a practical requirement for local development rather than a purely optional convenience. The containerization strategy must therefore cover both the Spring Boot application and the Neo4j database as a coherent local stack, while keeping developer onboarding simple.

## Decision Drivers
- Neo4j must be available locally; Docker Compose is the least-friction way to provide it
- Developer onboarding must remain simple: a new team member should run the full stack in under five minutes
- The Spring Boot application container must be production-grade: non-root user, minimal base image, explicit port exposure
- Neo4j data must survive container restarts via a named Docker volume
- The containerization setup must not interfere with the standard `./mvnw spring-boot:run` workflow for rapid development iteration

## Options Considered

### Option 1: Multi-stage Dockerfile for the app + Docker Compose with Neo4j
**Description:** A two-stage Dockerfile builds the production JAR. A `docker-compose.yml` defines two services: `neo4j` (from the official `neo4j:5` image) and `app` (built from the Dockerfile). Neo4j data is persisted in a named Docker volume. The app connects to Neo4j over the Compose internal network.

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src/ src/
RUN ./mvnw package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```yaml
services:
  neo4j:
    image: neo4j:5
    environment:
      NEO4J_AUTH: neo4j/trackerpassword
    ports:
      - "7474:7474"   # Neo4j Browser
      - "7687:7687"   # Bolt protocol
    volumes:
      - neo4j-data:/data

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_NEO4J_URI: bolt://neo4j:7687
      SPRING_NEO4J_AUTHENTICATION_USERNAME: neo4j
      SPRING_NEO4J_AUTHENTICATION_PASSWORD: trackerpassword
    depends_on:
      - neo4j

volumes:
  neo4j-data:
```

**Advantages:**
- `docker compose up --build` brings up the entire stack: Neo4j + Spring Boot app, no manual steps
- Neo4j Browser at `localhost:7474` provides a visual graph explorer out of the box — directly useful for inspecting the dependency graph
- Named volume ensures Neo4j data persists across `docker compose down` / `docker compose up` cycles
- Multi-stage build keeps the final app image small (JRE only, no Maven toolchain)
- Non-root user in the app container is a security best practice
- Developers can still run `./mvnw spring-boot:run` locally (pointing at a standalone Neo4j) for rapid iteration without rebuilding the Docker image

**Trade-offs:**
- Slightly more complex Compose file than the previous H2 volume approach
- First build is slower (Docker layer cache cold); subsequent builds are fast
- Developers must have Docker running for local development (Neo4j is now a hard dependency)
- App container healthcheck for Neo4j readiness should be configured to avoid startup race conditions

**Pick when:** Neo4j is the persistence layer and a fully reproducible, one-command local stack is the goal. Recommended.

---

### Option 2: Neo4j Aura Free (cloud) + JAR-only local run (no Docker for development)
**Description:** Developers run the Spring Boot app locally with `./mvnw spring-boot:run`, connecting to a free Neo4j Aura cloud instance instead of a local Docker container. No `Dockerfile` or `docker-compose.yml` required for development. A `Dockerfile` is provided only for production deployment.

```properties
# application-local.properties (not committed — each developer configures their own Aura credentials)
spring.neo4j.uri=neo4j+s://xxxxxxxx.databases.neo4j.io
spring.neo4j.authentication.username=neo4j
spring.neo4j.authentication.password=<aura-password>
```

**Advantages:**
- Zero Docker requirement for local development — runs on any machine with Java 17
- Neo4j Aura Free provides a persistent cloud graph with the Neo4j Browser built in
- Developers share a single Aura instance (or each provision their own free instance in under two minutes)
- Eliminates Neo4j container startup overhead from the development loop

**Trade-offs:**
- Development depends on an external cloud service — offline development is not possible
- Each developer needs an Aura account and credentials; onboarding requires an extra manual step
- Shared Aura instance means one developer's test data pollutes another's environment (unless each uses their own instance)
- Aura Free has storage and connection limits; adequate for prototyping but not for load or volume testing
- CI/CD still requires a Neo4j instance — Testcontainers (ADR-009) handles this, but the Aura approach does not carry over to CI

**Pick when:** Docker is not available on developer machines but internet access is reliable, and the team is comfortable managing cloud credentials for local development.

---

### Option 3: Docker Compose with Neo4j only — no app container (hybrid local dev)
**Description:** Provide a `docker-compose.yml` that runs only Neo4j (not the Spring Boot app). Developers run the app with `./mvnw spring-boot:run`, connecting to the Compose-managed Neo4j. A `Dockerfile` is provided for production deployment but not used in local development.

```yaml
services:
  neo4j:
    image: neo4j:5
    environment:
      NEO4J_AUTH: neo4j/trackerpassword
    ports:
      - "7474:7474"
      - "7687:7687"
    volumes:
      - neo4j-data:/data

volumes:
  neo4j-data:
```

**Advantages:**
- Keeps the development iteration loop fast: `./mvnw spring-boot:run` restarts in seconds with Spring DevTools hot reload; no Docker rebuild needed
- Neo4j Browser at `localhost:7474` available for graph inspection
- Simpler Compose file — only one service
- The Dockerfile is still provided for production use

**Trade-offs:**
- No single `docker compose up` command runs the full stack — developers must run two separate commands (`docker compose up neo4j` and `./mvnw spring-boot:run`)
- The app is not containerized in development, so Docker-specific issues (networking, volume paths, environment variable injection) only surface when building the production image
- Slightly less reproducible than Option 1

**Pick when:** The primary development workflow is `./mvnw spring-boot:run` with hot reload, and the team wants Neo4j managed by Docker while keeping the app on the host JVM.

## Recommendation
**Option 1: Multi-stage Dockerfile + Docker Compose with Neo4j and app as services.** The `depends_on: condition: service_healthy` constraint on the Neo4j healthcheck eliminates the startup race condition at the infrastructure level — the app container does not start until Neo4j's HTTP endpoint confirms readiness. This is more reliable than any application-level retry mechanism.

## Consequences
**If accepted:** Add `docker-compose.yml` to the project root with both the `neo4j` and `app` services. Configure a Neo4j healthcheck on the HTTP port (`7474`) with `start_period: 30s` to account for Neo4j's JVM warm-up. Set `depends_on: neo4j: condition: service_healthy` on the app service. Add a multi-stage `Dockerfile` for the app. Add `.dockerignore` excluding `target/`, `.git/`, and `node_modules/`. Configure `application.properties` to read Neo4j connection details from environment variables (`SPRING_NEO4J_URI`, `SPRING_NEO4J_AUTHENTICATION_USERNAME`, `SPRING_NEO4J_AUTHENTICATION_PASSWORD`) so the same image works in any environment. Document `docker compose up --build` as the single command to start the full stack in `HELP.md`.

The full Compose stack:
```yaml
services:
  neo4j:
    image: neo4j:5
    environment:
      NEO4J_AUTH: neo4j/trackerpassword
    ports:
      - "7474:7474"
      - "7687:7687"
    volumes:
      - neo4j-data:/data
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:7474 || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_NEO4J_URI: bolt://neo4j:7687
      SPRING_NEO4J_AUTHENTICATION_USERNAME: neo4j
      SPRING_NEO4J_AUTHENTICATION_PASSWORD: trackerpassword
    depends_on:
      neo4j:
        condition: service_healthy

volumes:
  neo4j-data:
```

**Watch out for:** The development iteration loop is slower with this option — every code change requires `docker compose up --build` to rebuild the app image. For rapid local development, run `docker compose up -d neo4j` to start only Neo4j, then run the app with `./mvnw spring-boot:run` pointing at `bolt://localhost:7687`; accept that in this mode the startup race must be managed manually (wait for Neo4j to be ready before starting the app). For Testcontainers in CI (ADR-009), the race condition is handled automatically by the `waitingFor` strategy on the `Neo4jContainer` — no Compose involvement needed in CI.
