# ADR-004: Graph Data Model

## Status
Accepted — Option 2: Rich `@RelationshipProperties` with `DependsOnRelationship`

## Context
The core domain is a directed graph where nodes are services and edges represent "depends on" relationships. With Neo4j chosen as the persistence layer (ADR-003), the data model is expressed as a property graph — nodes with labels and directed relationships with types — rather than as relational tables. This ADR decides how to map the domain onto Neo4j's node/relationship model using Spring Data Neo4j (SDN) annotations.

## Decision Drivers
- The graph is directed: relationship direction encodes the dependency direction and must be preserved
- Traversal must efficiently find all ancestors (upstream) and all descendants (downstream) of a node using Cypher
- The model must be expressible as SDN `@Node` and `@Relationship` / `@RelationshipProperties` annotations
- Cycle detection (ADR-006) must be performable as a Cypher reachability query against this model
- Node and edge metadata (e.g., description, dependency type, timestamps) must be extensible without breaking existing queries
- The model should be intuitive: a developer unfamiliar with Neo4j should understand it in under five minutes

## Options Considered

### Option 1: Simple `@Relationship` — service nodes with direct references
**Description:** A single `ServiceNode` class annotated with `@Node("Service")`. Direct dependencies are represented as a `Set<ServiceNode>` field annotated with `@Relationship(type = "DEPENDS_ON", direction = OUTGOING)`. Spring Data Neo4j manages the relationship transparently.

```java
@Node("Service")
public class ServiceNode {
    @Id @GeneratedValue
    private UUID id;

    @Property("name")
    private String name;

    @Property("description")
    private String description;

    @Relationship(type = "DEPENDS_ON", direction = OUTGOING)
    private Set<ServiceNode> dependsOn = new HashSet<>();
}
```

Cypher shape: `(:Service {name: "A"})-[:DEPENDS_ON]->(:Service {name: "B"})`

**Advantages:**
- Minimal code — no separate relationship class required
- SDN handles CRUD for both nodes and relationships through the single entity
- Cypher traversal queries are identical regardless of relationship metadata presence
- Easiest model to understand at a glance

**Trade-offs:**
- No metadata on the relationship itself (e.g., cannot record when the dependency was registered, or what type it is)
- Removing a specific dependency edge requires loading the `dependsOn` set, removing the element, and saving — no fine-grained edge deletion by ID
- Adding relationship metadata later requires migrating to Option 2 (breaking change to the entity model)

**Pick when:** Edge metadata is not needed and will never be needed; simplicity is the top priority.

---

### Option 2: Rich `@RelationshipProperties` — typed relationship entity
**Description:** Define a separate `DependsOnRelationship` class annotated with `@RelationshipProperties`. This class holds the relationship's metadata (type, timestamps). The `ServiceNode` holds a `List<DependsOnRelationship>` instead of a `Set<ServiceNode>`.

```java
@RelationshipProperties
public class DependsOnRelationship {
    @RelationshipId
    private Long id;

    @TargetNode
    private ServiceNode target;

    private String dependencyType;   // e.g. "RUNTIME", "BUILD", "OPTIONAL"
    private Instant createdAt;
}

@Node("Service")
public class ServiceNode {
    @Id @GeneratedValue
    private UUID id;
    private String name;
    private String description;

    @Relationship(type = "DEPENDS_ON", direction = OUTGOING)
    private List<DependsOnRelationship> dependsOn = new ArrayList<>();
}
```

Cypher shape: `(:Service)-[:DEPENDS_ON {dependencyType: "RUNTIME", createdAt: ...}]->(:Service)`

**Advantages:**
- Relationship has its own identity (`@RelationshipId`) — individual edges can be deleted by ID without loading the full set
- Relationship metadata (type, timestamps, weight) can be added without changing the graph structure or existing Cypher queries
- Extensible: new properties on the relationship require only a field addition, not a schema migration
- Dependency type (RUNTIME, BUILD, OPTIONAL) is a natural and useful attribute for blast-radius analysis

**Trade-offs:**
- Slightly more code than Option 1 — an additional class is required
- SDN relationship property mapping has occasional quirks (e.g., relationship ID handling differs between Neo4j versions)
- `List<DependsOnRelationship>` must be navigated to reach target nodes in Java code

**Pick when:** Edge metadata is needed now or is likely to be needed (e.g., dependency type, creation timestamp, optional/required flag). Recommended for this system.

---

### Option 3: Bidirectional explicit modelling — dual relationship types
**Description:** Model both directions as explicit relationship types: `DEPENDS_ON` (A→B means A depends on B) and `DEPENDED_ON_BY` (B→A, the inverse). Both are maintained in sync on every write.

```java
@Relationship(type = "DEPENDS_ON", direction = OUTGOING)
private List<DependsOnRelationship> dependsOn;

@Relationship(type = "DEPENDED_ON_BY", direction = OUTGOING)
private List<ServiceNode> dependedOnBy;
```

**Advantages:**
- Upstream and downstream traversal Cypher queries are symmetric in form — both follow outgoing edges
- Eliminates the need for `direction = INCOMING` in queries, which some developers find confusing

**Trade-offs:**
- Every edge insert/delete must maintain two relationships atomically — doubled write complexity
- Data integrity: if the two relationship types diverge (e.g., a crash mid-write), the graph is inconsistent
- Cypher natively handles direction with `<-[:DEPENDS_ON]-` syntax — the symmetry benefit is marginal
- Neo4j's native traversal handles incoming/outgoing direction efficiently; there is no query performance reason to duplicate edges

**Pick when:** Never for this system — Cypher handles both directions natively, and dual relationship maintenance adds risk with no real benefit.

## Recommendation
**Option 2: Rich `@RelationshipProperties` with `DependsOnRelationship`.** The domain will benefit from knowing when a dependency was registered and what type it is (runtime vs. build-time vs. optional). These are natural attributes that help on-call engineers interpret blast-radius results. The extra class is a small cost for meaningful extensibility. All Cypher traversal queries work identically whether the relationship has properties or not.

## Consequences
**If accepted:** Define `ServiceNode` (`@Node("Service")`) with `id`, `name`, `description`, and `List<DependsOnRelationship> dependsOn`. Define `DependsOnRelationship` (`@RelationshipProperties`) with `@RelationshipId Long id`, `@TargetNode ServiceNode target`, `String dependencyType`, and `Instant createdAt`. Both classes live in the `domain` package. The `ServiceRepository` extends `Neo4jRepository<ServiceNode, UUID>`. Upstream queries use `direction = INCOMING` in Cypher or `MATCH (dep)-[:DEPENDS_ON]->(s)`.

**Watch out for:** SDN eagerly loads the full subgraph by default when fetching a `ServiceNode` — this will pull in the entire connected graph for a well-connected node. Use `@Query` with explicit Cypher to load only the immediate neighbours when that is all that is needed for a given operation. Never call `findAll()` on the repository without a depth or projection limit.
