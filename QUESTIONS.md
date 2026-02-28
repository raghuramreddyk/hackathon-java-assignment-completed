# Questions

Here are 2 questions related to the codebase. There's no right or wrong answer - we want to understand your reasoning.

## Question 1: API Specification Approaches

When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded everything directly. 

What are your thoughts on the pros and cons of each approach? Which would you choose and why?

**Answer:**
```txt
Design-First / API Spec (Warehouse API)
Pros:
- Strong Contract Guarantee: The OpenAPI YAML serves as the single source of truth.
- Parallel Development: Frontend or other teams can use the YAML to build mock servers and client SDKs immediately, without waiting for backend implementation.
- Ecosystem Tooling: Readily integrates with API gateways, documentation tools (Swagger UI), and security scanners.
Cons:
- Build Complexity: Relies heavily on build plugins (e.g., Maven/Gradle generators) and abstract code generation which can sometimes be difficult to customize or debug.

Code-First / Direct Implementation (Product & Store API)
Pros:
- Developer Velocity: Faster to bootstrap. You stay in raw Java, retaining total control over the framework’s annotations and features.
- Less Boilerplate: No dealing with generated source folders or plugin configurations.
Cons:
- Documentation Drift: Documentation must be manually maintained or generated from code after the fact, increasing the risk that the API spec falls out of sync with the actual implementation.
- Prone to Breaking Changes: It’s easier to accidentally alter a DTO in Java and silently break downstream consumers without a strict explicit contract failing the build.

Which would I choose and why?
I strongly recommend the Design-First (OpenAPI Code Generation) approach for this project.
Why? Because an API is a product, and the contract is its most critical interface. The strictness of code generation prevents developers from accidentally introducing breaking changes as the team scales. Furthermore, since the infrastructure (Maven plugins, dependencies) is already successfully set up for the Warehouse API, the primary con of the Design-First approach (build complexity) has already been mitigated. Migrating Product and Store to this approach will unify the codebase, eliminate context switching for developers, and ensure the entire system has a guaranteed-accurate API specification.
```

---

## Question 2: Testing Strategy

Given the need to balance thorough testing with time and resource constraints, how would you prioritize tests for this project? 

Which types of tests (unit, integration, parameterized, etc.) would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```txt
When under time and resource constraints, particularly for API-driven applications like this one, I would adopt the "Testing Honeycomb" strategy, optimizing for confidence per minute spent.

1. Integration Tests (High Priority): Focus the majority of the effort here. Use framework-specific features (e.g., @SpringBootTest or @QuarkusTest) to test the entire vertical slice from the HTTP endpoint down to the database. These tests provide the highest ROI because they verify that components interact correctly, serialization works, and database queries are valid. To prevent brittle tests and false positives, use Testcontainers (e.g., spinning up a real PostgreSQL container) instead of an in-memory DB like H2.

2. Unit & Parameterized Tests (Medium Priority, Targeted): Do not waste time writing unit tests for simple pass-through controllers, DTOs, or standard CRUD repositories. Instead, reserve unit tests strictly for complex core business logic (e.g., pricing engines, state machines, complex validations). Use Parameterized Tests (@ParameterizedTest) extensively for these to cover numerous edge cases and boundary conditions with minimal code duplication.

3. End-to-End (E2E) Tests (Low Priority): Keep these to an absolute minimum (only the most critical "happy path" user flows). They are slow, fragile, and expensive to maintain under tight constraints.

Ensuring tests remain effective over time:
- Automation & Gates: Integrate tests into the CI/CD pipeline, forcing them to run and pass on every Pull Request.
- Meaningful Metrics: Use tools like JaCoCo to enforce a coverage baseline (e.g., 70-80%), but treat it as a safety net, not a goal.
- Mutation Testing: If the project matures and needs higher confidence, introduce mutation testing (like PIT) to guarantee that tests are actually asserting behaviors properly rather than just blindly running code to satisfy coverage metrics.
```
