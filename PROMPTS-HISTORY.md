- Inside the "agents" directory create a markdown file defining a Solution Architect agent with responsibilities, expertise areas, and behavior guidelines - he is a master software       
  architect specializing in modern software architecture patterns, clean architecture principles, and distributed systems design

- In a similar manner create an agent inside the "agents" directory that is a senior backend developer - he is a senior backend developer specializing in modern Java 17+ with deep working knowledge of the Spring ecosystem, JVM internals at a level useful to application engineers. He ships production-grade code: tested, observable, operable, and shaped by the constraints of the project rather than by his own preferences.

- I want you to update this two agents if necessary as I want a test-driven development approach when developing my application - and create a new qa-engineer agent which will be         
  responsible for writing the unit and integration tests for the application that will be developed

- Include also a code-reviewer agent in the team alongside these 3 agents that were already created - he is an elite code reviewer who combines deep architectural judgment with rigorous, hands-on review of implementation, security, performance, and operability. His reviews are constructive, educational, and prioritized — they raise the quality bar without slowing the team to a crawl. He catches real defects before production, he points out real risks before they compound, and he does it in a tone that makes engineers want to bring their next PR.

- Use the solution-architect agent to analyze the requirements from file "readme-files/prescreen-brief-edited.md" and provide to me a directory and files for all architectural decision records (for example what database to use but not only) that this agent need to take into consideration - and for every decision that need to be made - the agent to provide at least three options that he recommends to solve that problem - the best options according to his expertise.

- For ADR-001 I choose option 1: Spring Boot. For ADR-002 I choose Option1: Maven. For ADR-003 - why have you not proposed other options apart from H2 in-file, PostgreSQL, pure in-memory ? I would prefer to see options with database instances that are suitable for our use case - so relational, non-relational or even non-relational graph databases to me are also good options to consider.

- For ADR-003 I choose option neo4j - update all related ADRs accordingly

- For ADR-004 I choose the recommended option 2 - Rich @RelationshipProperties with DependsOnRelationship. For ADR-005 I choose the recommended Option 1: Cypher variable-length path query as the primary traversal implementation, with Option 3 used for the graph visualization endpoint that feeds the UI

- Since I want cycles between nodes to be supported - for ADR-006 choose option 2 - Allow cycles; detect and report at read time

- Change the testing strategy in ADR-009 accordingly now that you are certain that cycles are allowed. And change ADR-005 to support DISTINCT guard if and where necessary

- For ADR-007 choose option 1 - resource-oriented REST with nested sub-resources for graph querie

- For ADR-008 I have most experience with RestControllerAdvice and a centralized GlobalExceptionHandler - it is also easier to debug and scan through the code - so accepted option to be  
  the first one

- For ADR-009 I definitely want to have integration tests apart from the obvious unit tests - so option1 appears to be best one in my opinion - let us go for it.

- I do not have that much of a front-end experience - I will go with the recommended option 1 for ADR-010 related to the ui frontend appro

- For ADR-011 - is option1 or option3 better for handling the neo4j readiness check in order to avoid startup race conditions ?

- Accept option 1 - race condition handling is more important to me than the trade-offs of it

- For ADR-012 I think option1 is the most intuitive and it will also be the easiest one for a newly joined engineer in the team to be introduced to the domain logic and to be able to debug it and to trace requests flow. It is also easier to maintain in my opinion. Make it accepted.

- For ADR-013 if I want the approach to be api-first instead of code-first - does that mean that I should choose option 2 or this option is not included in the recommended three options ?

- Yes - add a new option 4 and accept it - I want the approach to be api-first as this is in my opinion a good industry standard

- For ADR-014 accept the recommended option - definitely human-readable slugs as the external identifier would align with how engineers naturally refer to services

- Since we chose cypher querying in ADR-005 for the graph traversal algorithm it makes sense that for the data access pattern we stick to this approach and use spring data neo4j Neo4jRepository with Query cypher annotations (option 1) for the data access pattern - ADR-015 - accept that option

- For ADR-016 - cors and api integration - as I mentioned I do not have that much of experience with front-end applications and I will trust the pros and cons for option 1 - as there is a built-in proxy in Vite

- Now that all ADRs are accepted - let us start with the api.yaml spec since we agreed to be api-first

- Now wire up the openapi-generator-maven-plugin in pom.xml

- Now I ran some commands to fix the issues I faced by executing "" - like :
  "Illegal character in opaque part at index 2: /src/main/resources/openapi/api.yaml"

- Now let's set up the project skeleton and package structure

- Implement the backend in test-driven development cycles - start with the tests definitions and implementation only - use agent defined at "agents/qa-engineer.md"

- Update qa-engineer.md to make sure that the written tests in the test-driven development all follow the taken decisions in the ADRs

- In a similar manner update the senior-backend-developer.md and code-reviewer.md agents - so that they all work taking into account strictly the architectural decision records

- Now that the tests are ready in the TDD - use the senior-backend-developer.md agent to add all necessary business logic for the backend service

- Run the Tier 3 tests with Docker

- Implement the Dockerfile and docker-compose.yml - ADR-011

- Implement the React/Vite frontend - ADR-010 and ADR-016

- run `npm install` and test the dev server

- Start the backend separately

- Run a simple end-to-end test to verify functionality of frontend <-> backend services

- Create a README.md file with clear instructions on how to build, run, and test the application

- Enhance README.md file with a section for the remaining work like security specifics -
  authentication and authorization, https/tls, rate limiting and similar ones that you can think
  of. Also - observability, testing, deployment (ci/cd) and other specifics you can think of