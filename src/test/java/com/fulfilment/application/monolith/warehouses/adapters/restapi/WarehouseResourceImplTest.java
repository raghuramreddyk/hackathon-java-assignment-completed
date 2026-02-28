package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Integration tests for the Warehouse REST endpoints.
 *
 * Uses @QuarkusTest (not @QuarkusIntegrationTest) so it runs against the
 * in-process Quarkus server and is picked up by surefire + JaCoCo.
 *
 * Endpoints tested:
 * GET /warehouse → listAllWarehousesUnits()
 * POST /warehouse → createANewWarehouseUnit()
 * GET /warehouse/{id} → getAWarehouseUnitByID()
 * DELETE /warehouse/{id} → archiveAWarehouseUnitByID()
 * POST /warehouse/{id}/replacement → replaceTheCurrentActiveWarehouse()
 */
@QuarkusTest
public class WarehouseResourceImplTest {

  @Inject
  WarehouseRepository warehouseRepository;
  @Inject
  EntityManager em;

  private static final String BASE = "/warehouse";

  // ─── Test lifecycle ────────────────────────────────────────────────────────

  @BeforeEach
  @Transactional
  public void setup() {
    em.createQuery("DELETE FROM DbWarehouse").executeUpdate();
  }

  // ─── Helper: create a warehouse in its own committed transaction ───────────

  @Transactional(TxType.REQUIRES_NEW)
  Warehouse createWarehouse(String buCode, String location, int capacity, int stock) {
    Warehouse w = new Warehouse();
    w.businessUnitCode = buCode;
    w.location = location;
    w.capacity = capacity;
    w.stock = stock;
    w.createdAt = LocalDateTime.now();
    warehouseRepository.create(w);
    return w;
  }

  @Transactional(TxType.REQUIRES_NEW)
  void archiveWarehouse(String buCode) {
    Warehouse w = warehouseRepository.findByBusinessUnitCode(buCode);
    w.archivedAt = LocalDateTime.now();
    warehouseRepository.update(w);
  }

  // ─── GET /warehouse ────────────────────────────────────────────────────────

  @Test
  public void testListAllWarehousesReturnsEmptyWhenNone() {
    given()
        .when().get(BASE)
        .then()
        .statusCode(200)
        .body("size()", is(0));
  }

  @Test
  public void testListAllWarehousesReturnsActiveOnly() {
    createWarehouse("LIST-001", "AMSTERDAM-001", 80, 20);
    createWarehouse("LIST-002", "ZWOLLE-001", 30, 10);
    // Create and archive a third — should NOT appear in list
    createWarehouse("LIST-003", "TILBURG-001", 30, 5);
    archiveWarehouse("LIST-003");

    given()
        .when().get(BASE)
        .then()
        .statusCode(200)
        .body("businessUnitCode", hasItems("LIST-001", "LIST-002"))
        .body("businessUnitCode", not(hasItem("LIST-003")));
  }

  // ─── GET /warehouse/{id} ───────────────────────────────────────────────────

  @Test
  public void testGetWarehouseByIdFound() {
    createWarehouse("GET-001", "AMSTERDAM-001", 80, 20);

    given()
        .when().get(BASE + "/GET-001")
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo("GET-001"))
        .body("location", equalTo("AMSTERDAM-001"))
        .body("capacity", is(80))
        .body("stock", is(20));
  }

  @Test
  public void testGetWarehouseByIdNotFound() {
    given()
        .when().get(BASE + "/NO-SUCH-CODE")
        .then()
        .statusCode(404);
  }

  /** Covers toDate(non-null) branch — archivedAt is included in the response. */
  @Test
  public void testGetArchivedWarehouseStillReturnableById() {
    createWarehouse("GET-ARCH-001", "ZWOLLE-001", 30, 5);
    archiveWarehouse("GET-ARCH-001");

    given()
        .when().get(BASE + "/GET-ARCH-001")
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo("GET-ARCH-001"))
        // archivedAt must be present and non-null
        .body("archivedAt", notNullValue());
  }

  // ─── POST /warehouse ───────────────────────────────────────────────────────

  @Test
  public void testCreateWarehouseSuccessfully() {
    given()
        .contentType("application/json")
        .body("""
            {
              "businessUnitCode": "CREATE-001",
              "location": "AMSTERDAM-001",
              "capacity": 80,
              "stock": 20
            }
            """)
        .when().post(BASE)
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo("CREATE-001"))
        .body("location", equalTo("AMSTERDAM-001"))
        .body("capacity", is(80))
        .body("stock", is(20));
  }

  @Test
  public void testCreateWarehouseWithNullStockDefaultsToZero() {
    given()
        .contentType("application/json")
        .body("""
            {
              "businessUnitCode": "CREATE-002",
              "location": "ZWOLLE-001",
              "capacity": 30
            }
            """)
        .when().post(BASE)
        .then()
        .statusCode(200)
        .body("stock", is(0));
  }

  @Test
  public void testCreateWarehouseInvalidLocationReturns400() {
    given()
        .contentType("application/json")
        .body("""
            {
              "businessUnitCode": "CREATE-003",
              "location": "INVALID-LOCATION",
              "capacity": 50,
              "stock": 10
            }
            """)
        .when().post(BASE)
        .then()
        .statusCode(400);
  }

  @Test
  public void testCreateWarehouseCapacityExceedsLocationMaxReturns400() {
    // ZWOLLE-001 max = 40, requesting capacity = 100
    given()
        .contentType("application/json")
        .body("""
            {
              "businessUnitCode": "CREATE-004",
              "location": "ZWOLLE-001",
              "capacity": 100,
              "stock": 10
            }
            """)
        .when().post(BASE)
        .then()
        .statusCode(400);
  }

  // ─── DELETE /warehouse/{id} (archive) ────────────────────────────────────

  @Test
  public void testArchiveWarehouseSuccessfully() {
    createWarehouse("ARCH-001", "AMSTERDAM-001", 80, 20);

    given()
        .when().delete(BASE + "/ARCH-001")
        .then()
        .statusCode(204);

    // Verify it no longer appears in the active list
    given()
        .when().get(BASE)
        .then()
        .body("businessUnitCode", not(hasItem("ARCH-001")));
  }

  @Test
  public void testArchiveNonExistentWarehouseReturns404() {
    given()
        .when().delete(BASE + "/NO-SUCH-CODE")
        .then()
        .statusCode(404);
  }

  @Test
  public void testArchiveAlreadyArchivedWarehouseReturns400() {
    createWarehouse("ARCH-002", "AMSTERDAM-001", 80, 20);

    // First archive → 204
    given().when().delete(BASE + "/ARCH-002").then().statusCode(204);

    // Second archive → 400
    given().when().delete(BASE + "/ARCH-002").then().statusCode(400);
  }

  // ─── POST /warehouse/{id}/replacement (replace) ──────────────────────────

  @Test
  public void testReplaceWarehouseSuccessfully() {
    createWarehouse("REPL-001", "AMSTERDAM-001", 80, 20);

    given()
        .contentType("application/json")
        .body("""
            {
              "location": "ZWOLLE-001",
              "capacity": 30,
              "stock": 10
            }
            """)
        .when().post(BASE + "/REPL-001/replacement")
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo("REPL-001"))
        .body("location", equalTo("ZWOLLE-001"))
        .body("capacity", is(30))
        .body("stock", is(10));
  }

  @Test
  public void testReplaceNonExistentWarehouseReturns404() {
    given()
        .contentType("application/json")
        .body("""
            {
              "location": "AMSTERDAM-001",
              "capacity": 50,
              "stock": 10
            }
            """)
        .when().post(BASE + "/NO-SUCH-CODE/replacement")
        .then()
        .statusCode(404);
  }

  @Test
  public void testReplaceArchivedWarehouseReturns400() {
    createWarehouse("REPL-002", "AMSTERDAM-001", 80, 20);
    archiveWarehouse("REPL-002");

    given()
        .contentType("application/json")
        .body("""
            {
              "location": "ZWOLLE-001",
              "capacity": 30,
              "stock": 10
            }
            """)
        .when().post(BASE + "/REPL-002/replacement")
        .then()
        .statusCode(400);
  }

  @Test
  public void testReplaceWithInvalidLocationReturns400() {
    createWarehouse("REPL-003", "AMSTERDAM-001", 80, 20);

    given()
        .contentType("application/json")
        .body("""
            {
              "location": "INVALID-LOCATION",
              "capacity": 50,
              "stock": 10
            }
            """)
        .when().post(BASE + "/REPL-003/replacement")
        .then()
        .statusCode(400);
  }

  @Test
  public void testReplaceCapacityExceedsLocationMaxReturns400() {
    createWarehouse("REPL-004", "AMSTERDAM-001", 80, 20);

    // ZWOLLE-001 max = 40, requesting 100
    given()
        .contentType("application/json")
        .body("""
            {
              "location": "ZWOLLE-001",
              "capacity": 100,
              "stock": 10
            }
            """)
        .when().post(BASE + "/REPL-004/replacement")
        .then()
        .statusCode(400);
  }
}
