# ADR-006: Cycle Detection Strategy

## Status
Accepted — Option 2: Allow cycles; detect and report at read time

## Context
A directed dependency graph can contain cycles (e.g., service A depends on B, B depends on C, C depends on A). The requirements ask the system to "handle potential cycles." With Neo4j chosen as the persistence layer (ADR-003), cycle detection can be expressed as a Cypher reachability query — a single database call — rather than an application-level graph traversal. This ADR decides whether cycles are prevented at write time, detected at read time, or both.

## Decision Drivers
- Cycles in a service dependency graph represent a real misconfiguration that on-call engineers must know about
- Preventing cycles at write time gives users immediate, actionable feedback and keeps the graph in a known-good state
- The cycle check must be atomic with the edge insert to prevent TOCTOU race conditions
- With Neo4j, the reachability check is a single Cypher query — significantly simpler than the application-level DFS required with a relational database
- The traversal algorithm (ADR-005) relies on Neo4j's native cycle avoidance in variable-length paths — but a defense-in-depth visited set remains valuable

## Options Considered

### Option 1: Pre-insert Cypher reachability check (prevent cycles at write time)
**Description:** Before persisting a new dependency edge `A → B`, execute a Cypher query that checks whether `A` is already reachable from `B` via any existing path. If yes, the proposed edge would close a cycle — reject it with `HTTP 409 Conflict` and a descriptive message. If no, proceed with the insert. Both the check and the insert run within the same Neo4j transaction.

```cypher
// Would adding A → B create a cycle?
// Check: is A reachable from B through existing DEPENDS_ON edges?
MATCH (b:Service {name: $targetName})-[:DEPENDS_ON*0..]->(a:Service {name: $sourceName})
RETURN count(a) > 0 AS wouldCreateCycle
```

If `wouldCreateCycle` is `true`, throw `CycleDetectedException` and return `409 Conflict`.

To include the cycle path in the error response:
```cypher
MATCH path = (b:Service {name: $targetName})-[:DEPENDS_ON*1..]->(a:Service {name: $sourceName})
RETURN [node IN nodes(path) | node.name] AS cyclePath
LIMIT 1
```

**Advantages:**
- The graph is guaranteed to be a DAG (Directed Acyclic Graph) at all times — traversal code never encounters a cycle
- Immediate feedback to the caller: the `409` response can include the exact path that would be closed (`"B → C → A → B"`)
- The check is a single Cypher query — far simpler than an application-level DFS reachability check
- Running inside the same transaction as the insert eliminates the TOCTOU window (Neo4j's write lock on the transaction prevents a concurrent insert from racing through)
- The cycle check is easy to unit-test: mock the repository to return `true` or `false` and verify the service layer behavior

**Trade-offs:**
- The reachability query adds latency to every edge-insertion request (Neo4j traverses the graph to check reachability)
- On very large or deeply connected graphs, an unbounded `[:DEPENDS_ON*0..]` may be slow — a depth cap (e.g., `*0..50`) matching ADR-005's `max-depth` provides a practical bound
- Some real-world dependency graphs legitimately have cycles (mutual runtime dependencies); if GlobalCorp needs to represent these, prevention is the wrong policy — but the requirements treat this as invalid data

**Pick when:** The business rule is that cycles are invalid and must be prevented. This is the recommended approach for this system.

---

### Option 2: Allow cycles; detect and report at read time
**Description:** Accept any edge insert without validation. When a traversal or query is executed, run a separate Cypher cycle-detection query and include cycle information in the response.

```cypher
// Detect all cycles reachable from a given service
MATCH path = (s:Service {name: $name})-[:DEPENDS_ON*]->(s)
RETURN [node IN nodes(path) | node.name] AS cycle
LIMIT 10
```

Include a `cycles` array in the traversal response DTO when cycles are detected.

**Advantages:**
- Writes are always fast — no graph traversal on insert
- Supports importing legacy data where cycles exist and cannot be cleaned up immediately
- Engineers can see cycles in query results and decide whether they are real or accidental

**Trade-offs:**
- The graph is allowed to be in an inconsistent/potentially-invalid state indefinitely
- Every consumer of traversal results must handle cyclic graphs — complexity leaks into every downstream component
- Cycle-detection queries (`MATCH (s)-[:DEPENDS_ON*]->(s)`) without bounds are expensive and may not terminate on complex graphs
- ADR-005's Cypher variable-length traversal relies on Neo4j's internal visited-node tracking; with cycles, the `DISTINCT` on results is still safe, but reasoning about correctness becomes harder

**Pick when:** Data is ingested from external systems where cycles cannot be prevented, or the system's role is purely observational rather than authoritative.

---

### Option 3: Hybrid — prevent cycles via API, flag on bulk import
**Description:** The normal REST API (POST a dependency) enforces the pre-insert Cypher check (Option 1). A separate bulk-import endpoint accepts a batch of edges without the per-edge check, then runs a Cypher all-cycles scan as a post-import validation step and flags any cyclic nodes in Neo4j with a `hasCycle: true` property.

```cypher
// Post-import: find and flag all nodes involved in cycles
MATCH (s:Service)-[:DEPENDS_ON*]->(s)
SET s.hasCycle = true
RETURN s.name
```

Normal traversal responses include `hasCycle` in the service DTO when the property is set.

**Advantages:**
- Covers both normal operation (clean graph) and migration/import scenarios
- Tarjan's SCC equivalent is expressible in Cypher — no application-level algorithm needed
- Users get immediate validation feedback on normal writes and deferred cycle warnings on imports

**Trade-offs:**
- Two code paths for dependency creation must be maintained and tested
- The window between bulk import and post-import cycle scan leaves the graph in an unknown state
- Adds schema complexity: `hasCycle` property on `ServiceNode` that is only populated conditionally
- Overkill for a greenfield system with no legacy data to import

**Pick when:** The system must onboard legacy service registry data that may contain cycles, alongside strict enforcement for new entries.

## Recommendation
**Option 1: Pre-insert Cypher reachability check.** For a greenfield system, preventing cycles at write time keeps the graph in a well-defined DAG state with zero implementation complexity beyond a single `@Query` method. The check is a natural fit for Neo4j — one Cypher query replaces what would have been an application-level BFS in a relational setup. The `409` response with the cycle path gives engineers exactly the information they need to correct their input.

## Consequences
**If accepted:** Add a `boolean wouldCreateCycle(String sourceName, String targetName)` method to `ServiceRepository` backed by the Cypher reachability query. Add a `findCyclePath(String sourceName, String targetName)` method that returns the path as a `List<String>` for the error message. The service layer calls `wouldCreateCycle` before every `addDependency` operation, within the same `@Transactional` method. Throw a domain exception `CycleDetectedException(List<String> path)` that the `@RestControllerAdvice` maps to `HTTP 409 Conflict` with a body of `{ "error": "CYCLE_DETECTED", "path": ["A", "C", "B", "A"] }`.

**Watch out for:** The `[:DEPENDS_ON*0..]` unbounded pattern in the reachability query will follow all paths. Add an explicit upper bound matching `tracker.traversal.max-depth` (ADR-005) to prevent the cycle check itself from becoming a runaway query on a large graph.
