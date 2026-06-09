# ADR-011: Containerization

## Status
Proposed

## Context
The requirements list containerization as optional. Containerizing the application makes it portable across developer machines, simplifies onboarding, and provides a clear path to deploying the system in GlobalCorp's infrastructure. The question is whether to containerize the backend only, use Docker Compose for a full local stack, or defer entirely.

## Decision Drivers
- The system is internal tooling — deployment simplicity matters more than advanced orchestration
- Developer onboarding must remain simple: a new team member should be able to run the full system in under five minutes
- The H2 file-backed database (ADR-003) means no separate database container is required for the minimal setup
- Optional containerization should not block delivering the core API functionality
- A Dockerfile should be production-grade (non-root user, minimal base image, explicit port exposure)

## Options Considered

### Option 1: Multi-stage Dockerfile for the backend + optional Docker Compose
**Description:** A two-stage Dockerfile: the first stage uses `eclipse-temurin:17-jdk` to build the JAR with Maven; the second stage uses `eclipse-temurin:17-jre` to run it. A `docker-compose.yml` is provided for convenience, mounting a volume for the H2 data file. The frontend build is optionally included in the same image or served separately.

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

**Advantages:**
- Multi-stage build keeps the final image small (JRE only, no Maven/JDK)
- Non-root user is a security best practice and easy to implement
- `docker-compose.yml` with a volume mount for the H2 data directory gives persistent storage across container restarts
- Anyone with Docker can run `docker compose up` with no Java or Maven installation
- Provides a natural path to deploying on Kubernetes or any container platform

**Trade-offs:**
- Build inside Docker is slower than a local Maven build on a warm machine (no layer cache on first build)
- H2 in file mode inside a container requires a Docker volume — forgetting to mount it loses data on container recreation
- Developers still need the local Maven workflow for rapid iteration

**Pick when:** The team wants a reproducible, shareable run environment and the system will eventually be deployed to a container platform. Recommended.

---

### Option 2: Docker Compose with PostgreSQL replacing H2
**Description:** Rather than using H2, use Docker Compose to run both the Spring Boot application and a PostgreSQL container. The application connects to PostgreSQL over the Compose internal network. Data is persisted in a named Docker volume.

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: tracker
      POSTGRES_USER: tracker
      POSTGRES_PASSWORD: tracker
    volumes:
      - postgres-data:/var/lib/postgresql/data
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/tracker
    depends_on:
      - db
volumes:
  postgres-data:
```

**Advantages:**
- Production-equivalent database from day one
- No H2/PostgreSQL dialect risk (see ADR-003)
- The Compose file is the single source of truth for the entire stack

**Trade-offs:**
- More complex Compose file; developers must understand Docker networking
- Requires Docker for any development — cannot run the backend alone with `./mvnw spring-boot:run`
- Overrides the ADR-003 decision to use H2 for simplicity

**Pick when:** The team decides to adopt PostgreSQL as the primary database (ADR-003, Option 2), making Docker a mandatory development dependency.

---

### Option 3: No containerization — JAR only
**Description:** The deliverable is an executable JAR built with `./mvnw package`. Documentation describes how to run it with `java -jar`. No Dockerfile or Docker Compose file is provided.

**Advantages:**
- Zero additional configuration or files to maintain
- Runs on any machine with Java 17 installed — no Docker required
- Simplest possible delivery for a proof-of-concept

**Trade-offs:**
- Onboarding requires Java 17 to be installed and on `PATH`
- No reproducible environment — "works on my machine" problems may surface
- Deployment to any container platform requires writing a Dockerfile later anyway

**Pick when:** The project is a short-lived prototype with a single developer and Docker is not available.

## Recommendation
**Option 1: Multi-stage Dockerfile with optional Docker Compose.** It keeps the development default simple (`./mvnw spring-boot:run` with H2) while providing a container path for sharing and deployment. The Dockerfile is a small investment that pays off immediately when onboarding new team members or demonstrating the system to GlobalCorp.

## Consequences
**If accepted:** Add `Dockerfile` and `docker-compose.yml` to the project root. The Compose file mounts `./data` as a volume into the container at the path where H2 writes its file. Add `.dockerignore` to exclude `target/`, `.git/`, and `node_modules/`. Document the `docker compose up --build` command in `HELP.md`. The Docker build is not part of the default Maven lifecycle — it runs separately via `docker build`.

**Watch out for:** The H2 data file path inside the container must match the `spring.datasource.url` in `application.properties`. Use an environment variable (`SPRING_DATASOURCE_URL`) to allow overriding the path at container runtime, so the same image can run with H2 in development and PostgreSQL in production without rebuilding.
