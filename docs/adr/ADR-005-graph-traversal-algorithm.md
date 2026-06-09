# ADR-005: Graph Traversal Algorithm

## Status
Accepted — Option 1 (Cypher variable-length path) for traversal; Option 3 (path-collecting Cypher) for the UI visualization endpoint

## Context
A core feature of the system is returning the full dependency chain of a given service — both the set of services that transitively depend on it (upstream/ancestors) and the set of services it transitively depends on (downstream/descendants). With Neo4j chosen as the persistence layer (ADR-003), traversal can be expressed as a single Cypher query using variable-length path syntax, pushed entirely to the database — or it can remain in application-layer code using Spring Data Neo4j repositories. This ADR decides the implementation approach.

## Decision Drivers
- Correctness: every reachable node must be visited exactly once, regardless of graph shape or depth
- Cycle safety: cycles are explicitly allowed (ADR-006); the traversal must terminate correctly on cyclic graphs — `DISTINCT` on results and Neo4j's internal visited-node tracking during variable-length traversal are both required, not optional guards
- Neo4j is a native graph engine — traversal is its primary strength; pushing traversal to the database is idiomatic
- Results should be useful for blast-radius analysis: knowing the distance (hop count) from the origin is valuable
- The algorithm must be unit-testable in isolation from the database
- Performance: sub-second response for graphs up to 10,000 nodes

## Options Considered

### Option 1: Cypher variable-length path query (database-side traversal)
**Description:** Express the full transitive traversal as a single Cypher query using variable-length relationship syntax (`[:DEPENDS_ON*]`). Neo4j executes the traversal natively using index-free adjacency — no application-level looping required.

Downstream (what does service A transitively depend on?):
```cypher
MATCH (s:Service {name: $name})-[:DEPENDS_ON*1..]->(dep:Service)
RETURN DISTINCT dep
```

Upstream (what services transitively depend on service A?):
```cypher
MATCH (dep:Service)-[:DEPENDS_ON*1..]->(s:Service {name: $name})
RETURN DISTINCT dep
```

With hop depth included:
```cypher
MATCH path = (s:Service {name: $name})-[:DEPENDS_ON*1..]->(dep:Service)
RETURN dep, min(length(path)) AS depth
ORDER BY depth
```

**Advantages:**
- Single round-trip to the database — no N+1 queries regardless of graph depth or width
- Neo4j's index-free adjacency makes multi-hop traversal O(edges touched), not O(total graph size)
- `DISTINCT` is mandatory: with cycles allowed (ADR-006), the same node is reachable via an unbounded number of paths — without `DISTINCT`, the result set would contain duplicates for every distinct path that reaches a given node
- Neo4j's variable-length traversal has built-in cycle termination: it tracks visited relationships internally per path and will not traverse the same relationship twice within a single path, preventing infinite loops on cyclic graphs
- Hop depth is available via `length(path)` — directly useful for blast-radius tier reporting
- No application-side traversal code to write or test — the Cypher query is the algorithm

**Trade-offs:**
- The traversal logic lives in a Cypher string — harder to step through in a debugger than Java code
- Variable-length queries without an upper bound (`*1..`) can be slow on extremely dense or deep graphs; a practical upper-bound (`*1..20`) is a sensible guard
- Unit testing requires either a running Neo4j instance (via Testcontainers) or a mocked repository

**Pick when:** Neo4j is the persistence layer (as chosen in ADR-003). This is the idiomatic and recommended approach.

---

### Option 2: Application-side iterative BFS using Spring Data Neo4j repositories
**Description:** Load direct neighbours one hop at a time using SDN repository calls, maintaining a queue and visited set in the application layer — the same BFS pattern that would be used with a relational database.

```java
Queue<UUID> queue = new LinkedList<>();
Set<UUID> visited = new LinkedHashSet<>();
queue.add(startId);
while (!queue.isEmpty()) {
    UUID current = queue.poll();
    if (visited.contains(current)) continue;
    visited.add(current);
    serviceRepository.findDirectDependencies(current)
        .stream()
        .filter(dep -> !visited.contains(dep.getId()))
        .forEach(dep -> queue.add(dep.getId()));
}
```

**Advantages:**
- Pure Java — easy to step through in a debugger and unit-test by mocking the repository
- Cycle safety is explicit and visible in the code (visited set)
- Familiar to developers who have not worked with Cypher

**Trade-offs:**
- One repository call per hop level — O(depth) round-trips to Neo4j instead of one
- Wastes Neo4j's primary strength: native graph traversal is completely bypassed
- More code to write, own, and maintain than a Cypher query
- No depth/tier information without additional bookkeeping

**Pick when:** The team has no Cypher experience and needs to ship quickly; acceptable as a first implementation to be replaced once Cypher comfort grows.

---

### Option 3: Cypher with explicit depth limit and path collection for visualization
**Description:** Extend Option 1 by returning full path objects rather than just terminal nodes, and enforcing an explicit maximum depth. The full path is used to build an edge list for graph visualization in the UI (ADR-010).

```cypher
MATCH path = (s:Service {name: $name})-[:DEPENDS_ON*1..15]->(dep:Service)
WITH dep, path, length(path) AS depth
RETURN DISTINCT dep, depth,
       [r IN relationships(path) | {from: startNode(r).name, to: endNode(r).name}] AS edges
ORDER BY depth
```

**Advantages:**
- Returns both nodes and the edges between them in one query — the UI can render the full subgraph without additional requests
- Explicit depth limit (`*1..15`) prevents runaway queries on unexpectedly large graphs
- Depth information enables blast-radius tier rendering in the UI

**Trade-offs:**
- More complex Cypher — path manipulation (`relationships(path)`, `startNode`, `endNode`) requires Cypher familiarity
- Returns more data per query than necessary for a simple "list all affected services" use case
- Path deduplication is more demanding with cycles allowed: a cyclic graph produces an unbounded number of distinct paths to the same target node; the depth limit (`*1..15`) is a mandatory termination guard, not a performance hint — without it, the query cannot guarantee termination on a graph with cycles

**Pick when:** The UI requires a full edge list to render the dependency subgraph (not just a flat node list), and the team is comfortable with Cypher path queries. A strong candidate for the visualization endpoint specifically.

## Recommendation
**Option 1: Cypher variable-length path query** as the primary traversal implementation, with **Option 3** used for the graph visualization endpoint that feeds the UI. Pushing traversal to Neo4j is idiomatic, eliminates N+1 round-trips, and produces accurate hop-depth information useful for blast-radius tiering — all with less code than the application-side BFS alternative.

## Consequences
**If accepted:** Define two `@Query`-annotated methods on `ServiceRepository`:
- `findAllDownstream(String name)` — variable-length OUTGOING traversal returning `List<ServiceNode>` with depth
- `findAllUpstream(String name)` — variable-length INCOMING traversal
- `findSubgraphEdges(String name)` — path-collecting variant for the visualization endpoint

Add a depth upper bound of `*1..50` to all variable-length path queries. With cycles allowed (ADR-006), this bound is a correctness requirement — it guarantees query termination — not merely a performance hint. Expose it as a configurable property (`tracker.traversal.max-depth=50`) so it can be tuned without a code change.

**Watch out for:** `@Query` methods on SDN repositories that return custom projections (depth + node) require a `@QueryResult` DTO or a custom `Map<String, Object>` return type — SDN cannot automatically map Cypher `RETURN dep, depth` to a standard `@Node` class. Define a `ServiceWithDepthDTO` record for this purpose.
