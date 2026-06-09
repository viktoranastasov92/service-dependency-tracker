# ADR-009: Testing Strategy

## Status
Proposed

## Context
A system responsible for providing accurate blast-radius information during incidents must be highly reliable. With Neo4j chosen as the persistence layer (ADR-003), the integration test strategy must account for the absence of an embedded in-memory database — there is no Neo4j equivalent of H2 `:mem:`. Integration tests that exercise the Neo4j repository layer must use either Testcontainers (a real Neo4j Docker container) or Spring Data Neo4j's `@DataNeo4jTest` slice with Testcontainers. This ADR defines the three-tier test pyramid for this specific stack.

## Decision Drivers
- Unit tests must be fast (milliseconds) and runnable without any external process or Docker
- Integration tests must exercise real Neo4j behaviour — Cypher query correctness, relationship mapping, and traversal results cannot be validated against a mock
- Graph-specific logic (Cypher traversal queries, cycle detection) is the highest-risk code and must be tested with a real Neo4j instance
- Tests must be runnable by any developer with `./mvnw test` given Docker is available (Docker is required for Neo4j regardless — see ADR-011)
- Test code is held to the same quality standard as production code
- TDD discipline: failing tests are written before implementation

## Options Considered

### Option 1: Three-tier pyramid — Unit (Mockito) + `@DataNeo4jTest` (Testcontainers) + `@SpringBootTest` (Testcontainers)
**Description:** Three test tiers with increasing scope and infrastructure cost:

**Tier 1 — Unit tests** (`@ExtendWith(MockitoExtension.class)`):
Test all service-layer logic (`GraphTraversalService`, `CycleReportingService`, `ServiceManagementService`) in complete isolation. `ServiceRepository` is mocked with Mockito. No Spring context, no Neo4j, runs in milliseconds. `CycleReportingService` is the service responsible for detecting cycles at read time (ADR-006 Option 2) and annotating traversal responses — its unit tests verify that the annotation logic is correct given a mocked repository response, not that cycles are rejected. These are the majority of tests and are written first (TDD).

**Tier 2 — Repository slice tests** (`@DataNeo4jTest` + Testcontainers):
Test `@Query`-annotated Cypher queries against a real Neo4j container. Spring Data Neo4j's `@DataNeo4jTest` loads only the Neo4j slice (repositories, node entities) without starting the web layer. Uses a shared singleton Testcontainers `Neo4jContainer` to avoid per-test container startup cost.

```java
@DataNeo4jTest
@Testcontainers
class ServiceRepositoryTest {
    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5")
        .withoutAuthentication();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.neo4j.uri", neo4j::getBoltUrl);
    }
}
```

**Tier 3 — Full integration tests** (`@SpringBootTest` + Testcontainers):
A small number of tests that load the full application context and exercise the complete HTTP → Controller → Service → Repository → Neo4j → Response stack. The same singleton `Neo4jContainer` is reused. Use `MockMvc` with `@AutoConfigureMockMvc` to drive HTTP requests.

**Advantages:**
- Unit tests (Tier 1) provide fast feedback without infrastructure — the TDD cycle stays tight
- `@DataNeo4jTest` validates Cypher query correctness against a real Neo4j instance without loading the web layer
- Singleton container pattern amortizes Neo4j startup cost across the full test run (~5–10 seconds once)
- Each tier tests exactly one concern — failures are easy to locate
- `@DataNeo4jTest` is purpose-built for this stack and officially supported

**Trade-offs:**
- Docker must be available to run Tier 2 and Tier 3 tests — `./mvnw test` requires Docker
- First run is slower than H2-based tests due to container pull and startup; subsequent runs use the local image cache
- Developers must understand which tier to add a new test to

**Pick when:** Neo4j is the persistence layer and comprehensive Cypher query validation is required. This is the recommended approach.

---

### Option 2: Unit + `@WebMvcTest` + `@SpringBootTest` with mocked repository (no Neo4j in tests)
**Description:** Mock the `ServiceRepository` at every test tier — even in `@SpringBootTest` — using `@MockBean`. No Testcontainers or real Neo4j instance in any test. Cypher query correctness is not directly verified by automated tests.

**Advantages:**
- No Docker required for any test — `./mvnw test` runs anywhere with Java 17
- Fastest possible suite — no container startup overhead
- `@WebMvcTest` is the correct slice for controller and serialization testing

**Trade-offs:**
- The most critical code — the `@Query` Cypher strings — is never executed against a real Neo4j instance by automated tests
- A broken Cypher query (wrong syntax, wrong return type, wrong relationship direction) will pass all tests and fail in production
- Mocks diverge from real SDN behaviour over time (especially around eager loading, projection mapping, and relationship hydration)
- Fundamentally undermines the value of the test suite for the graph layer

**Pick when:** Docker is completely unavailable in the CI environment and cannot be enabled — a last resort, not a choice.

---

### Option 3: Testcontainers for all tiers — no mocking of the repository layer
**Description:** Use Testcontainers Neo4j for all tests that involve the repository. Skip `@DataNeo4jTest` slices; use only `@SpringBootTest` with the full context and a shared `Neo4jContainer`. Unit tests still mock the service layer for controller tests.

**Advantages:**
- Maximum realism — every test that touches Neo4j uses a real instance
- No risk of mock divergence
- Simplest test configuration: one container shared by all tests

**Trade-offs:**
- No isolated repository-only slice — all Neo4j tests load the full Spring context
- Slower than the `@DataNeo4jTest` slice approach for repository-focused tests
- Harder to isolate a failing Cypher query from a failing controller mapping

**Pick when:** The team prefers fewer test configuration patterns and is willing to trade slice isolation for simplicity.

## Recommendation
**Option 1: Three-tier pyramid with `@DataNeo4jTest` + Testcontainers.** The `@DataNeo4jTest` slice validates Cypher queries efficiently with minimal context overhead. The singleton container pattern keeps the suite fast. Unit tests keep the TDD cycle tight for service-layer logic. Docker is already a project dependency (ADR-011 includes Neo4j in Docker Compose), so requiring it for tests is consistent with the project's infrastructure assumptions.

## Consequences
**If accepted:** Structure tests as:
- `src/test/java/.../service/` — Mockito unit tests for `GraphTraversalService`, `CycleReportingService`, `ServiceManagementService`
- `src/test/java/.../rest/` — `@WebMvcTest` slice tests for each controller (service layer mocked with `@MockBean`)
- `src/test/java/.../repository/` — `@DataNeo4jTest` + Testcontainers slice tests for all `@Query` Cypher methods
- `src/test/java/.../integration/` — `@SpringBootTest` + Testcontainers full-stack tests for end-to-end HTTP scenarios

Add to `pom.xml`: `spring-boot-starter-test` (already present), `testcontainers` BOM, `org.testcontainers:neo4j`. Create a shared `Neo4jTestcontainerConfig` base class or `@TestConfiguration` that starts the singleton container and registers `@DynamicPropertySource`.

**Graph traversal test scenarios** (cycles are allowed — ADR-006 Option 2 — so test cases must reflect this):
- Empty graph: traversal of a service with no dependencies returns an empty result
- Single node, no edges: upstream and downstream both return empty
- Linear chain (A→B→C→D): downstream of A returns B, C, D with correct depths; upstream of D returns C, B, A
- Diamond (A→B, A→C, B→D, C→D): downstream of A returns B, C, D — D appears exactly once (`DISTINCT` guard)
- Direct cycle (A→B, B→A): both edges are stored without error; downstream of A returns B, upstream of A returns B; the response includes a `cycles` annotation identifying the A↔B cycle
- Transitive cycle (A→B→C→A): all three edges are stored; traversal terminates at the configured `max-depth` bound and does not loop; the `cycles` annotation lists [A, B, C, A]
- Self-loop (A→A): stored without error; traversal returns no additional nodes but annotates the self-loop in `cycles`
- Disconnected graph: upstream/downstream of a service with no connections to the queried node returns empty

**Cycle reporting test scenarios** (`CycleReportingService` unit tests):
- Repository returns a non-empty cycle path list → `cycles` field in response DTO is populated correctly
- Repository returns an empty list → `cycles` field is absent or empty; no false-positive cycle annotation
- Multiple distinct cycles reachable from the same node → all cycles are included in the annotation

**`@DataNeo4jTest` repository test scenarios** (Cypher query correctness against real Neo4j):
- `findAllDownstream` returns `DISTINCT` results on a diamond graph — D appears once, not twice
- `findAllDownstream` terminates on a cyclic graph and does not exceed `max-depth` results
- `findAllUpstream` returns correct direction (incoming, not outgoing)
- `findCyclesFrom` returns all cycle paths reachable from a given node; returns empty on an acyclic subgraph
- `findSubgraphEdges` (visualization) returns the full edge list for the UI; edges in cycles appear once

**Watch out for:** `@DataNeo4jTest` by default enables Neo4j's reactive driver. If the project uses the imperative (non-reactive) driver, set `spring.neo4j.pool.enabled=true` and ensure the test slice does not attempt to configure a reactive session factory. Verify the Testcontainers Neo4j image version matches the production Neo4j version (currently `neo4j:5`). Clear the Neo4j database between `@DataNeo4jTest` tests using `@BeforeEach` with a `MATCH (n) DETACH DELETE n` Cypher statement to prevent cycle data from one test leaking into another.
