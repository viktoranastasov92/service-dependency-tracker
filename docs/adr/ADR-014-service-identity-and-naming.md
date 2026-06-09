# ADR-014: Service Identity and Naming

## Status
Proposed

## Context
Every service registered in the system must have a unique identity used in API paths, dependency edges, and query results. The choice between human-readable names as identifiers versus generated UUIDs has significant implications for API usability, URL design, and data integrity. On-call engineers querying the API under pressure need identifiers they can type and remember.

## Decision Drivers
- On-call engineers must be able to construct API URLs from memory or a simple naming convention (e.g., `/api/v1/services/payment-service/downstream`)
- Service names must be unique across the system to serve as natural identifiers
- The identifier must be stable — renaming a service should not break existing dependency edges
- The data model (ADR-004) uses a surrogate primary key (`id`) for the database row; this ADR decides what is exposed externally
- Identifiers appear in URLs, JSON responses, and potentially log output

## Options Considered

### Option 1: Human-readable slugs as the external identifier (name-as-id)
**Description:** Services are identified in the API by their name, which must be unique, lowercase, kebab-case (e.g., `payment-service`, `user-auth-db`). The database stores both a surrogate integer/UUID primary key (internal) and the name (unique constraint, external). API paths use the name: `GET /api/v1/services/payment-service/downstream`. The name is validated on registration: `[a-z0-9-]+`, max 100 characters.

**Advantages:**
- URLs are self-describing and memorable: `/api/v1/services/payment-service/upstream` is unambiguous
- No lookup required to construct a valid URL — the name is the ID
- JSON responses are human-readable without cross-referencing an ID table
- Error messages are clear: "Service 'payment-service' not found" vs "Service 'f3a2...' not found"
- Aligns with how engineers naturally refer to services in conversation and documentation

**Trade-offs:**
- If a service is renamed, all API clients using the old name will receive 404s — requires a migration or redirect
- Names must be validated and normalized on input (case-insensitivity, special character rejection)
- Slightly more complex unique constraint management than UUID primary keys

**Pick when:** Developer experience and URL readability are priorities, and service renaming is expected to be rare. Recommended for this system.

---

### Option 2: UUID as the external identifier
**Description:** Each service is assigned a UUID (`java.util.UUID.randomUUID()`) on registration. The UUID is the identifier in all API paths and foreign keys. Names are stored but treated as mutable display labels.

**Advantages:**
- IDs are stable regardless of name changes
- UUIDs are globally unique — safe to merge data from multiple instances
- No validation or normalization required for the identifier itself
- Standard practice in REST APIs that treat names as mutable metadata

**Trade-offs:**
- URLs are opaque: `/api/v1/services/f3a2b1c0-8d4e-4f5a-9b6c-7e8d9f0a1b2c/downstream`
- Engineers cannot construct URLs from memory — must look up the UUID first
- JSON responses are harder to read at a glance
- Cross-referencing UUIDs in logs and incident reports is cumbersome

**Pick when:** Service renaming is frequent, data is merged from multiple sources, or the API is consumed only by machines (not humans).

---

### Option 3: Dual identity — UUID internally, name as external alias with redirect
**Description:** Services have both a UUID (stable, internal) and a name (mutable, external alias). The API accepts both: `GET /api/v1/services/{idOrName}/downstream` resolves the path segment as a name first, falling back to UUID. A service rename updates the name while the UUID remains the canonical ID; responses always return the UUID so clients can store the stable reference.

**Advantages:**
- Handles renames gracefully — UUID references never break
- Readable URLs during normal use (name-based)
- Future-proof: clients that cache UUIDs are unaffected by renames

**Trade-offs:**
- Most complex implementation: the path resolution logic must handle both formats
- API documentation must explain the dual-identity model, which can confuse consumers
- Overkill for a system where service renames are expected to be rare events

**Pick when:** Service names change frequently and both human and machine consumers must be supported simultaneously without breaking changes.

## Recommendation
**Option 1: Human-readable slugs as the external identifier.** The primary users are on-call engineers who benefit enormously from readable, memorizable URLs. Service renames are rare operational events in a stable microservices environment — the trade-off is worth it. Enforce the slug format on registration and document the "rename = new registration + re-map dependencies" procedure in operational runbooks.

## Consequences
**If accepted:** The `ServiceEntity` has an `id` (UUID, internal, not exposed in the API) and a `name` (unique, indexed, used in all API paths and JSON). The `DependencyEntity` foreign keys reference the internal UUID but the API represents dependencies by name. All `@PathVariable String name` parameters in controllers are validated against the slug pattern before hitting the service layer. The `POST /api/v1/services` endpoint normalizes names to lowercase on input.

**Watch out for:** Two services with names that differ only in case (e.g., `Payment-Service` and `payment-service`) must be treated as the same service. Apply `name.toLowerCase().trim()` on input and enforce the unique constraint on the normalized form. Return `409 Conflict` with a clear message if a case-insensitive duplicate is detected.
