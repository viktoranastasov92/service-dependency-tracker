# ADR-015: Data Access Pattern

## Status
Accepted — Option 1: Spring Data Neo4j `Neo4jRepository` with `@Query` Cypher annotations

## Context
With Neo4j chosen as the persistence layer (ADR-003) and the property graph model defined (ADR-004), the data access pattern determines how the application reads and writes nodes and relationships. Spring Data Neo4j (SDN) offers several levels of abstraction — from fully automated repository interfaces to low-level driver sessions. This ADR decides the right balance between abstraction and control for this system's access patterns.

## Decision Drivers
- Standard CRUD operations (register a service, add/remove a dependency) must not require boilerplate
- Graph traversal queries (downstream, upstream, cycle detection) are expressed in Cypher — the pattern must support custom `@Query` annotations cleanly
- The repository interface must be mockable for unit tests (ADR-009 Tier 1) without a running Neo4j
- The pattern must not hide Cypher from the developer — Cypher is the primary tool for graph queries and should be visible and testable
- SDN's default eager-loading behaviour must be controllable — unbounded subgraph loading is a real risk

## Options Considered

### Option 1: Spring Data Neo4j `Neo4jRepository` with `@Query` Cypher annotations
**Description:** Define repository interfaces that extend `Neo4jRepository<N, ID>`. SDN generates CRUD implementations at startup. All non-trivial graph queries are expressed as `@Query`-annotated methods with explicit Cypher strings. This is the idiomatic SDN pattern.

```java
public interface ServiceRepository extends Neo4jRepository<ServiceNode, UUID> {

    Optional<ServiceNode> findByName(String name);

    // Downstream traversal (ADR-005)
    @Query("""
        MATCH (s:Service {name: $name})-[:DEPENDS_ON*1..$maxDepth]->(dep:Service)
        RETURN DISTINCT dep, min(length(shortestPath((s)-[:DEPENDS_ON*]->(dep)))) AS depth
        ORDER BY depth
        """)
    List<ServiceWithDepthDTO> findAllDownstream(String name, int maxDepth);

    // Upstream traversal (ADR-005)
    @Query("""
        MATCH (dep:Service)-[:DEPENDS_ON*1..$maxDepth]->(s:Service {name: $name})
        RETURN DISTINCT dep, min(length(shortestPath((dep)-[:DEPENDS_ON*]->(s)))) AS depth
        ORDER BY depth
        """)
    List<ServiceWithDepthDTO> findAllUpstream(String name, int maxDepth);

    // Cycle detection (ADR-006)
    @Query("""
        MATCH (b:Service {name: $targetName})-[:DEPENDS_ON*0..]->(a:Service {name: $sourceName})
        RETURN count(a) > 0 AS wouldCreateCycle
        """)
    boolean wouldCreateCycle(String sourceName, String targetName);

    // Cycle path for error response
    @Query("""
        MATCH path = (b:Service {name: $targetName})-[:DEPENDS_ON*1..]->(a:Service {name: $sourceName})
        RETURN [node IN nodes(path) | node.name] AS cyclePath
        LIMIT 1
        """)
    List<String> findCyclePath(String sourceName, String targetName);

    // Direct neighbours only (for UI immediate dependency view)
    @Query("""
        MATCH (s:Service {name: $name})-[r:DEPENDS_ON]->(dep:Service)
        RETURN dep, r
        """)
    List<ServiceNode> findDirectDependencies(String name);
}
```

**Advantages:**
- CRUD operations (save, findById, findByName, delete) require zero boilerplate
- `@Query` methods make every Cypher string explicit — they are readable, reviewable, and testable in `@DataNeo4jTest`
- Repository interfaces are mockable with Mockito in unit tests — no Neo4j instance required for service-layer tests
- Derived method names (`findByName`) are generated and validated at startup
- Spring Data's `@Transactional` integration ensures consistent reads and writes

**Trade-offs:**
- SDN's default `findById` / `findAll` load the full subgraph up to depth 1 — callers must use `@Query` projections when only specific fields are needed
- Cypher lives in Java string literals — IDE support for syntax highlighting and validation requires a plugin
- `@QueryResult` or projection interfaces required for `@Query` methods that return non-node types (e.g., `ServiceWithDepthDTO`)

**Pick when:** The domain is graph-shaped and most queries benefit from explicit Cypher control. This is the standard SDN pattern and is recommended for this system.

---

### Option 2: Low-level Neo4j Java Driver (`Driver` / `Session`) with hand-written Cypher
**Description:** Bypass Spring Data Neo4j entirely. Inject the Neo4j `Driver` bean directly into service classes. Execute Cypher via `session.run(query, parameters)` and map `Record` results manually to domain objects.

```java
@Service
public class ServiceQueryService {
    private final Driver driver;

    public List<ServiceDTO> findDownstream(String name) {
        try (var session = driver.session()) {
            return session.run(
                "MATCH (s:Service {name: $name})-[:DEPENDS_ON*1..50]->(dep:Service) " +
                "RETURN DISTINCT dep.name AS name, dep.description AS description",
                Values.parameters("name", name)
            ).list(r -> new ServiceDTO(r.get("name").asString(), r.get("description").asString()));
        }
    }
}
```

**Advantages:**
- Complete control over every Cypher query — no SDN abstraction layer
- No risk of accidental eager subgraph loading (SDN's most common footgun)
- Results mapped directly to DTOs — no entity proxy objects in the service layer
- Easier to reason about what queries are executed for any given operation

**Trade-offs:**
- Significant boilerplate: session lifecycle management, parameter binding, and result mapping are all manual
- No repository interface to mock in unit tests — service classes must be tested with a real Neo4j instance or a stub `Driver`
- Loses all Spring Data features: no derived queries, no `@Transactional` integration, no pagination support
- Higher maintenance burden — every query change requires touching service class code rather than a single annotated repository method

**Pick when:** SDN's abstraction layer causes specific, measured problems (e.g., unbounded loading in a high-traffic path) and the team needs direct driver control for those specific queries. Best used selectively alongside Option 1, not as a full replacement.

---

### Option 3: Spring Data Neo4j + `Neo4jTemplate` for complex operations
**Description:** Use `Neo4jRepository` for standard CRUD (Option 1), and inject `Neo4jTemplate` for complex operations that require more control than `@Query` annotations can provide — for example, bulk operations, dynamic query construction, or conditional Cypher execution.

```java
@Service
public class BulkImportService {
    private final Neo4jTemplate neo4jTemplate;

    public void importServices(List<ServiceNode> nodes) {
        nodes.forEach(neo4jTemplate::save);
    }

    public List<ServiceNode> findByDynamicCriteria(Map<String, Object> criteria) {
        String cypher = buildCypher(criteria);
        return neo4jTemplate.findAll(cypher, criteria, ServiceNode.class);
    }
}
```

**Advantages:**
- Combines the ergonomics of `Neo4jRepository` for standard queries with direct template access for edge cases
- `Neo4jTemplate` provides typed result mapping while still accepting raw Cypher
- Useful for the bulk-import scenario described in ADR-006 Option 3 (if adopted)
- Avoids dropping to the raw `Driver` for most custom operations

**Trade-offs:**
- Two access patterns in the same codebase — developers must know when to use the repository and when to use the template
- `Neo4jTemplate` is less documented than `Neo4jRepository` — fewer examples available
- Dynamic query construction via string concatenation risks Cypher injection if parameters are not bound correctly (always use parameterised queries)

**Pick when:** The majority of queries fit the `@Query` repository pattern, but a small number of operations require dynamic or bulk Cypher execution that is awkward to express as static annotations.

## Recommendation
**Option 1: Spring Data Neo4j `Neo4jRepository` with `@Query` Cypher annotations.** It covers all access patterns for this system with minimal boilerplate. The Cypher strings in `@Query` annotations are explicit, reviewable, and exercised directly by `@DataNeo4jTest` integration tests (ADR-009). Mocking the repository interface in unit tests keeps service-layer tests fast and isolated. If specific high-traffic queries later exhibit SDN loading problems, migrate those methods to Option 2 selectively — there is no need to choose upfront.

## Consequences
**If accepted:** Define `ServiceRepository extends Neo4jRepository<ServiceNode, UUID>` with: `findByName`, `findAllDownstream`, `findAllUpstream`, `wouldCreateCycle`, `findCyclePath`, and `findDirectDependencies` — all as `@Query` methods except `findByName`. Define `ServiceWithDepthDTO` as a `@QueryResult` record or projection interface for traversal result mapping. All repository methods are exercised by `@DataNeo4jTest` slice tests backed by Testcontainers Neo4j (ADR-009).

**Watch out for:** SDN's default `findById` loads the node plus all immediately related nodes to depth 1. For a well-connected service node, this means loading all direct dependencies and their metadata eagerly. Always prefer named `@Query` projections that return only the fields actually needed for a given operation. Never call `findAll()` without a filter or depth projection — on a graph with hundreds of services, this loads the entire database into memory.
