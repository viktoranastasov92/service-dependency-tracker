# ADR-016: CORS and API Integration Between Frontend and Backend

## Status
Proposed

## Context
The frontend (React/Vite, ADR-010) and backend (Spring Boot, ADR-001) run on different ports during development (Vite on :5173, Spring Boot on :8080). The browser's Same-Origin Policy blocks cross-origin requests by default, requiring explicit CORS configuration. In production, both are served from the same origin (Spring Boot serves the built frontend static files), eliminating the need for CORS. This ADR decides how to handle cross-origin requests in development and how to structure the frontend-backend integration in production.

## Decision Drivers
- Developers must be able to run `./mvnw spring-boot:run` and `npm run dev` concurrently during development without CORS errors
- In production (JAR or Docker), the Spring Boot application serves the React build output from `src/main/resources/static/` — no CORS needed
- The CORS configuration must not be more permissive than necessary (no `allowedOrigins("*")` in production)
- The integration approach determines how the Maven build assembles the final artifact

## Options Considered

### Option 1: Vite proxy in development + Spring Boot serves static files in production
**Description:** During development, Vite's built-in dev server proxies `/api/**` requests to `http://localhost:8080`. No CORS configuration is needed in Spring Boot because the request reaches Spring Boot from the Vite server (same-origin from the browser's perspective). In production, `npm run build` outputs to `frontend/dist/`, the Maven build copies those files to `src/main/resources/static/`, and Spring Boot serves them at the root URL.

**Vite proxy config (`vite.config.ts`):**
```typescript
server: {
  proxy: {
    '/api': 'http://localhost:8080'
  }
}
```

**Maven integration:** Use `frontend-maven-plugin` to run `npm install` and `npm run build` during the `generate-resources` phase. Copy the output to `src/main/resources/static/` via the `maven-resources-plugin`.

**Advantages:**
- Zero CORS configuration in Spring Boot — the proxy handles it transparently
- Production deployment is a single JAR serving both API and UI from one port
- No environment-specific API base URL configuration needed in the React app — always `/api`
- The Vite proxy perfectly replicates the production routing behavior during development

**Trade-offs:**
- The frontend Maven build adds time to `mvn package` — mitigated by running `npm run build` only when frontend source changes
- Developers must remember to start both `./mvnw spring-boot:run` and `npm run dev` simultaneously during development
- `frontend-maven-plugin` configuration is non-trivial for first-time setup

**Pick when:** The goal is a single deployable artifact and minimal infrastructure. Recommended for this system.

---

### Option 2: Spring Boot CORS configuration with `@CrossOrigin` or `WebMvcConfigurer`
**Description:** Configure Spring Boot to allow CORS requests from `http://localhost:5173` in development. The frontend always runs on its own Vite server, even in production (deployed to a CDN or separate static host).

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:5173", "https://tracker.internal.globalcorp.com")
            .allowedMethods("GET", "POST", "DELETE")
            .allowedHeaders("Content-Type");
    }
}
```

**Advantages:**
- Frontend and backend are independently deployable — the UI can be hosted on a CDN
- No Maven/npm build integration required; teams work on frontend and backend separately
- CORS configuration is explicit and auditable

**Trade-offs:**
- Requires maintaining an allowlist of frontend origins — must be updated when the deployment URL changes
- Two separate deployment artifacts to manage (frontend static files + backend JAR)
- More complex infrastructure for what is an internal tool
- Risk of leaving overly permissive CORS in production

**Pick when:** The frontend is deployed independently (CDN, separate web server) and the backend is a pure API server.

---

### Option 3: Nginx reverse proxy for both development and production
**Description:** Run an Nginx container (added to `docker-compose.yml`) that proxies `/api` to Spring Boot and serves the React build output for all other paths.

**Advantages:**
- Exactly mirrors production behavior in development
- No CORS issues in any environment
- Nginx is a production-grade static file server

**Trade-offs:**
- Requires Docker running at all times — cannot run the backend standalone
- Adds operational complexity (Nginx config, Docker Compose service)
- Overkill for a development setup when Vite's built-in proxy does the same job

**Pick when:** The production deployment uses Nginx as a reverse proxy and exact environment parity is required during development.

## Recommendation
**Option 1: Vite proxy in development, Spring Boot static files in production.** One deployable artifact, zero CORS configuration, and a development experience where `/api` calls work identically in both environments. The `frontend-maven-plugin` integration is a one-time setup cost.

## Consequences
**If accepted:** Configure `vite.config.ts` with a proxy for `/api` pointing to `http://localhost:8080`. Add `frontend-maven-plugin` to `pom.xml` to install Node, run `npm install`, and run `npm run build` during `generate-resources`. Configure the `maven-resources-plugin` (or the frontend plugin's copy goal) to copy `frontend/dist/` to `${project.build.outputDirectory}/static/`. Add `spring.web.resources.static-locations=classpath:/static/` to `application.properties`. Configure Spring MVC's `addResourceHandlers` to return `index.html` for unknown paths (SPA fallback routing).

**Watch out for:** The SPA fallback (`/**` → `index.html`) must not intercept API calls (`/api/**`). Configure the fallback handler to apply only when the path does not start with `/api` and the request does not have an extension (i.e., it is a client-side route, not a static asset request).
