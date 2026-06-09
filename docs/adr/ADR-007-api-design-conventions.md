# ADR-007: API Design Conventions

## Status
Proposed

## Context
The system exposes a RESTful HTTP API that frontend clients and potentially other internal tools will consume. The API must be intuitive enough that an on-call engineer can understand it without reading extensive documentation, and consistent enough that clients can be written once without case-by-case special handling.

## Decision Drivers
- The API is the primary integration surface; inconsistency here propagates to every consumer
- On-call engineers need to query blast radius quickly — URLs must be self-describing
- The API must clearly distinguish services (nodes) from dependencies (edges) as first-class resources
- HTTP status codes must be used correctly so generic HTTP clients (curl, Postman) behave as expected
- Pagination and filtering scope can be deferred but the URL structure should not preclude adding them later
- The API should be self-documenting via OpenAPI/Swagger

## Options Considered

### Option 1: Resource-oriented REST with nested sub-resources for graph queries
**Description:** Services are the primary resource (`/api/v1/services`). Dependencies are sub-resources of services (`/api/v1/services/{id}/dependencies`). Graph traversal queries are expressed as sub-resource collections on specific services: `/api/v1/services/{id}/upstream` and `/api/v1/services/{id}/downstream`. All responses are JSON. Versioning is via URL prefix (`/api/v1/`).

**Endpoint summary:**
```
POST   /api/v1/services                              — register a service
GET    /api/v1/services                              — list all services
GET    /api/v1/services/{id}                         — get service by id
DELETE /api/v1/services/{id}                         — remove a service

POST   /api/v1/services/{id}/dependencies            — add a dependency (body: { "dependsOnId": "..." })
GET    /api/v1/services/{id}/dependencies            — list direct dependencies
DELETE /api/v1/services/{id}/dependencies/{depId}    — remove a dependency

GET    /api/v1/services/{id}/upstream                — full transitive upstream graph
GET    /api/v1/services/{id}/downstream              — full transitive downstream graph
```

**Advantages:**
- URLs are self-describing and match the mental model ("a service has dependencies")
- Follows REST conventions that most HTTP clients and API consumers already understand
- `upstream` and `downstream` sub-resources make the blast-radius query obvious and bookmarkable
- Easy to add query parameters (`?depth=2`, `?format=tree`) without breaking the URL structure
- Maps cleanly to Spring MVC `@RestController` with `@PathVariable`

**Trade-offs:**
- Nested URLs can become deep (`/services/{id}/dependencies/{depId}`) — some teams find flat URLs easier
- "Upstream" and "downstream" are not standard REST sub-resources — they are actions/queries, which some strict REST purists object to (though this is pragmatic and widely accepted)

**Pick when:** Clarity for human consumers is the top priority and the team is comfortable with nested resource URLs. Recommended for this system.

---

### Option 2: Flat REST resources with query parameters for graph traversal
**Description:** Services are at `/api/v1/services`; dependencies are a completely separate resource at `/api/v1/dependencies`. Graph traversal is expressed as a query parameter: `/api/v1/services/{id}?traversal=upstream`.

**Endpoint summary:**
```
POST   /api/v1/services
GET    /api/v1/services
GET    /api/v1/services/{id}
GET    /api/v1/services/{id}?traversal=upstream|downstream

POST   /api/v1/dependencies           — body: { "fromId": "...", "toId": "..." }
GET    /api/v1/dependencies?serviceId=X
DELETE /api/v1/dependencies/{id}
```

**Advantages:**
- Flat URL structure is simpler — no deeply nested paths
- Dependencies as a standalone resource makes it easy to list all edges in the graph
- Query parameters for traversal are flexible

**Trade-offs:**
- The single `GET /services/{id}` endpoint doing double duty (get detail vs. run traversal) based on a query param is not clean; the response schema changes based on the param
- `POST /api/v1/dependencies` with a body is less intuitive than `POST /api/v1/services/{id}/dependencies`
- Discoverability is lower — it is less obvious that traversal is possible

**Pick when:** The team strongly prefers flat URL hierarchies, or the API is consumed by tools that handle query parameters better than path segments.

---

### Option 3: GraphQL API
**Description:** Expose a single `/graphql` endpoint with a schema that defines `Service`, `Dependency`, and query types for traversal. Clients specify exactly which fields they want.

```graphql
type Query {
  service(id: ID!): Service
  services: [Service!]!
  upstream(serviceId: ID!): [Service!]!
  downstream(serviceId: ID!): [Service!]!
}
type Mutation {
  registerService(name: String!, description: String): Service!
  addDependency(fromId: ID!, toId: ID!): Dependency!
  removeDependency(fromId: ID!, toId: ID!): Boolean!
}
```

**Advantages:**
- Clients can request exactly the fields they need — efficient for a UI that shows partial data
- Introspection makes the schema self-documenting
- A single endpoint simplifies routing

**Trade-offs:**
- Steeper learning curve: GraphQL tooling (Spring for GraphQL), schema design, and resolver patterns are new concepts for teams used to REST
- Error handling conventions differ from HTTP REST (GraphQL returns 200 with errors in the body)
- Browser fetch and curl usage is less natural than plain REST
- Overkill for an API with a small, stable surface area

**Pick when:** The UI has complex, highly variable data fetching requirements and the team has GraphQL experience.

## Recommendation
**Option 1: Resource-oriented REST with nested sub-resources.** It is the most immediately understandable design for an on-call engineer querying the API under stress. The `upstream` and `downstream` sub-resources make the blast-radius query a single, bookmarkable URL. Spring MVC maps this structure directly and cleanly.

## Consequences
**If accepted:** All controllers are under `/api/v1/` prefix. Use `@RequestMapping("/api/v1/services")` on the service controller. Use `ResponseEntity<T>` for all responses to allow explicit status code control. Register `springdoc-openapi-starter-webmvc-ui` to auto-generate Swagger UI at `/swagger-ui.html`. Use `@Valid` on request bodies with Bean Validation constraints. Represent IDs as strings (service names or UUIDs) in the URL path to avoid exposing internal database IDs.

**Watch out for:** The `DELETE /api/v1/services/{id}` endpoint must decide whether to cascade-delete all edges involving that service or reject deletion if edges exist. Define this behavior explicitly (see ADR-008) and document it in the OpenAPI spec.
