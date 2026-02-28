package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class WarehouseSearchTest {

  @Inject
  WarehouseRepository warehouseRepository;

  @BeforeEach
  @Transactional
  public void setup() {
    warehouseRepository.deleteAll();

    createWarehouse("W-001", "AMSTERDAM-001", 100, 10, null);
    createWarehouse("W-002", "AMSTERDAM-001", 50, 5, null);
    createWarehouse("W-003", "ZWOLLE-001", 80, 20, null);
    createWarehouse("W-004", "ZWOLLE-001", 20, 0, null);
    createWarehouse("W-ARCHIVED", "AMSTERDAM-001", 100, 10, LocalDateTime.now());
  }

  private void createWarehouse(String buCode, String location, int capacity, int stock, LocalDateTime archivedAt) {
    DbWarehouse dbWarehouse = new DbWarehouse();
    dbWarehouse.businessUnitCode = buCode;
    dbWarehouse.location = location;
    dbWarehouse.capacity = capacity;
    dbWarehouse.stock = stock;
    dbWarehouse.createdAt = LocalDateTime.now().minusHours(1); // older
    dbWarehouse.archivedAt = archivedAt;
    warehouseRepository.persistAndFlush(dbWarehouse);
  }

  @Test
  public void testSearchByLocation() {
    given()
        .queryParam("location", "AMSTERDAM-001")
        .when().get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("$.size()", is(2))
        .body("businessUnitCode", containsInAnyOrder("W-001", "W-002"));
  }

  @Test
  public void testSearchByMinCapacity() {
    given()
        .queryParam("minCapacity", 80)
        .when().get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("$.size()", is(2))
        .body("businessUnitCode", containsInAnyOrder("W-001", "W-003"));
  }

  @Test
  public void testSearchByMaxCapacity() {
    given()
        .queryParam("maxCapacity", 50)
        .when().get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("$.size()", is(2))
        .body("businessUnitCode", containsInAnyOrder("W-002", "W-004"));
  }

  @Test
  public void testSearchByRange() {
    given()
        .queryParam("minCapacity", 40)
        .queryParam("maxCapacity", 90)
        .when().get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("$.size()", is(2))
        .body("businessUnitCode", containsInAnyOrder("W-002", "W-003"));
  }

  @Test
  public void testSearchSortByCapacityAsc() {
    given()
        .queryParam("sortBy", "capacity")
        .queryParam("sortOrder", "asc")
        .when().get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("businessUnitCode", contains("W-004", "W-002", "W-003", "W-001"));
  }

  @Test
  public void testSearchSortByCapacityDesc() {
    given()
        .queryParam("sortBy", "capacity")
        .queryParam("sortOrder", "desc")
        .when().get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("businessUnitCode", contains("W-001", "W-003", "W-002", "W-004"));
  }

  @Test
  public void testSearchPagination() {
    // Page 0, size 2, sorted by capacity asc
    given()
        .queryParam("sortBy", "capacity")
        .queryParam("sortOrder", "asc")
        .queryParam("page", 0)
        .queryParam("pageSize", 2)
        .when().get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("$.size()", is(2))
        .body("businessUnitCode", contains("W-004", "W-002"));

    // Page 1, size 2
    given()
        .queryParam("sortBy", "capacity")
        .queryParam("sortOrder", "asc")
        .queryParam("page", 1)
        .queryParam("pageSize", 2)
        .when().get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("$.size()", is(2))
        .body("businessUnitCode", contains("W-003", "W-001"));
  }

  @Test
  public void testSearchExcludesArchived() {
    given()
        .when().get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("businessUnitCode", not(hasItem("W-ARCHIVED")));
  }
}
