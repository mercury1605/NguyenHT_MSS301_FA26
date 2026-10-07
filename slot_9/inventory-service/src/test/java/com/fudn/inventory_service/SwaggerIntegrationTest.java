package com.fudn.inventory_service;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SwaggerIntegrationTest {

    @ServiceConnection
    static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.3.0");

    @LocalServerPort
    private Integer port;

    static {
        mySQLContainer.start();
    }

    @BeforeEach
    void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @Test
    void swaggerUiShouldBeAccessible() {
        // RestAssured tu follow redirect /swagger-ui.html -> /swagger-ui/index.html
        RestAssured.given()
                .when().get("/swagger-ui.html")
                .then().statusCode(200)
                .body(containsString("Swagger UI"));
    }

    @Test
    void apiDocsShouldReturnJson() {
        RestAssured.given()
                .when().get("/api-docs")
                .then().statusCode(200)
                .contentType("application/json")
                .body("info.title", equalTo("Inventory Service API"))
                .body("info.version", equalTo("v0.0.1"));
    }

    @Test
    void apiDocsShouldContainInventoryEndpoints() {
        RestAssured.given()
                .when().get("/api-docs")
                .then().statusCode(200)
                .body("paths.'/api/inventory'.get", notNullValue());
    }

    @Test
    void apiDocsShouldDeclareBearerAuthAndUseGatewayUrl() {
        RestAssured.given()
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Host", "localhost")
                .header("X-Forwarded-Port", "9000")
                .when().get("/api-docs")
                .then().statusCode(200)
                .body("components.securitySchemes.bearerAuth.scheme", equalTo("bearer"))
                .body("security[0].bearerAuth", notNullValue())
                .body("servers[0].url", equalTo("http://localhost:9000"));
    }
}
