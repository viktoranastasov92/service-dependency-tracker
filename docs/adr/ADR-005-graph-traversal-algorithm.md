# ADR-005: Graph Traversal Algorithm

## Status
Proposed

## Context
A core feature of the system is returning the full dependency chain of a given service — both the set of services that transitively depend on it (upstream/ancestors) and the set of services it transitively depends on (downstream/descendants). The traversal must work correctly even in the presence of graph cycles (which are detected separately per ADR-006 but may exist in data loaded before cycle detection was enforced).

## Decision Drivers
- Correctness: the algorithm must visit every reachable node exactly once, even if cycles exist
- The graph fits in JVM memory at the expected scale (hundreds to low thousands of nodes)
- The algorithm must run inside the Spring service layer without requiring a special database query
- The result set should clearly distinguish direct dependencies from transitive ones if needed
- The algorithm must be unit-testable in isolation from the database
- Performance: sub-second response for graphs of up to 10,000 nodes is the target

## Options Considered

### Option 1: Iterative BFS (Breadth-First Search) with visited set
**Description:** Starting from the given service node, maintain a queue (FIFO) of nodes to visit and a `Set<String>` of already-visited node IDs. For each node dequeued, load its direct neighbors from the repository, add unvisited neighbors to the queue and the visited set, and continue until the queue is empty. The visited set is the result.

```
Queue<String> queue = new LinkedList<>();
Set<String> visited = new LinkedHashSet<>();
queue.add(startId);
while (!queue.isEmpty()) {
    String current = queue.poll();
    if (visited.contains(current)) continue;
    visited.add(current);
    for (String neighbor : getDirectNeighbors(current)) {
        if (!visited.contains(neighbor)) queue.add(neighbor);
    }
}
```

**Advantages:**
- Naturally produces results in order of distance from the origin — useful for showing "blast radius tiers"
- The visited set guarantees each node is processed once, making it safe against cycles
- Iterative implementation avoids JVM stack overflow on deep graphs (unlike recursive DFS)
- Easy to understand, test, and explain to stakeholders
- Can be adapted to return depth/level information alongside each node

**Trade-offs:**
- Queue and visited set consume O(n) memory — acceptable at this scale
- Slightly more complex to implement than recursive DFS, but the iterative form is worth the safety

**Pick when:** Level-order (tier) information is useful, or when maximum clarity and cycle safety are priorities. Recommended for this system.

---

### Option 2: Recursive DFS (Depth-First Search) with visited set
**Description:** Starting from the given node, recursively visit each unvisited neighbor depth-first. The visited set prevents re-visiting nodes and breaks cycles.

```
void dfs(String nodeId, Set<String> visited) {
    if (visited.contains(nodeId)) return;
    visited.add(nodeId);
    for (String neighbor : getDirectNeighbors(nodeId)) {
        dfs(neighbor, visited);
    }
}
```

**Advantages:**
- Very concise code — the recursive form is intuitive
- Produces a DFS ordering which can be useful for topological sort use cases
- Same cycle safety as BFS when a visited set is maintained

**Trade-offs:**
- Recursive calls consume JVM stack space; a graph with depth > ~500 edges will cause `StackOverflowError`
- Harder to extract level/distance information compared to BFS
- Iterative DFS (using an explicit stack) is safer but less natural to read

**Pick when:** Graph depth is provably shallow (< 100 hops), or a topological ordering of results is needed.

---

### Option 3: Database-side recursive CTE (`WITH RECURSIVE`)
**Description:** Express the full transitive closure query directly in SQL using a recursive Common Table Expression. Both H2 and PostgreSQL support `WITH RECURSIVE`. The query starts from the given node and recursively joins the `dependencies` table until no new rows are found.

```sql
WITH RECURSIVE downstream AS (
  SELECT to_service_id AS id FROM dependencies WHERE from_service_id = :startId
  UNION
  SELECT d.to_service_id FROM dependencies d
  JOIN downstream ds ON d.from_service_id = ds.id
)
SELECT s.* FROM services s JOIN downstream ds ON s.id = ds.id;
```

**Advantages:**
- Single database round-trip returns all transitive results — no N+1 query problem
- The database query planner can optimize the recursive join
- H2 and PostgreSQL both support this syntax, so it works in both development and production

**Trade-offs:**
- H2's `WITH RECURSIVE` behavior for cycles may differ from PostgreSQL (H2 may loop without a cycle guard depending on version)
- Cycle handling requires adding `WHERE NOT EXISTS` guards or depth limits, which significantly increases query complexity
- SQL is harder to unit test than Java logic
- Couples the traversal logic to the SQL dialect, complicating future database migration

**Pick when:** The database is PostgreSQL in production, graph sizes are large enough that multiple round-trips are a measurable bottleneck, and the team is comfortable writing and testing recursive SQL.

## Recommendation
**Option 1: Iterative BFS with a visited set.** It is safe against cycles, does not risk stack overflow, returns results in a meaningful level-order, and is trivially unit-testable as pure Java. At the expected scale (hundreds to thousands of services), loading all direct neighbors per hop involves at most a few hundred small queries — well within sub-second performance.

## Consequences
**If accepted:** Implement a `GraphTraversalService` that accepts a start node ID and a direction enum (`UPSTREAM` / `DOWNSTREAM`), and returns an ordered `List<ServiceDTO>` or a `Set<String>` of reachable service IDs. The service is injected with the `DependencyRepository` and has no direct HTTP coupling. The traversal logic is fully unit-testable by mocking the repository.

**Watch out for:** The BFS will issue one `SELECT` per hop level if neighbors are loaded lazily. For large graphs, consider loading all edges into memory at traversal start (single query) and building an in-memory adjacency map for the traversal, then discarding it after the request completes. This trades memory for query count.
