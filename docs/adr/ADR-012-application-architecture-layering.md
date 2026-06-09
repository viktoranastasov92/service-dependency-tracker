# ADR-012: Application Architecture Layering

## Status
Proposed

## Context
The internal package and class structure of the backend determines how easily the codebase can be navigated, tested, and evolved. An explicit layering decision prevents the codebase from collapsing into a single package of mixed concerns, which is particularly problematic for a domain with non-trivial logic like graph traversal and cycle detection.

## Decision Drivers
- Graph traversal and cycle detection are domain logic — they must not live in controllers or JPA entities
- The persistence layer must be swappable (H2 today, PostgreSQL tomorrow) without touching business logic
- Each layer must be independently unit-testable (controllers without the database, services without HTTP)
- The package structure should communicate intent: a new developer should locate relevant code within seconds
- The architecture should not be over-engineered for a system of this scope

## Options Considered

### Option 1: Classic three-layer architecture (Controller → Service → Repository)
**Description:** Three explicit layers with one-directional dependencies: the web layer (controllers, DTOs, exception handlers) calls the service layer (domain logic, graph traversal, cycle detection), which calls the persistence layer (JPA repositories, entities). Each layer lives in its own package.

```
com.example.tracker
├── rest/              ← @RestController, request/response DTOs, GlobalExceptionHandler
├── service/           ← GraphTraversalService, CycleDetectionService, ServiceRegistry
├── repository/        ← Spring Data JPA interfaces
├── domain/            ← JPA entities (ServiceEntity, DependencyEntity)
└── config/            ← Spring @Configuration classes
```

**Advantages:**
- Universally understood by Java/Spring developers — no ramp-up time
- Clear dependency direction: `rest` → `service` → `repository/domain`
- Each layer is independently mockable in tests: controllers mock services, services mock repositories
- Spring Boot is designed around this pattern; auto-configuration aligns with it
- Simple and appropriate for a single-module application of this size

**Trade-offs:**
- "Service" layer can become a dumping ground if discipline is not maintained
- No explicit ports/adapters — swapping the persistence layer requires knowing which classes to replace
- Does not enforce domain purity (entities can leak into the rest layer if developers are careless)

**Pick when:** The team knows Spring, the application is single-module, and simplicity is valued over architectural purity. Recommended for this system.

---

### Option 2: Hexagonal Architecture (Ports and Adapters)
**Description:** The domain core (graph model, traversal logic) is isolated behind port interfaces. Adapters implement those interfaces for specific technologies: a `JpaServiceRepository` adapter implements the `ServiceRepository` port; a `SpringMvcAdapter` implements the inbound HTTP port.

```
com.example.tracker
├── domain/
│   ├── model/          ← Service, Dependency (plain Java, no JPA annotations)
│   ├── port/
│   │   ├── in/         ← use case interfaces (RegisterServiceUseCase, GetDependencyChainUseCase)
│   │   └── out/        ← repository port interfaces (ServiceRepository, DependencyRepository)
│   └── service/        ← use case implementations
├── adapter/
│   ├── in/rest/        ← @RestController classes
│   └── out/persistence/ ← JPA entities + Spring Data repositories + mappers
└── config/
```

**Advantages:**
- Domain logic has zero framework dependencies — pure Java, maximum testability
- Swapping persistence (H2 → PostgreSQL → Neo4j) requires only replacing the adapter, not touching domain code
- Use case interfaces document the system's capabilities explicitly

**Trade-offs:**
- Significantly more classes, interfaces, and mappers for a small application
- Developers unfamiliar with hexagonal architecture have a steep learning curve
- The mapping between domain objects and JPA entities (and back) is boilerplate that slows development
- The benefits of strict port isolation are not felt at this system's scale

**Pick when:** The domain is complex, the system is expected to outlive its current technology choices, and the team has architectural experience. Valuable but over-engineered here.

---

### Option 3: Feature-package (vertical slice) architecture
**Description:** Packages are organized by feature rather than by layer. Each feature package contains its own controller, service, repository, and DTOs.

```
com.example.tracker
├── service/            ← ServiceController, ServiceService, ServiceRepository, ServiceEntity
├── dependency/         ← DependencyController, DependencyService, DependencyRepository
├── traversal/          ← TraversalController, GraphTraversalService, TraversalResponse
└── common/             ← GlobalExceptionHandler, ApiError
```

**Advantages:**
- All code related to a feature is co-located — easy to navigate by feature
- Features can be extracted to separate modules later if the application grows

**Trade-offs:**
- The graph domain cuts across `service`, `dependency`, and `traversal` features, so shared logic (e.g., the adjacency map) has no clear home
- More complex than the three-layer approach for a small, tightly coupled domain
- Less immediately recognizable to Spring Boot developers

**Pick when:** The application has many loosely coupled features that rarely interact. This system's features are tightly coupled by the graph domain, making vertical slicing awkward.

## Recommendation
**Option 1: Classic three-layer architecture.** For an application of this scope and team size, the three-layer model is the right tool. It is familiar, testable, and sufficient. The graph traversal and cycle detection logic belong clearly in the service layer. If the system grows significantly, the three-layer structure can be evolved toward hexagonal without a full rewrite.

## Consequences
**If accepted:** The package structure is `rest/`, `service/`, `repository/`, `domain/`, `config/` under the base package. Request and response DTOs live in `rest/` (not in `domain/`) to prevent the API contract from bleeding into domain logic. Service layer classes are plain Spring `@Service` beans with constructor injection. No `@Autowired` field injection — constructor injection is mandatory for testability.

**Watch out for:** Do not let JPA entities (`@Entity` annotated classes) be returned directly from controllers. Always map to DTOs at the service or controller boundary. This prevents accidental lazy-loading exceptions (`LazyInitializationException`) when Jackson serializes the response outside the transaction boundary.
