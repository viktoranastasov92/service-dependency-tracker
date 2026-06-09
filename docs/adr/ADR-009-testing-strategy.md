# ADR-009: Testing Strategy

## Status
Proposed

## Context
A system responsible for providing accurate blast-radius information during incidents must be highly reliable. The testing strategy defines how correctness is verified at each layer, how tests are structured for speed and isolation, and how the CI pipeline runs them. Testability was treated as a first-class architectural concern when designing the service and repository layers.

## Decision Drivers
- Unit tests must be fast (milliseconds) and runnable without any external process
- Integration tests must verify the full HTTP request/response cycle including serialization and error handling
- Graph-specific logic (traversal, cycle detection) is the highest-risk code and must be tested exhaustively
- Tests must be runnable by any developer with `./mvnw test` — no additional setup required
- The architecture (service layer decoupled from HTTP, repository abstracted via Spring Data) must support testing each layer in isolation
- Test code is production code: it must be readable and maintainable

## Options Considered

### Option 1: Three-tier pyramid — Unit + Spring MVC Slice + Full Integration
**Description:** Follow the classic test pyramid with three tiers:

1. **Unit tests** (`@ExtendWith(MockitoExtension.class)`): Test `GraphTraversalService`, `CycleDetectionService`, and domain logic in complete isolation. Repositories are mocked with Mockito. No Spring context loaded. These are the majority of tests.

2. **Web layer slice tests** (`@WebMvcTest`): Test each `@RestController` in a lightweight Spring context that loads only the web layer. The service layer is mocked. Verifies request mapping, JSON serialization, validation, and error handler behavior. Fast (no full context, no database).

3. **Full integration tests** (`@SpringBootTest` + H2 in-memory): A small number of tests that load the full application context against an in-memory H2 database. Verify that the full stack works end-to-end: HTTP → Controller → Service → Repository → H2 → Response. Use `TestRestTemplate` or `MockMvc` with `@AutoConfigureMockMvc`.

**Advantages:**
- Unit tests run in milliseconds — fast feedback loop
- `@WebMvcTest` catches serialization bugs and route mapping errors without a full context load
- Full integration tests provide a confidence safety net for the complete stack
- Each tier tests exactly one concern — failures are easy to locate
- H2 in-memory mode means integration tests require no external infrastructure

**Trade-offs:**
- Three test types require understanding which tier to add a test to
- Mocking the service layer in `@WebMvcTest` requires keeping mock setups aligned with actual service behavior

**Pick when:** A balanced, well-structured test suite that scales as the codebase grows is the goal. Recommended for this system.

---

### Option 2: Integration-only with `@SpringBootTest`
**Description:** All tests use `@SpringBootTest` with a full application context and H2 in-memory database. No unit tests or web slice tests.

**Advantages:**
- Single test style — developers only need to know one pattern
- Tests are maximally realistic — they exercise the entire stack
- No mocking required; no risk of mocks diverging from real behavior

**Trade-offs:**
- Each test starts a full Spring context — significantly slower than unit tests
- Slow feedback loop discourages test-first development
- Failures may be caused by any layer, making root cause identification harder
- Graph traversal logic (the highest-complexity code) cannot be tested with isolated edge cases as easily

**Pick when:** The team is small, the codebase is simple, and fast feedback is not a priority — neither of which applies here.

---

### Option 3: Testcontainers with a real PostgreSQL for integration tests
**Description:** Replace H2 in integration tests with Testcontainers, which spins up a real PostgreSQL Docker container for each test run. This eliminates the H2/PostgreSQL dialect gap.

**Advantages:**
- Integration tests run against the exact same database as production — no dialect differences
- Catches PostgreSQL-specific issues (constraint names, index behavior, `WITH RECURSIVE` behavior) early
- Testcontainers is well-supported in Spring Boot via `@ServiceConnection`

**Trade-offs:**
- Requires Docker to be running during test execution — breaks `./mvnw test` in Docker-in-Docker CI environments
- Significantly slower than H2 in-memory tests (container startup overhead)
- Overkill if the team commits to the H2-to-PostgreSQL migration path only when promoting to production

**Pick when:** PostgreSQL is the production database and the H2 dialect gap is a real concern — valid but premature at this stage.

## Recommendation
**Option 1: Three-tier pyramid.** Unit tests for graph and domain logic, `@WebMvcTest` slices for controllers, and a small set of `@SpringBootTest` integration tests for full-stack confidence. This keeps the test suite fast, well-organized, and easy to extend. Graph traversal and cycle detection tests deserve the most coverage given they contain the most complex logic.

## Consequences
**If accepted:** Structure tests as follows:
- `src/test/java/.../service/` — unit tests for `GraphTraversalService`, `CycleDetectionService` using Mockito
- `src/test/java/.../rest/` — `@WebMvcTest` slice tests for each controller
- `src/test/java/.../integration/` — `@SpringBootTest` full-stack tests
- Test `application.properties` (in `src/test/resources/`) sets `spring.datasource.url=jdbc:h2:mem:testdb` and `spring.jpa.hibernate.ddl-auto=create-drop`

Add `spring-boot-starter-test` (already present in `pom.xml`) which bundles JUnit 5, Mockito, AssertJ, and MockMvc. No additional test dependencies are required for the recommended approach.

**Watch out for:** Graph traversal tests must cover: empty graph, single node, linear chain, diamond dependency (A→B, A→C, B→D, C→D — D should appear once in results), and a cyclic graph with the visited-set guard preventing infinite loop. Cycle detection tests must cover: no cycle allowed (insert succeeds), direct cycle rejected, transitive cycle rejected, and adding an edge that does not create a cycle is not incorrectly rejected.
