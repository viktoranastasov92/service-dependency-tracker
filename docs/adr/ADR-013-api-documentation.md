# ADR-013: API Documentation

## Status
Proposed

## Context
The API will be consumed by the UI (built in ADR-010) and potentially by other internal tools at GlobalCorp. Clear, up-to-date API documentation reduces integration friction and supports on-call engineers who may need to query the API directly via curl during an incident. Documentation must stay synchronized with the implementation.

## Decision Drivers
- Documentation must be accurate — stale docs are worse than no docs for incident response
- Code-first (annotation-driven) documentation is easier to keep current than hand-authored specs
- The documentation should be interactively explorable (try-it-out) so engineers can test queries from the browser
- The Spring Boot ecosystem has mature tooling for this requirement
- Zero additional servers or services should be required to view the documentation

## Options Considered

### Option 1: springdoc-openapi (OpenAPI 3.1, Swagger UI)
**Description:** Add `springdoc-openapi-starter-webmvc-ui` to `pom.xml`. It auto-scans all `@RestController` classes and generates an OpenAPI 3.1 JSON spec at `/v3/api-docs` and an interactive Swagger UI at `/swagger-ui.html`. Enrich with `@Operation`, `@ApiResponse`, and `@Schema` annotations where auto-generation is insufficient.

**Advantages:**
- Zero configuration for the baseline — add the dependency and Swagger UI is live
- Stays synchronized with the code by construction — if an endpoint changes, the spec updates automatically
- Swagger UI's "Try it out" feature lets engineers execute API calls from the browser during development and incidents
- OpenAPI 3.1 spec can be imported into Postman, Insomnia, or API gateway tools
- Spring Boot 4.x is supported by `springdoc-openapi` 2.x

**Trade-offs:**
- Auto-generated docs often need manual `@Operation` annotations to add meaningful descriptions
- Swagger UI is not the most elegant UI; it is functional but dated
- The `/v3/api-docs` endpoint should be secured in a production deployment to avoid exposing the API surface to attackers

**Pick when:** Fast, accurate, interactive API documentation is needed with minimal investment. This is the standard choice for Spring Boot APIs and is recommended here.

---

### Option 2: Hand-authored OpenAPI YAML in `src/main/resources/`
**Description:** Write the OpenAPI 3.1 specification manually as a YAML file. Use `springdoc-openapi` or `swagger-ui-dist` to serve it as a static UI, or host it externally.

**Advantages:**
- Complete control over the documentation — no annotation clutter in production code
- The spec can be used as a design-first contract before implementation
- Can be version-controlled independently of the code

**Trade-offs:**
- Must be manually kept in sync with the implementation — guaranteed to drift
- Writing OpenAPI YAML by hand is tedious and error-prone
- No benefit over annotation-driven generation for a team that will maintain both

**Pick when:** The API is designed contract-first (spec before code) and a dedicated technical writer maintains the YAML. Not appropriate for a developer-maintained internal tool.

---

### Option 3: Spring REST Docs
**Description:** Documentation is generated from test output. `MockMvc` tests annotated with `@AutoConfigureRestDocs` produce AsciiDoc snippets; the Maven build assembles them into an HTML reference document.

**Advantages:**
- Documentation is guaranteed to be accurate because it is generated from passing tests
- Tests and docs co-evolve — if an endpoint changes and tests are not updated, the build fails
- AsciiDoc output can be integrated into a static site or wiki

**Trade-offs:**
- High setup cost: every documented endpoint needs a dedicated test that produces snippets
- No interactive "Try it out" capability — documentation is static HTML only
- Significantly more complex Maven build pipeline
- Less accessible for an on-call engineer who wants to quickly explore the API

**Pick when:** Documentation accuracy is a hard contractual requirement and the team is committed to maintaining test coverage for every documented endpoint.

## Recommendation
**Option 1: springdoc-openapi.** Single dependency addition, zero configuration baseline, interactive Swagger UI, and automatic synchronization with the code. For an internal tool, this hits the ideal point on the effort/value curve.

## Consequences
**If accepted:** Add `springdoc-openapi-starter-webmvc-ui` (version compatible with Spring Boot 4.x / Spring Framework 6) to `pom.xml`. Access Swagger UI at `http://localhost:8080/swagger-ui.html` during development. Annotate request DTOs with `@Schema(description = "...")` to document field-level semantics. Annotate controllers with `@Tag(name = "Services")` and `@Operation(summary = "...")` for meaningful grouping in the UI. Configure `springdoc.api-docs.path=/v3/api-docs` and `springdoc.swagger-ui.path=/swagger-ui.html` in `application.properties`.

**Watch out for:** By default, `springdoc-openapi` exposes the raw OpenAPI JSON at `/v3/api-docs`. If the API is ever exposed outside the internal network, add Spring Security to protect both `/v3/api-docs` and `/swagger-ui.html` behind an authentication check or IP allowlist.
