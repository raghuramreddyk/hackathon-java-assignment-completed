package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class SwaggerUITest {

  @Test
  public void testSwaggerUIEndpointIsAvailable() {
    given()
        .when().get("/q/swagger-ui/")
        .then()
        .statusCode(200);
  }

  @Test
  public void testOpenApiSchemaIsAvailable() {
    given()
        .when().get("/q/openapi")
        .then()
        .statusCode(200);
  }
}
