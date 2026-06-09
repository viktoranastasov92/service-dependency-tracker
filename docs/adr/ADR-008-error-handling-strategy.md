# ADR-008: Error Handling Strategy

## Status
Proposed

## Context
A REST API that returns inconsistent or opaque error responses is difficult to consume and debug. The system must communicate errors — validation failures, missing resources, cycle conflicts, duplicate registrations — in a uniform, machine-readable format so that both the UI and programmatic clients can handle them reliably without parsing error messages.

## Decision Drivers
- All error responses must use a consistent JSON schema so clients can deserialize errors with a single model class
- HTTP status codes must accurately reflect the type of error (4xx for client errors, 5xx for server errors)
- Error messages must be actionable: they should tell the client what went wrong and ideally how to fix it
- Internal stack traces must never leak to external API consumers
- The implementation must not scatter try/catch blocks across every controller method
- Spring Boot's default error handling (`/error` endpoint with `BasicErrorController`) must be replaced or extended

## Options Considered

### Option 1: `@RestControllerAdvice` with a centralized `GlobalExceptionHandler`
**Description:** A single `@RestControllerAdvice` class intercepts all exceptions thrown from any `@RestController`. Each exception type is mapped to an `@ExceptionHandler` method that returns a `ResponseEntity<ApiError>`. `ApiError` is a consistent DTO with fields: `status` (int), `error` (string), `message` (string), `timestamp` (ISO-8601), and optionally `path` (string).

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ServiceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ServiceNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(404).body(new ApiError(404, "Not Found", ex.getMessage(), req.getRequestURI()));
    }
    @ExceptionHandler(CycleDetectedException.class)
    public ResponseEntity<ApiError> handleCycle(CycleDetectedException ex, HttpServletRequest req) {
        return ResponseEntity.status(409).body(new ApiError(409, "Conflict", ex.getMessage(), req.getRequestURI()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        // collect field errors
    }
}
```

**Advantages:**
- Single location for all error handling — easy to audit and extend
- No try/catch blocks in controllers; they stay focused on the happy path
- Custom exception classes (`ServiceNotFoundException`, `CycleDetectedException`, `DuplicateServiceException`) map directly to HTTP status codes
- `ApiError` DTO is a clear contract for UI and programmatic clients
- Fully testable: `MockMvc` tests verify the error response body and status code without mocking the handler

**Trade-offs:**
- Requires defining custom exception classes for each domain error category
- Developers must remember to throw the right exception type; throwing a generic `RuntimeException` will hit the catch-all handler

**Pick when:** A clean, uniform error response contract is required across all endpoints. This is the standard Spring Boot pattern and is recommended for this system.

---

### Option 2: Spring Boot's `ProblemDetail` (RFC 9457 / RFC 7807)
**Description:** Spring Boot 6+ (used in Boot 4.x via Spring Framework 6) has built-in support for RFC 9457 `ProblemDetail`. Controllers throw exceptions, and Spring automatically maps them to `application/problem+json` responses with `type`, `title`, `status`, `detail`, and `instance` fields. Custom extensions can add domain-specific fields.

```java
@ExceptionHandler(ServiceNotFoundException.class)
public ProblemDetail handleNotFound(ServiceNotFoundException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    pd.setTitle("Service Not Found");
    pd.setType(URI.create("https://example.com/errors/service-not-found"));
    return pd;
}
```

**Advantages:**
- Industry-standard error format (RFC 9457) — clients that understand the standard can parse errors generically
- Spring Boot 4.x has first-class support; minimal boilerplate
- `application/problem+json` content type signals to clients that this is a structured error
- `instance` field maps naturally to the request URI

**Trade-offs:**
- RFC 9457 is less universally known than a simple custom `ApiError` DTO among frontend developers
- The `type` field is expected to be a resolvable URI describing the error type — requires defining and hosting those URIs
- Slightly more verbose setup compared to a simple `ApiError` record

**Pick when:** The API may be consumed by external parties who understand RFC standards, or standardized error formats are an explicit requirement.

---

### Option 3: Per-controller try/catch with manual `ResponseEntity` construction
**Description:** Each controller method wraps its logic in a try/catch block and returns `ResponseEntity` with the appropriate status and a simple error string or map.

```java
@PostMapping("/{id}/dependencies")
public ResponseEntity<?> addDependency(@PathVariable String id, @RequestBody DependencyRequest req) {
    try {
        dependencyService.addDependency(id, req.getDependsOnId());
        return ResponseEntity.status(201).build();
    } catch (ServiceNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (CycleDetectedException e) {
        return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }
}
```

**Advantages:**
- Explicit and locally visible — no "magic" handler elsewhere in the codebase
- No additional classes required for simple projects

**Trade-offs:**
- Duplicates error handling logic across every controller method
- Response shape may drift between endpoints if developers are not disciplined
- Controller methods become long and hard to read
- Validation errors (`MethodArgumentNotValidException`) still need a global handler

**Pick when:** Never. This approach is an anti-pattern for any API with more than one or two endpoints.

## Recommendation
**Option 1: `@RestControllerAdvice` with a centralized `GlobalExceptionHandler`.** It keeps controllers clean, provides a uniform error contract, and is the standard Spring Boot pattern. Consider adopting `ProblemDetail` (Option 2) as the `ApiError` body format to get RFC 9457 compliance with minimal extra effort — the two options compose naturally.

## Consequences
**If accepted:** Create the following custom exceptions: `ServiceNotFoundException` (404), `DuplicateServiceException` (409), `CycleDetectedException` (409), `DependencyNotFoundException` (404). The `GlobalExceptionHandler` maps each to a consistent `ApiError` response. Add a catch-all handler for `Exception.class` that returns 500 without exposing the stack trace. Configure `server.error.include-stacktrace=never` in `application.properties`.

**Watch out for:** Bean Validation errors (`@Valid` on request bodies) produce `MethodArgumentNotValidException` with a list of field-level errors. The handler must iterate `ex.getBindingResult().getFieldErrors()` and include them in the response — a single top-level `message` is not enough for form validation feedback in the UI.
