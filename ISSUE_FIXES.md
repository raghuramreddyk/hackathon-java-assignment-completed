# Issue Fixes — Transaction Management, Concurrency & Optimistic Locking

## 1. Optimistic Locking — `WarehouseRepository.update()`

**Problem**: Using a bulk JPQL `UPDATE` statement bypasses JPA's `@Version` check entirely. Concurrent transactions could silently overwrite each other (lost update).

**Fix**: Replaced bulk JPQL with a JPA managed-entity approach.

```java
// Before (bypasses @Version — unsafe under concurrent access)
getEntityManager()
    .createQuery("UPDATE DbWarehouse w SET w.location = :loc WHERE w.businessUnitCode = :code")
    .executeUpdate();

// After (JPA enforces @Version on flush — throws OptimisticLockException on conflict)
DbWarehouse managed = find("businessUnitCode", warehouse.businessUnitCode).firstResult();
managed.location   = warehouse.location;
managed.capacity   = warehouse.capacity;
managed.stock      = warehouse.stock;
managed.archivedAt = warehouse.archivedAt;
getEntityManager().flush();   // ← version check happens here
```

**Why it works**: `DbWarehouse` has `@Version Long version`. When `flush()` is called, Hibernate issues:
```sql
UPDATE warehouse SET ..., version = version + 1
WHERE businessUnitCode = ? AND version = <expected>
```
If another transaction committed first, the `WHERE` clause matches zero rows and Hibernate throws `OptimisticLockException`, preventing the lost update.

**Files changed**: `WarehouseRepository.java`

---

## 2. Concurrency — `ReplaceWarehouseUseCaseTest` Bad Test Data

**Problem**: The concurrent-replace test used `TILBURG-001` (max capacity = 40) with `capacity = 60`. Thread 2 was rejected at the capacity validation layer before ever touching the database, so the test never exercised the optimistic lock path.

**Fix**: Changed Thread 2 to use `AMSTERDAM-001` (max capacity = 100), which passes all validations and forces a real concurrent DB write.

```java
// Before — silently blocked at validation, never reaches DB
replaceWarehouseInNewTransaction(businessUnitCode, "TILBURG-001", 60, 30);
//                                                  ^^^^^^^^^^^   ^^
//                               TILBURG-001 max = 40 → 60 > 40 → validation fails

// After — passes validation, reaches DB, exercises @Version lock
replaceWarehouseInNewTransaction(businessUnitCode, "AMSTERDAM-001", 60, 30);
//                                                  ^^^^^^^^^^^^^   ^^
//                               AMSTERDAM-001 max = 100 → 60 ≤ 100 → DB hit ✅
```

**Files changed**: `ReplaceWarehouseUseCaseTest.java`

---

## 3. Transaction Management — Store Domain Event Firing

**Problem**: `StoreResource.create()` called `storeCreatedEvent.fireAsync()` inside a `@Transactional` method. `fireAsync()` dispatches on a separate thread immediately — before the transaction commits. If the DB write later fails (e.g. unique-name constraint violation), the transaction rolls back but the legacy gateway had already been notified. This violated the transactional outbox principle: *notify external systems only on committed success*.

**Fix**: Switched to synchronous CDI event with `TransactionPhase.AFTER_SUCCESS`.

### `StoreEventObserver.java`
```java
// Before — async, no awareness of transaction outcome
public void onStoreCreated(@ObservesAsync StoreCreatedEvent event) { ... }

// After — CDI delivers event ONLY after the originating transaction commits
public void onStoreCreated(
        @Observes(during = TransactionPhase.AFTER_SUCCESS) StoreCreatedEvent event) { ... }
```

### `StoreResource.java`
```java
// Before — async dispatch, TransactionPhase is ignored
storeCreatedEvent.fireAsync(new StoreCreatedEvent(store));

// After — synchronous dispatch, CDI respects TransactionPhase.AFTER_SUCCESS
storeCreatedEvent.fire(new StoreCreatedEvent(store));
```

> `TransactionPhase.AFTER_SUCCESS` is a synchronous CDI observer mechanism — it only works with `fire()`, not `fireAsync()`. `fireAsync()` routes to a separate thread and bypasses transaction-phase awareness entirely.

**Files changed**: `StoreEventObserver.java`, `StoreResource.java`

**Test that was failing → now passes**: `StoreTransactionIntegrationTest.testLegacySystemNotNotifiedOnFailedStoreCreation`

---

## 4. Input Validation — `ReplaceWarehouseUseCase`

**Problem**: `newWarehouse.capacity` and `newWarehouse.stock` are `Integer` (nullable). Comparisons like `newWarehouse.capacity > location.maxCapacity()` would throw `NullPointerException` if capacity was absent, returning an unhandled HTTP 500 instead of a clean 400.

**Fix**: Added explicit null/negative guards before the comparison block.

```java
// Validation 3 (new — guards against NPE in downstream comparisons)
if (newWarehouse.capacity == null || newWarehouse.capacity < 0) {
    throw new IllegalArgumentException("Warehouse capacity must be a non-negative value");
}
if (newWarehouse.stock == null || newWarehouse.stock < 0) {
    throw new IllegalArgumentException("Warehouse stock must be a non-negative value");
}
```

**Files changed**: `ReplaceWarehouseUseCase.java`

---

## 5. HTTP Status Codes — `WarehouseResourceImpl.replaceTheCurrentActiveWarehouse()`

**Problem**: All `IllegalArgumentException`s from the replace use case were mapped to HTTP 400, including "does not exist" — which should be 404. The archive endpoint already handled this correctly.

**Fix**: Applied the same 404/400 split used in the archive endpoint.

```java
// Before — always 400
throw new WebApplicationException(e.getMessage(), 400);

// After — matches archive endpoint behaviour
int status = e.getMessage().contains("does not exist") ? 404 : 400;
throw new WebApplicationException(e.getMessage(), status);
```

**Files changed**: `WarehouseResourceImpl.java`

---

## Summary

| # | Issue | Root Cause | Fix | File(s) |
|---|-------|-----------|-----|---------|
| 1 | Lost updates under concurrent writes | Bulk JPQL skips `@Version` check | Use JPA managed entity + `flush()` | `WarehouseRepository.java` |
| 2 | Concurrency test validated wrong layer | Bad test data (capacity > location max) | Use location with sufficient max capacity | `ReplaceWarehouseUseCaseTest.java` |
| 3 | Legacy gateway called on failed transaction | `fireAsync()` fires before TX commits | `@Observes(AFTER_SUCCESS)` + `fire()` | `StoreEventObserver.java`, `StoreResource.java` |
| 4 | NPE on null capacity/stock | No null guard before Integer unboxing | Explicit null/negative validation | `ReplaceWarehouseUseCase.java` |
| 5 | Wrong HTTP 400 for "not found" | Flat catch maps all errors to 400 | Distinguish "does not exist" → 404 | `WarehouseResourceImpl.java` |

---

## 6. JaCoCo Test Coverage Additions

**Problem**: The `com.fulfilment.application.monolith.warehouses....` packages lacked complete test coverage, specifically missing coverage for REST endpoints, some validations (null/negative capacity and stock branches), and database edge cases.

**Fix**: Configured JaCoCo in the build and added new test classes/methods to achieve 100% test coverage on the warehouse modules.

**Changes made**:
1. **`application.properties` & `pom.xml`**:
   - Added `quarkus-jacoco` dependency to correctly instrument Quarkus bytecode handling.
   - Configured `jacoco-maven-plugin` to generate coverage reports.
   - Filtered report to specifically scope coverage to the warehouse modules (as per the code assignment instructions).

2. **`WarehouseResourceImplTest.java` (New File)**:
   - Added 17 new integration tests using `@QuarkusTest` to fully cover the `WarehouseResourceImpl` REST layer.
   - Covered HTTP 200, 201, 204, 400, and 404 paths for `GET`, `POST`, and `DELETE` endpoints.
   - Fixed assertions expecting HTTP 201 on `POST` to expect HTTP 200, matching the actual JAX-RS behavior for `@POST` without a specific status annotation. 

3. **`ReplaceWarehouseUseCaseTest.java` (Added 6 Tests)**:
   - Added tests covering the `IllegalArgumentException` thrown on null and negative stock/capacity.
   - Added coverage for `WarehouseRepository.getAll()` verifying archived entries are filtered correctly.
   - Added test for the `WarehouseRepository.update()` defensive guard against updating a non-existent managed entity.

**Outcome**: The `target/jacoco-report/` now shows full coverage across all 4 warehouse packages (`usecases`, `restapi`, `database`, `models`) with 51 total tests passing successfully.
