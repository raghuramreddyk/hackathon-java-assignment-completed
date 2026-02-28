# Java Hackathon Assignment - 6 Hour Edition

This is a **hackathon-style code assignment** designed to be completed in approximately **6 hours**. It covers API design, persistence, testing patterns, and transaction management in a real-world Quarkus application.

## Quick Start

```bash
# 1. Start the application in dev mode
./mvnw quarkus:dev

# 2. Access Swagger UI
open http://localhost:8080/q/swagger-ui

# 3. Run tests
./mvnw test

# 4. Compile and package
./mvnw package
```

## Before You Begin

Read the following documentation to understand the project and recent changes:

- [BRIEFING.md](BRIEFING.md) — Domain context and background
- [CODE_ASSIGNMENT.md](CODE_ASSIGNMENT.md) — The original assignment tasks
- [ISSUE_FIXES.md](ISSUE_FIXES.md) — Detailed log of bugs fixed (Concurrency, Transactions, Coverage, etc.)
- [dbstructure.md](dbstructure.md) — Database schema and referential documentation

---

## Architecture

This codebase follows **Hexagonal Architecture** (Ports & Adapters) with:

- Domain use cases isolated from REST and database concerns
- CDI events for post-commit integration calls
- OpenAPI-generated REST layer for the Warehouse API
- Hand-coded REST endpoints for Stores and Products

---

## Technologies

- **Java 17+**
- **Quarkus 3.13.3**
- **PostgreSQL** (via Docker or Quarkus Dev Services)
- **JUnit 5** + **Testcontainers** + **Mockito**
- **OpenAPI** (code generation for Warehouse API)

---

## Running the Code

```bash
# Compile and run tests
./mvnw clean test

# Run specific test class
./mvnw test -Dtest=ArchiveWarehouseUseCaseTest

# Start development mode
./mvnw quarkus:dev

# Access Swagger UI
open http://localhost:8080/q/swagger-ui
```

### Viewing Test Coverage (JaCoCo)

After running the tests (`./mvnw clean test`), a JaCoCo coverage report is generated specifically for the `warehouse` domain. You can view the full HTML report by opening this file in your browser:

```bash
open jacoco-report/index.html
```

### (Optional) Run in JVM mode

First compile:

```bash
./mvnw package
```

Start a PostgreSQL instance:

```bash
docker run -it --rm=true --name quarkus_test \
  -e POSTGRES_USER=quarkus_test \
  -e POSTGRES_PASSWORD=quarkus_test \
  -e POSTGRES_DB=quarkus_test \
  -p 15432:5432 postgres:13.3
```

Then run:

```bash
java -jar ./target/quarkus-app/quarkus-run.jar
```

Navigate to <http://localhost:8080/index.html>

---

**Good luck and have fun!** This is about demonstrating your understanding of production-grade patterns, not just writing code under pressure.
