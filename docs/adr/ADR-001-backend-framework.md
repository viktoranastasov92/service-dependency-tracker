# ADR-001: Backend Framework Choice

## Status
Accepted — Option 1: Spring Boot 4.x

## Context
The project requires a Java backend that exposes a RESTful API for managing and querying a service dependency graph. The framework choice shapes developer experience, ecosystem availability, startup time, and long-term maintainability. A starter project using Spring Boot 4.x with Maven has already been scaffolded.

## Decision Drivers
- The project already has a Spring Boot 4.x scaffold committed — switching frameworks has a non-zero migration cost
- Java 17 is the baseline; the chosen framework must support it fully
- The team needs a rich ecosystem for web, data access, validation, and testing without assembling it from scratch
- Operational simplicity matters: the system should be runnable locally with a single command
- The framework should have broad community adoption so engineers unfamiliar with the project can ramp up quickly
- Startup time and memory footprint are acceptable trade-offs at this scale (single-node, internal tooling)

## Options Considered

### Option 1: Spring Boot 4.x (current scaffold)
**Description:** Convention-over-configuration framework built on the Spring ecosystem. The `pom.xml` already declares `spring-boot-starter-parent 4.0.6` and `spring-boot-starter-web`, so the project is running today.

**Advantages:**
- Zero migration cost — the scaffold is already working
- Largest Java ecosystem: Spring Data JPA, Spring Security, Actuator, Spring MVC test support all available via single starters
- Extensive documentation, StackOverflow coverage, and tooling (Spring Initializr, IDE plugins)
- `@SpringBootTest` and `MockMvc` provide a complete, well-understood integration-test story
- Spring Boot 4.x targets Jakarta EE 11 and virtual threads out of the box

**Trade-offs:**
- Higher memory baseline and slower cold-start compared to Quarkus/Micronaut (acceptable for always-on internal tooling)
- "Magic" auto-configuration can obscure what is happening; requires discipline to keep the context lean
- Framework updates (Boot 3 → 4) may introduce breaking changes in the dependency chain

**Pick when:** The team already has Spring experience, the scaffold is in place, and operational simplicity is more important than minimal resource footprint.

---

### Option 2: Quarkus 3.x
**Description:** Red Hat's Kubernetes-native Java framework with ahead-of-time compilation via GraalVM Native Image. Designed to minimize startup time and memory use.

**Advantages:**
- Native image produces sub-100 ms startup and dramatically lower memory footprint
- RESTEasy Reactive and Panache ORM provide ergonomic REST and data-access layers
- Dev Services automatically spins up database containers during development — excellent DX
- First-class support for virtual threads (Project Loom)

**Trade-offs:**
- Significant migration cost from the existing Spring Boot scaffold (different annotations, different config model)
- Native image compilation is slow (minutes per build) and requires GraalVM toolchain
- Smaller community than Spring; fewer StackOverflow answers for edge cases
- Some Spring libraries (e.g., Spring Security) are not drop-in compatible

**Pick when:** The service will be deployed to Kubernetes at high replica count and memory/startup constraints are primary NFRs.

---

### Option 3: Micronaut 4.x
**Description:** JVM framework designed for microservices, with compile-time dependency injection (no reflection at runtime) and AOT processing.

**Advantages:**
- Compile-time DI eliminates reflection overhead and catches wiring errors at build time
- Smaller memory footprint than Spring Boot at runtime
- First-class GraalVM Native Image support similar to Quarkus
- Micronaut Data provides type-safe repository generation at compile time

**Trade-offs:**
- Migration cost from the existing Spring Boot scaffold
- Smallest community of the three options; harder to find help for niche problems
- Compile-time DI model is unfamiliar to developers with a Spring background
- Less mature ecosystem for graph-specific concerns

**Pick when:** Compile-time safety and low memory footprint are required, and the team has no existing Spring investment to protect.

## Recommendation
**Option 1: Spring Boot 4.x.** The scaffold is already in place and functional. Migrating to Quarkus or Micronaut would cost days of effort for benefits (startup time, memory) that do not matter for an internal incident-response tool running as a persistent server. The Spring ecosystem provides everything this system needs — web, data access, validation, testing — with zero additional ceremony.

## Consequences
**If accepted:** Development begins immediately on the existing scaffold. Spring Data JPA, Spring MVC, and Spring Boot Test are added as needed. The team can use standard Spring idioms throughout.

**Watch out for:** Spring Boot 4.x requires Java 17+ and Jakarta EE 11 namespace (`jakarta.*` not `javax.*`). Ensure any third-party libraries added are Jakarta-compatible. Keep the Spring application context lean by only enabling the auto-configurations actually needed.
