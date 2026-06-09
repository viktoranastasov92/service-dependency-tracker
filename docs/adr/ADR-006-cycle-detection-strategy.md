# ADR-006: Cycle Detection Strategy

## Status
Proposed

## Context
A directed dependency graph can contain cycles (e.g., service A depends on B, B depends on C, C depends on A). The requirements ask the system to "handle potential cycles." The system must decide whether to prevent cycles at write time, detect and report them at read time, or a combination. Cycles can cause infinite traversal loops if not handled, making the traversal algorithm's visited-set guard (ADR-005) necessary regardless of this decision.

## Decision Drivers
- Cycles in a dependency graph may represent real misconfiguration that on-call engineers need to be alerted to
- Preventing cycles at write time (pre-check before insert) gives users immediate, actionable feedback
- Allowing cycles but reporting them at read time is more permissive but may mask problems
- The cycle check algorithm must run in the same transaction as the edge insert to be race-condition safe
- The solution must be testable in isolation

## Options Considered

### Option 1: Pre-insert cycle check (DFS-based, prevent cycles at write time)
**Description:** Before persisting a new dependency edge `(A → B)`, run a directed DFS from `B` to check if `A` is reachable from `B`. If it is, the proposed edge would create a cycle — reject it with a `409 Conflict` HTTP response and a descriptive error message. If it is not, persist the edge.

```
// Before saving edge A → B:
// Check: is A reachable from B?
if (isReachable(B, A)) {
    throw new CycleDetectedException("Adding A→B would create a cycle");
}
```

**Advantages:**
- The graph remains acyclic at all times — traversal code does not need cycle guards at query time
- Immediate user feedback: the error message can include the cycle path for debugging
- The invariant is enforced at the domain layer, not scattered across traversal code
- Simple to reason about: the graph is a DAG (Directed Acyclic Graph) by construction

**Trade-offs:**
- The reachability check adds latency to every edge-insertion request (proportional to graph depth)
- If cycle detection is run outside a database transaction, a concurrent insert could violate the invariant — must run inside the same transaction or use a distributed lock
- Some real-world dependency graphs do have cycles (e.g., mutual runtime dependencies); preventing them blocks valid data entry

**Pick when:** The business rule is that cycles represent invalid data and must be prevented. This is the recommended choice.

---

### Option 2: Allow cycles; detect and annotate at read time
**Description:** Accept any edge insert without validation. When a traversal query is executed, use the visited-set BFS (ADR-005) which naturally terminates when it encounters already-visited nodes. Optionally, run a separate cycle-detection pass (Tarjan's or Kahn's algorithm) and include a `hasCycle: true` flag and the cycle members in the traversal response.

**Advantages:**
- Writes are always fast — no graph traversal on insert
- Supports data imports where cycles exist in legacy records
- Engineers can see cycles in query results and decide whether they are real or accidental

**Trade-offs:**
- The graph is allowed to be in an inconsistent state indefinitely
- Traversal results can be confusing: the same service may appear in both upstream and downstream sets
- Every consumer of traversal results must be written to handle cyclic graphs
- More complex traversal response schema (cycle annotations)

**Pick when:** Data is ingested from external systems where cycles cannot be prevented, and the system's role is to report the state of the graph rather than enforce its correctness.

---

### Option 3: Hybrid — prevent cycles on direct API writes, flag on bulk import
**Description:** The normal REST API (POST a dependency) enforces no-cycle pre-check (Option 1). A separate bulk-import endpoint accepts a set of edges without validation, and after import runs a background cycle-detection job (Tarjan's SCC algorithm) that flags cyclic services in the database. Flagged services surface a warning in traversal responses.

**Advantages:**
- Covers both normal operation (clean graph) and migration/import scenarios (legacy data)
- Tarjan's SCC algorithm is O(V + E) and can run as a scheduled task
- Users get clean immediate validation on normal writes

**Trade-offs:**
- Highest implementation complexity of the three options
- The window between bulk import and cycle detection leaves the graph in an unknown state
- Two code paths for dependency creation must be maintained and tested separately

**Pick when:** The system must support both strict real-time validation for normal use and permissive batch loading from legacy systems.

## Recommendation
**Option 1: Pre-insert cycle check.** For a new system with no legacy data to import, preventing cycles at write time is the simplest strategy that keeps the graph in a well-defined DAG state. The performance cost is negligible at the expected data scale (hundreds of services). The traversal code (ADR-005) still maintains its visited-set guard as a defense-in-depth measure.

## Consequences
**If accepted:** Add a `CycleDetectionService` that performs a DFS/BFS reachability check from the target node back to the source node before every edge insert. This check runs within the same `@Transactional` method as the edge insert to prevent TOCTOU race conditions. Return `HTTP 409 Conflict` with a body explaining which existing path would be completed by the proposed edge. Unit tests should cover: no cycle (insert succeeds), direct cycle (A→B, B→A), and transitive cycle (A→B, B→C, C→A).

**Watch out for:** The cycle check itself is a graph traversal — if the graph is very large, it adds latency to edge inserts. If this becomes a bottleneck, add a circuit-breaker that limits traversal depth and rejects edges that would require a full traversal of more than N nodes to verify.
