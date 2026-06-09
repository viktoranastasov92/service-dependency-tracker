# ADR-013: API Documentation

## Status
Accepted — Option 4: API-first with `openapi-generator-maven-plugin`

## Context
The API will be consumed by the UI (built in ADR-010) and potentially by other internal tools at GlobalCorp. Clear, up-to-date API documentation reduces integration friction and supports on-call engineers who may need to query the API directly via curl during an incident. Documentation must stay synchronized with the implementation.

## Decision Drivers
- Documentation must be accurate — stale docs are worse than no docs for incident response
- An API-first approach (spec before code) enforces the contract at compile time and aligns with industry best practice for collaborative API design
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

---

### Option 4: API-first with `openapi-generator-maven-plugin` (spec drives code generation)
**Description:** The OpenAPI 3.1 specification is written and owned by the team as a YAML file (`src/main/resources/openapi/api.yaml`). The `openapi-generator-maven-plugin` runs during the Maven `generate-sources` phase and generates Spring MVC controller interfaces (delegate pattern) and request/response model classes directly from the spec. Production controllers implement the generated interfaces. If the spec changes, the plugin regenerates the stubs — and any implementation that no longer satisfies the interface fails to compile. The spec is the single source of truth; the code is derived from it.

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.x.x</version>
    <executions>
        <execution>
            <goals><goal>generate</goal></goals>
            <configuration>
                <inputSpec>${project.basedir}/src/main/resources/openapi/api.yaml</inputSpec>
                <generatorName>spring</generatorName>
                <apiPackage>com.example.tracker.rest.generated</apiPackage>
                <modelPackage>com.example.tracker.rest.dto</modelPackage>
                <configOptions>
                    <delegatePattern>true</delegatePattern>
                    <useSpringBoot3>true</useSpringBoot3>
                    <interfaceOnly>true</interfaceOnly>
                    <useTags>true</useTags>
                </configOptions>
            </configuration>
        </execution>
    </executions>
</plugin>
```

The generated interface for the services endpoint looks like:

```java
// Generated — do not edit
@RequestMapping("/api/v1")
public interface ServicesApi {
    @PostMapping("/services")
    ResponseEntity<ServiceDTO> registerService(@Valid @RequestBody RegisterServiceRequest body);

    @GetMapping("/services/{id}/downstream")
    ResponseEntity<TraversalResultDTO> getDownstream(@PathVariable String id);
}
```

The production controller simply implements it:

```java
@RestController
public class ServiceController implements ServicesApi {
    @Override
    public ResponseEntity<ServiceDTO> registerService(RegisterServiceRequest body) { ... }

    @Override
    public ResponseEntity<TraversalResultDTO> getDownstream(String id) { ... }
}
```

Add `springdoc-openapi-starter-webmvc-ui` alongside the generator to serve the spec as an interactive Swagger UI — the spec file is the source, not generated annotations.

**Advantages:**
- The spec is the contract: any divergence between spec and implementation is a compile error, not a runtime surprise
- The API can be fully designed, reviewed, and agreed upon before a single line of implementation is written
- Generated model classes (DTOs) eliminate the need to hand-write request/response records — they come from the spec
- The OpenAPI YAML can be shared with frontend developers (ADR-010) and UI code generation tools (e.g., `openapi-typescript-codegen`) so the frontend client is also generated from the same spec
- No annotation pollution in production controllers — `@Operation`, `@ApiResponse` live in the spec, not in Java code
- Changing the API requires changing the spec first — this enforces a deliberate, reviewed change process

**Trade-offs:**
- The generated source lives in `target/generated-sources/` — it must be added to `.gitignore` and the build must run before the project compiles in an IDE (run `./mvnw generate-sources` once after checkout)
- The generator occasionally produces verbose or slightly awkward Java for complex schema constructs; minor post-generation tweaks via `configOptions` are sometimes needed
- The team must learn OpenAPI YAML authoring; complex schemas (e.g., `oneOf`, discriminators) have a learning curve
- Generator version and Spring Boot version must be kept compatible; check the `openapi-generator` release notes when upgrading Spring Boot

**Pick when:** API-first is a team practice, the spec is treated as a first-class artifact, and compile-time contract enforcement is valued. This is the recommended industry standard for API-driven development.

## Recommendation
**Option 4: API-first with `openapi-generator-maven-plugin`.** Writing the spec before the implementation enforces a deliberate API design process, eliminates drift between documentation and code at compile time, and enables both the backend and frontend (ADR-010) to generate their respective artifacts from the same single source of truth. This is the correct choice for a team that values API design as a first-class engineering activity.

## Consequences
**If accepted:** Create `src/main/resources/openapi/api.yaml` with the full OpenAPI 3.1 specification covering all endpoints agreed in ADR-007. Add `openapi-generator-maven-plugin` to `pom.xml` with `delegatePattern=true`, `interfaceOnly=true`, and `useSpringBoot3=true`. Add `springdoc-openapi-starter-webmvc-ui` to serve the spec at `/swagger-ui.html`. Add `target/generated-sources/` to `.gitignore`. Add `./mvnw generate-sources` as the first step in the developer onboarding instructions. Production controllers implement the generated interfaces — they contain only business logic, no routing annotations.

**Watch out for:** IDE setup requires running `./mvnw generate-sources` at least once after a fresh checkout so the IDE can resolve the generated interfaces. In IntelliJ, mark `target/generated-sources/openapi/src/main/java` as a generated sources root. The `delegatePattern=true` option generates a delegate interface alongside the API interface — use the API interface directly in controllers (simpler) unless the delegate pattern is specifically needed for optional method overriding.
