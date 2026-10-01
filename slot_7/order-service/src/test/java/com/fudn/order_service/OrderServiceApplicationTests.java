package com.fudn.order_service;

import com.fudn.order_service.stub.InventoryStubs;
import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock(@ConfigureWireMock(baseUrlProperties = "inventory.url"))
class OrderServiceApplicationTests {

    @ServiceConnection
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.3.0");

    @LocalServerPort
    private Integer port;

    @BeforeEach
    void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    static {
        mySQLContainer.start();
    }

    @Test
    void shouldSubmitOrder() {
        String json = """
                { "skuCode": "iphone_15", "price": 1000, "quantity": 1 }
                """;
        InventoryStubs.stubInventoryCall("iphone_15", 1);

        String body = RestAssured.given()
                .contentType("application/json")
                .body(json)
                .when().post("/api/order")
                .then().log().all()
                .statusCode(201)
                .extract().body().asString();

        assertThat(body, Matchers.is("Order Placed Successfully"));
        verify(getRequestedFor(urlEqualTo("/api/inventory?skuCode=iphone_15&quantity=1")));
    }

    @Test
    void shouldFailOrderWhenProductIsNotInStock() {
        String json = """
                { "skuCode": "iphone_15", "price": 1000, "quantity": 1000 }
                """;
        InventoryStubs.stubInventoryOutOfStock("iphone_15", 1000);

        RestAssured.given()
                .contentType("application/json")
                .body(json)
                .when().post("/api/order")
                .then().log().all()
                .statusCode(400);
    }
}