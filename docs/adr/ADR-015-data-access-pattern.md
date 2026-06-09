# ADR-015: Data Access Pattern

## Status
Proposed

## Context
The service layer needs to read and write services and dependency edges to the relational database. The data access pattern determines how SQL is generated, how entities are mapped, and how much control developers have over query behavior. Spring Boot 4.x and the JPA ecosystem offer multiple patterns ranging from fully automated to fully manual.

## Decision Drivers
- The domain model is simple: two entity types (`Service`, `Dependency`) with a well-understood schema
- Graph traversal queries load the adjacency list for a given node — these must be performant and not generate N+1 queries
- The persistence layer must be swappable with an in-memory stub for unit tests
- Developers should not need to write boilerplate CRUD SQL
- The pattern must support derived query methods and custom JPQL for the non-trivial lookups needed by the graph layer

## Options Considered

### Option 1: Spring Data JPA with `JpaRepository` interfaces
**Description:** Define repository interfaces that extend `JpaRepository<Entity, ID>`. Spring Data generates CRUD implementations at startup. Complex queries use derived method names (`findByName`, `findByFromService`) or `@Query` annotations with JPQL for graph-specific lookups such as "find all dependencies where this service is the source" or "find all dependencies where this service is the target."

**Advantages:**
- CRUD operations (save, findById, findAll, delete) require zero boilerplate
- Derived query methods (`findAllByFromService_Name(String name)`) are generated at startup and fail fast if misconfigured
- `@Query` with JPQL allows precise control for graph traversal queries without raw SQL
- Spring Data repositories are interfaces — easily mocked in unit tests with Mockito or replaced with `@Repository` in-memory implementations
- Pagination and sorting are built-in via `PagingAndSortingRepository`

**Trade-offs:**
- Generated queries may not be optimal for complex join patterns; `@Query` is needed for anything beyond simple lookups
- JPQL abstracts away some SQL power (e.g., window functions, `WITH RECURSIVE`) — must fall back to `@Query(nativeQuery = true)` for those
- Entity relationships (`@ManyToOne`, `@OneToMany`) require careful fetch type configuration to avoid N+1 and LazyInitializationException

**Pick when:** The domain has standard CRUD needs with a few custom queries. This is the standard Spring Boot data access pattern and is recommended for this system.

---

### Option 2: JDBC Template with hand-written SQL
**Description:** Use `JdbcTemplate` or the newer `JdbcClient` (Spring Framework 6.1+) to execute hand-written SQL queries. Entities are mapped via `RowMapper` implementations.

**Advantages:**
- Complete SQL control — no ORM translation layer, no proxy classes, no lazy loading surprises
- Best performance for complex queries; no overhead of JPA persistence context
- `JdbcClient` (Spring 6.1) provides a fluent, readable API: `jdbcClient.sql("SELECT ...").query(ServiceMapper.class).list()`
- No entity mapping complexity — maps directly to DTOs, not to entities

**Trade-offs:**
- All SQL is hand-written — significant boilerplate for standard CRUD
- No automatic schema management (must use Flyway or Liquibase for DDL)
- Unit testing requires either an in-memory H2 database or a full mock of `JdbcTemplate`
- `RowMapper` implementations are verbose

**Pick when:** Query complexity and performance are paramount, or the team has a strong preference for SQL over ORM. Not necessary at this system's scale.

---

### Option 3: Spring Data JPA + Specifications API for dynamic queries
**Description:** Extend Option 1 with `JpaSpecificationExecutor<T>` on repositories. Dynamic query predicates are expressed as `Specification<T>` objects composed at runtime. Useful if graph queries need to be filtered dynamically (e.g., filter by dependency type, created date range).

**Advantages:**
- All benefits of Spring Data JPA plus composable, type-safe dynamic query predicates
- Avoids proliferating `@Query` methods for every query variant
- Cleanly separates query construction from repository interface

**Trade-offs:**
- `Specification` API is verbose for simple predicates
- Adds complexity that is not needed if the query requirements are static (which they are at initial scope)
- The Criteria API underlying Specifications is harder to read than JPQL

**Pick when:** The API needs to support rich filtering on list endpoints (e.g., filter services by tag, owner, or creation date). Useful to add later, not at initial implementation.

## Recommendation
**Option 1: Spring Data JPA with `JpaRepository` interfaces.** It covers all data access needs with minimal boilerplate. Use `@Query` with JPQL for the adjacency list queries. Annotate all `@OneToMany` / `@ManyToOne` associations as `FetchType.LAZY` and explicitly load them only when needed to avoid the N+1 problem.

## Consequences
**If accepted:** Define `ServiceRepository extends JpaRepository<ServiceEntity, UUID>` with a `findByName(String name)` method. Define `DependencyRepository extends JpaRepository<DependencyEntity, UUID>` with `findAllByFromService(ServiceEntity from)` and `findAllByToService(ServiceEntity to)` for direct adjacency list loading. For graph traversal, the service layer calls `findAllByFromService` or `findAllByToService` iteratively per BFS hop.

**Watch out for:** The BFS traversal (ADR-005) issues one repository call per hop level. For a graph with depth D and branching factor B, this is O(D) round trips. If this becomes a performance issue, replace the per-hop calls with a single `findAll()` that loads all edges into memory at traversal start, builds a local adjacency map, and traverses it in-memory. This trades memory for round trips and is valid at the expected data scale.
