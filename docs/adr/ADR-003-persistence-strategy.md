# ADR-003: Persistence Strategy

## Status
Proposed

## Context
The system maintains a registry of services and a directed graph of dependency edges between them. The storage layer must support CRUD on nodes and edges, directed traversal in both directions (upstream / downstream), cycle detection on every write, and the ability to query the full transitive closure of any given node. The data volume is modest (hundreds to low thousands of services) but the access pattern is graph-shaped, which is a genuine differentiator between storage categories. This ADR considers relational, document, and native graph options.

## Decision Drivers
- Data must survive application restarts
- The access pattern is fundamentally graph-shaped: multi-hop traversal in both directions is a primary query
- Developer setup should be as frictionless as possible for a prototype / internal tool
- Spring Boot integration must be available and well-supported
- Testability: the storage layer must be exercisable in automated tests without requiring a live external process (or with a Testcontainers fallback)
- A clear path to a more production-grade deployment should exist if the prototype graduates
- Data volume does not justify horizontal scaling at this stage

---

## Options Considered

### Option 1: PostgreSQL (via Docker Compose or Testcontainers)
**Description:** A full relational database running in Docker locally, connected via the standard `spring-boot-starter-data-jpa` + `postgresql` JDBC driver. The graph is modelled as an adjacency list — two tables: `services` and `dependencies`. Multi-hop traversal is handled either by recursive CTEs (`WITH RECURSIVE`) at the SQL level or by iterative BFS in application code (see ADR-005).

**Advantages:**
- Production-equivalent from day one — no dialect gap between development and deployment
- Mature, battle-tested engine with excellent Spring Data JPA support
- `WITH RECURSIVE` CTE allows full transitive closure queries purely in SQL when needed
- Rich tooling ecosystem: pgAdmin, psql, DBeaver, DataGrip
- Testcontainers provides a real PostgreSQL instance in CI with one annotation (`@Container`)
- Well-understood operations story: backups, point-in-time recovery, replication all available

**Trade-offs:**
- Requires Docker to be installed and running for local development
- Multi-hop graph traversal in SQL (`WITH RECURSIVE`) is expressive but non-trivial to write and maintain; application-layer BFS is simpler but requires pulling edges into memory per hop
- Relational schema is not a natural fit for a graph — the impedance mismatch must be managed

**Pick when:** Production deployment to a real PostgreSQL instance is planned, dialect consistency matters, or the team is already comfortable with PostgreSQL operations.

---

### Option 2: H2 in file mode (embedded, file-backed)
**Description:** H2 embedded directly in the JVM process with file-backed persistence (`jdbc:h2:file:./data/tracker`). No separate process or Docker container. Spring Boot auto-configures everything. The graph model is the same adjacency list as Option 1 — JPA entities are portable.

**Advantages:**
- Zero infrastructure: no Docker, no installed server, `./mvnw spring-boot:run` is the entire setup
- Data survives restarts (unlike pure in-memory mode)
- H2 Console (`/h2-console`) provides a browser-based query interface during development
- Full JPA compatibility — the same entity classes run against PostgreSQL without modification
- H2 in-memory mode (`:mem:`) gives fast, fully isolated test databases with no external dependency

**Trade-offs:**
- Not suitable for concurrent multi-instance deployments (file lock restricts to a single JVM)
- Minor SQL dialect differences from PostgreSQL (`IDENTITY`, some window functions); ANSI SQL discipline is required
- H2 does not support PostgreSQL-specific features (full-text search, `jsonb`, advanced index types)

**Pick when:** Zero-friction local development is the top priority, the data volume is small, and a future migration to PostgreSQL is acceptable.

---

### Option 3: SQLite (via Xerial JDBC driver)
**Description:** A single-file embedded SQL database accessed through the `org.xerial:sqlite-jdbc` driver. Spring Data JPA works with SQLite through a Hibernate dialect (`com.github.gwenn:sqlite-dialect`). The graph is stored as an adjacency list, same as Options 1 and 2.

**Advantages:**
- Single `.db` file — trivially portable, version-controllable for small datasets, easy to attach to a bug report
- No JVM process overhead beyond the driver itself; extremely lightweight
- Zero infrastructure, same as H2
- Widely understood outside the Java ecosystem — many tools can read a `.db` file directly

**Trade-offs:**
- Hibernate + SQLite requires a third-party dialect that is not maintained by the Hibernate team; occasional incompatibilities with new Hibernate versions
- No native Spring Boot auto-configuration — `DataSource` bean must be configured manually
- Write concurrency is limited: SQLite uses a file-level write lock, which matters under any concurrent load
- `WITH RECURSIVE` CTE is supported but the dialect may not expose it cleanly through JPQL

**Pick when:** Maximum portability and minimal footprint matter more than Spring Boot integration ergonomics, and the dataset will remain small and single-user.

---

### Option 4: MongoDB (document store)
**Description:** A document-oriented database where each service is stored as a document containing its metadata and an embedded or referenced list of dependency IDs. Accessed via `spring-boot-starter-data-mongodb` and Spring Data MongoDB repositories.

**Advantages:**
- Flexible schema: service metadata fields can vary between services without schema migrations
- Spring Data MongoDB provides familiar repository abstractions comparable to Spring Data JPA
- Embedded document arrays can store direct neighbours without a join table
- MongoDB Atlas offers a free hosted tier, removing local infrastructure from the picture
- Testcontainers has a first-class MongoDB module for integration tests

**Trade-offs:**
- Documents are not a natural fit for graphs: multi-hop traversal requires either embedded denormalisation (which diverges on updates) or application-level recursive queries across many round-trips
- No equivalent to `WITH RECURSIVE` — transitive closure must be assembled entirely in application code, loading one hop at a time
- MongoDB's aggregation pipeline can express `$graphLookup` for graph traversal, but the syntax is complex and the depth is capped by the `maxDepth` parameter
- Adds operational complexity (separate MongoDB process or Atlas account) for a use case that is fundamentally relational
- Consistency guarantees weaker than a relational database for the write-then-read cycle-detection pattern

**Pick when:** The service metadata is highly variable and schema-free storage is the primary driver; not recommended when graph traversal is the dominant access pattern.

---

### Option 5: Neo4j (native graph database)
**Description:** A property graph database where services are `(:Service)` nodes and dependencies are `[:DEPENDS_ON]` directed relationships. Accessed via `spring-boot-starter-data-neo4j` and Spring Data Neo4j (SDN) with the Cypher query language. The entire graph traversal problem — upstream, downstream, cycle detection, transitive closure — is expressed natively.

**Advantages:**
- Native graph storage and index-free adjacency: traversal does not require joins or recursive CTEs — Cypher walks edges in O(edges touched) regardless of total graph size
- Upstream query: `MATCH (a)-[:DEPENDS_ON*]->(s:Service {name: $name}) RETURN a`; downstream: `MATCH (s)-[:DEPENDS_ON*]->(b) RETURN b` — concise and correct
- Built-in cycle detection: `MATCH p=(s)-[:DEPENDS_ON*]->(s)` detects a cycle before committing an edge
- Spring Data Neo4j maps `@Node` and `@Relationship` annotations analogously to JPA `@Entity`
- Neo4j Desktop or Neo4j Aura (free cloud tier) gives a browser-based visual graph explorer out of the box — directly useful for visualising the blast radius
- The data model matches the domain perfectly — no impedance mismatch

**Trade-offs:**
- Requires a running Neo4j instance (Docker, Neo4j Desktop, or Aura) — more infrastructure than H2/SQLite
- The team must learn Cypher; it is not difficult but it is an additional skill
- Spring Data Neo4j is less widely known than Spring Data JPA; fewer community resources for edge cases
- Testcontainers has a Neo4j module, but container startup is slower than H2 in-memory
- If the system ever needs to store highly relational non-graph data alongside the graph, Neo4j is not optimal

**Pick when:** The core access pattern is graph traversal, the team is open to learning Cypher, and a native graph model that matches the domain is valued over minimal infrastructure.

---

### Option 6: ArangoDB (multi-model: document + graph)
**Description:** A multi-model database that stores documents and natively supports graph traversal over named edge collections. Accessed from Spring Boot via the ArangoDB Spring Data integration (`arangodb-spring-data`). Services are stored as documents in a vertex collection; dependencies are stored in an edge collection.

**Advantages:**
- Multi-model: handles both document-style metadata queries and native graph traversal in a single engine
- AQL (ArangoDB Query Language) supports `FOR v, e, p IN 1..N OUTBOUND` graph traversal natively
- Single binary distribution with a built-in web UI (Aardvark) for graph exploration
- No depth limit on traversal (unlike MongoDB's `$graphLookup`)
- Free Community Edition available; Docker image is small and fast to start

**Trade-offs:**
- `arangodb-spring-data` is a community-maintained integration; less polished than Spring Data JPA or Spring Data Neo4j
- AQL is powerful but unfamiliar — another query language the team must learn
- Smaller community and ecosystem than PostgreSQL or Neo4j
- Testcontainers does not have an official ArangoDB module (can use the generic container approach)
- Less documentation and fewer production case studies than the other options

**Pick when:** A multi-model approach (graph + document in one engine) is compelling, and the team is willing to trade ecosystem maturity for flexibility.

---

## Recommendation
**Option 5: Neo4j** is the most architecturally honest choice for this system. The domain is a directed graph, the dominant access patterns are graph traversal (upstream/downstream chains), and cycle detection — all of which Neo4j handles natively with no impedance mismatch and concise Cypher queries. The Neo4j Aura free tier eliminates local infrastructure concerns during development.

**If zero-infrastructure is a hard constraint**, choose **Option 2: H2 in file mode**. It has no external dependency, full JPA compatibility, and a clean migration path. It is the right choice for a no-friction prototype that may be migrated to a proper database later.

**Option 1: PostgreSQL** is the right choice if the team decides the system will run in a production environment with PostgreSQL already in place and operational consistency with the rest of the stack is more important than graph-native storage.

## Consequences
**If Neo4j is accepted:** Add `spring-boot-starter-data-neo4j` and the Testcontainers Neo4j module to `pom.xml`. Define `@Node` entities (`ServiceNode`) and `@RelationshipProperties` for `DEPENDS_ON` edges. Connect to Neo4j Aura (cloud) or a local Docker container. ADR-004 (Graph Data Model) and ADR-015 (Data Access Pattern) must be updated to reflect the Neo4j node/relationship model instead of the JPA adjacency list model.

**If H2 is accepted:** Add `spring-boot-starter-data-jpa` and `h2` to `pom.xml`. Configure `spring.datasource.url=jdbc:h2:file:./data/tracker`. ADR-004 and ADR-015 remain as written (adjacency list + Spring Data JPA).

**Watch out for:** Whichever option is chosen, the cycle detection logic (ADR-006) and traversal algorithm (ADR-005) must be implemented consistently with the data model. With Neo4j, both can be expressed in Cypher and pushed to the database layer. With H2/PostgreSQL, both live in the application service layer.
