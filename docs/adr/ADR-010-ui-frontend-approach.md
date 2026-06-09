# ADR-010: UI / Frontend Approach

## Status
Accepted — Option 1: React (Vite) with `react-flow` for graph visualization

## Context
The system needs a user interface for on-call engineers to visualize the service dependency graph, search for services, and explore upstream/downstream relationships. The requirements specify the UI should consume the REST API and suggest fetch API or Axios as the HTTP client. The scale is an internal tool — it does not need to support millions of users or SEO.

## Decision Drivers
- On-call engineers need to quickly visualize and navigate a dependency graph — a visual representation (graph diagram or tree) is more useful than a plain list
- The frontend must consume the Spring Boot REST API (same origin or configured CORS)
- Minimal build tooling complexity is preferred — the project is backend-primary
- The UI does not need server-side rendering, authentication integration, or complex routing at initial release
- Developer velocity: a frontend developer (or a backend developer wearing a frontend hat) should be able to build the UI without a steep learning curve
- The frontend deliverable should be servable as static files from the Spring Boot application itself

## Options Considered

### Option 1: React (Vite) with a graph visualization library
**Description:** A single-page application built with React using Vite as the build tool. HTTP calls are made via the native `fetch` API or Axios. Graph visualization is provided by `react-flow` (interactive node-edge diagrams) or `vis-network`. The built output (static HTML/CSS/JS) is placed in `src/main/resources/static/` so Spring Boot serves it at the root URL.

**Advantages:**
- React is the most widely adopted frontend framework — highest chance the team has existing experience
- Vite provides sub-second hot module replacement during development
- `react-flow` is purpose-built for interactive directed graphs — renders service dependency graphs beautifully with pan, zoom, and node selection
- Component model maps naturally to the domain: `ServiceNode`, `DependencyEdge`, `TraversalPanel`
- The built static assets integrate into the Spring Boot JAR without any additional server

**Trade-offs:**
- Requires Node.js and npm/yarn for the frontend build — a separate toolchain from Maven
- `react-flow` adds bundle size; needs careful lazy-loading for production
- Two separate dev servers during development (Spring Boot on :8080, Vite on :5173) — requires CORS configuration or a Vite proxy

**Pick when:** The team has frontend experience or the graph visualization is a primary product feature. Recommended for this system.

---

### Option 2: Vanilla HTML/CSS/JavaScript with fetch API (no build step)
**Description:** A single `index.html` file with vanilla JavaScript using the native `fetch` API. The graph is rendered using `D3.js` (loaded from a CDN) for SVG-based force-directed layout. Placed directly in `src/main/resources/static/`.

**Advantages:**
- Zero build tooling — no Node.js, no npm, no bundler
- The entire frontend is one or two files that any developer can read and edit immediately
- Spring Boot serves it with no configuration change
- D3.js force-directed layout handles arbitrary graph structures well
- Perfect for a quick prototype or a tool that prioritizes function over form

**Trade-offs:**
- No component model — JavaScript grows complex quickly as features are added
- D3.js has a steep learning curve for developers not familiar with SVG and its selection model
- No TypeScript — runtime errors are caught late
- Harder to test than React components
- Does not scale well beyond a few hundred lines of JavaScript

**Pick when:** The UI is purely a prototype or internal debugging tool, the team has D3.js experience, and minimizing toolchain complexity is the top priority.

---

### Option 3: Vue 3 (Vite) with a graph library
**Description:** A single-page application built with Vue 3 using the Composition API and Vite. `vue-flow` (a Vue port of react-flow) or `cytoscape.js` provides graph visualization. Build output served as static files from Spring Boot.

**Advantages:**
- Vue 3 Composition API is arguably easier to learn than React hooks for developers new to frontend
- `vue-flow` provides the same quality of graph visualization as `react-flow`
- `cytoscape.js` is a powerful, framework-agnostic graph library with excellent layout algorithms
- Good TypeScript support via Volar

**Trade-offs:**
- Smaller community than React — fewer tutorials, StackOverflow answers, and third-party component libraries
- `vue-flow` is less mature than `react-flow`
- Same dual-server development workflow issue as React

**Pick when:** The team has Vue experience and prefers its template syntax over JSX.

## Recommendation
**Option 1: React (Vite) with `react-flow`.** The graph visualization requirement is central to the product value — engineers need to see the blast radius visually, not read a JSON list. `react-flow` provides first-class directed graph rendering. The Node.js toolchain adds minimal friction given that the project has a clear frontend scope. Vite's dev proxy eliminates the CORS problem during development.

## Consequences
**If accepted:** Create a `frontend/` directory at the project root (sibling to `src/`). Initialize with `npm create vite@latest frontend -- --template react-ts`. Configure `vite.config.ts` with a proxy: `"/api" → "http://localhost:8080"`. Add a Maven frontend plugin (`frontend-maven-plugin` or `exec-maven-plugin`) to run `npm run build` during `mvn package` and copy the output to `src/main/resources/static/`. In development, run `./mvnw spring-boot:run` and `npm run dev` concurrently.

**Watch out for:** The `react-flow` canvas requires an explicit pixel height on its container — it does not size itself to the viewport automatically. Plan the layout early. For graphs with more than ~200 nodes, enable `react-flow`'s minimap and ensure the node positions are computed with a layout algorithm (e.g., dagre for hierarchical layout of DAGs) rather than random placement.
