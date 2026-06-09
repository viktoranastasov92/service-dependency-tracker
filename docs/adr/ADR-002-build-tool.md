# ADR-002: Build Tool

## Status
Accepted — Option 1: Maven with Maven Wrapper

## Context
A build tool manages compilation, dependency resolution, test execution, and artifact packaging. The project already uses Maven (Maven Wrapper is committed), so this decision largely validates the existing choice and rules out migration to alternatives.

## Decision Drivers
- Maven Wrapper (`mvnw` / `mvnw.cmd`) is already committed and working
- The team must be able to build and run the project with a single command (`./mvnw spring-boot:run`)
- Build reproducibility is important — the wrapper pins the Maven version
- The chosen build tool must have first-class Spring Boot plugin support
- CI/CD pipelines should require no custom toolchain installation

## Options Considered

### Option 1: Maven (current — via Maven Wrapper)
**Description:** Apache Maven is a declarative XML-based build tool with a convention-over-configuration lifecycle. The Maven Wrapper (`mvnw`) pins the exact Maven version in `.mvn/wrapper/maven-wrapper.properties`, making the build fully self-contained.

**Advantages:**
- Already in place — `pom.xml`, `.mvn/`, `mvnw`, and `mvnw.cmd` are all committed
- `spring-boot-maven-plugin` is the reference plugin for Spring Boot; best-supported and most documented
- Declarative POM is easy to read and review in pull requests
- Excellent IDE support (IntelliJ, Eclipse, VS Code all have first-class Maven integration)
- Maven Central is the most mature artifact repository; no additional repository configuration needed for standard dependencies

**Trade-offs:**
- Verbose XML configuration for non-standard build logic
- No incremental compilation across sub-modules out of the box (less relevant for a single-module project)
- Slower than Gradle for large multi-module builds due to lack of build caching

**Pick when:** The project is already using Maven, the team knows it, and there is no compelling reason to migrate.

---

### Option 2: Gradle (Kotlin DSL)
**Description:** A Groovy/Kotlin DSL build tool with an incremental build engine, build caching, and a flexible task graph. The Spring Boot Gradle plugin is fully supported and functionally equivalent to the Maven plugin.

**Advantages:**
- Kotlin DSL provides type-safe build scripts with IDE auto-complete
- Incremental compilation and build caching significantly reduce CI build times on large projects
- More concise configuration for complex custom build logic
- Gradle Wrapper (`gradlew`) provides the same self-contained benefit as Maven Wrapper

**Trade-offs:**
- Migration cost: the existing `pom.xml` must be rewritten as `build.gradle.kts`
- Build scripts are code, which means they can become complex and hard to audit
- Longer initial configuration for developers unfamiliar with Gradle
- Build daemon behavior can occasionally cause stale-state issues that require `./gradlew --stop`

**Pick when:** The project has multiple sub-modules, build performance is a bottleneck, or the team already prefers Gradle.

---

### Option 3: Maven with Build Extensions (Maven Daemon / mvnd)
**Description:** Standard Maven augmented with `mvnd` (Maven Daemon), which keeps a warm JVM process between builds to reduce build overhead, similar to the Gradle daemon.

**Advantages:**
- Near-zero migration cost — `pom.xml` unchanged; only the invocation command changes
- Significantly faster repeated builds (2–5x) due to warm JVM and parallel module building
- Fully compatible with all existing Maven plugins and lifecycle phases

**Trade-offs:**
- `mvnd` is a separate binary that must be installed or bundled; slightly more complex CI setup
- Less widely known than standard Maven or Gradle
- Still limited to single-module build with no incremental compilation benefit

**Pick when:** Build speed is a pain point but migrating to Gradle is not worth the disruption.

## Recommendation
**Option 1: Maven with Maven Wrapper.** The wrapper is already committed and the `pom.xml` is functional. There is no build-performance problem to solve at this project size. Introducing Gradle would cost migration time with no tangible benefit.

## Consequences
**If accepted:** All build commands use `./mvnw` (Linux/macOS) or `mvnw.cmd` (Windows). The Spring Boot Maven plugin handles packaging (`./mvnw package`), running (`./mvnw spring-boot:run`), and test execution (`./mvnw test`). Dependencies are declared in `pom.xml`.

**Watch out for:** Keep the `pom.xml` dependency list minimal and prefer Spring Boot's managed versions (inherited from `spring-boot-starter-parent`) over pinning explicit versions, to avoid version conflicts.
