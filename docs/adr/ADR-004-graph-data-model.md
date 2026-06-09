# ADR-004: Graph Data Model

## Status
Proposed

## Context
The core domain is a directed graph where nodes are services and edges represent "depends on" relationships. The data model must support registering services, adding/removing directed dependency edges, and traversing the graph in both directions (upstream: who depends on me; downstream: what do I depend on). The model must be stored in a relational database (per ADR-003).

## Decision Drivers
- The graph is directed: edge direction encodes the dependency direction and must be preserved
- Traversal queries must efficiently find all ancestors (upstream) and all descendants (downstream) of a node
- The model must be expressible in a standard relational schema compatible with JPA/Hibernate
- Cycle detection must be possible using this model (see ADR-006)
- The schema should be simple enough that a developer can understand it in under five minutes
- Node and edge metadata (e.g., description, dependency type) should be extensible without schema redesign

## Options Considered

### Option 1: Adjacency List — two tables (`services` + `dependencies`)
**Description:** Two relational tables. `services` stores one row per service (id, name, description, timestamps). `dependencies` stores one row per directed edge (`from_service_id`, `to_service_id`, optional metadata), where the semantic is "`from` depends on `to`". A unique constraint on `(from_service_id, to_service_id)` prevents duplicate edges.

```
services         dependencies
-----------      ----------------------------------
id (PK)          id (PK)
name (UNIQUE)    from_service_id (FK → services.id)
description      to_service_id   (FK → services.id)
created_at       created_at
```

**Advantages:**
- Extremely simple schema — two tables, easy to understand and query
- Standard SQL: INSERT, DELETE, and SELECT on edges are single-row operations
- Full JPA mapping: `Service` entity with `@ManyToMany` or a dedicated `Dependency` entity with two `@ManyToOne` references
- Upstream query: `SELECT * FROM dependencies WHERE to_service_id = ?`; downstream query: `SELECT * FROM dependencies WHERE from_service_id = ?`
- Adding edge metadata (dependency type, weight) is a simple column addition

**Trade-offs:**
- Multi-hop traversal (full transitive closure) requires recursive queries (CTE `WITH RECURSIVE`) or application-level BFS/DFS — the SQL `WITH RECURSIVE` is supported by H2 and PostgreSQL
- No native cycle detection at the database level; must be enforced in application logic

**Pick when:** The database is relational (as chosen in ADR-003), the graph is sparse to moderately dense, and simplicity is valued. This is the correct choice for this system.

---

### Option 2: Closure Table
**Description:** Three tables: `services`, `direct_dependencies` (immediate edges only), and `dependency_closure` (all ancestor/descendant pairs at every depth, pre-computed). The closure table is updated on every edge insert/delete.

```
dependency_closure
----------------------------------------------
ancestor_id   (FK → services.id)
descendant_id (FK → services.id)
depth         (integer, 0 = self-reference)
```

**Advantages:**
- Full transitive closure is a single `SELECT` — no recursion needed at query time
- Upstream and downstream queries are O(1) SQL lookups
- Excellent read performance for deep graphs

**Trade-offs:**
- Write complexity: inserting or deleting a single edge requires updating O(n) rows in the closure table
- Schema complexity is significantly higher — three tables with non-obvious invariants to maintain
- Cycle prevention must still be enforced before inserting (otherwise the closure update logic loops)
- Overkill for the expected graph size (hundreds of services)

**Pick when:** The graph has thousands of nodes, traversal queries dominate the workload, and write operations are rare.

---

### Option 3: Native graph database (Neo4j)
**Description:** Replace the relational database with Neo4j, a native property graph database. Nodes are `Service` nodes; edges are `DEPENDS_ON` relationships. Traversal is expressed in Cypher (`MATCH (a)-[:DEPENDS_ON*]->(b)`).

**Advantages:**
- Traversal queries are idiomatic and concise in Cypher
- Native cycle detection and path queries are built into the query language
- Spring Data Neo4j provides a JPA-like mapping layer

**Trade-offs:**
- Requires running a separate Neo4j server (Docker or native install) — significantly more infrastructure than H2
- The team must learn Cypher in addition to Java
- Spring Data Neo4j is less mature and less documented than Spring Data JPA
- Replaces the H2/PostgreSQL migration path with a completely different database technology
- Overkill for hundreds of services where a simple adjacency list works perfectly well

**Pick when:** The graph has millions of nodes and edges, traversal depth exceeds 10 hops regularly, and the team has Neo4j expertise.

## Recommendation
**Option 1: Adjacency List with two tables.** It maps cleanly to JPA entities, is trivially understandable, and handles the expected data volume with ease. Multi-hop traversal is implemented in application-layer BFS/DFS (see ADR-005), which is the correct separation of concerns — the database stores data, the application traverses it.

## Consequences
**If accepted:** Create `ServiceEntity` (id, name, description, createdAt) and `DependencyEntity` (id, fromService, toService, createdAt) JPA entities. Add a unique constraint on `(from_service_id, to_service_id)`. The `DependencyEntity` uses two `@ManyToOne(fetch = LAZY)` associations to `ServiceEntity`. Repository interfaces extend `JpaRepository`.

**Watch out for:** The self-referential foreign keys (`from_service_id` and `to_service_id` both point to `services.id`) require that the referenced service exists before an edge can be inserted — enforce this at the service layer to return a meaningful error rather than a database constraint violation.
