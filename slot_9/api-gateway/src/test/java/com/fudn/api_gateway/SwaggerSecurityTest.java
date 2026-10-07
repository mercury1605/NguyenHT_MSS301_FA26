package com.fudn.api_gateway;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnableWireMock(@ConfigureWireMock(baseUrlProperties = {
        "services.product.url", "services.order.url", "services.inventory.url" }))
class SwaggerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void swaggerUiShouldBeAccessibleWithoutToken() throws Exception {
        // /swagger-ui.html redirect sang /swagger-ui/index.html, khong bi 401
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void aggregateProductDocsShouldBePermittedWithoutToken() throws Exception {
        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/api-docs"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"info\":{\"title\":\"Product Service API\"}}")));

        mockMvc.perform(get("/aggregate/product-service/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Product Service API")));

        // setPath phai doi /aggregate/product-service/v3/api-docs -> /api-docs
        // X-Forwarded-* giup service ghi server URL = gateway trong docs (Try it out di qua gateway)
        WireMock.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/api-docs"))
                .withHeader("X-Forwarded-Host", WireMock.containing("localhost")));
    }

    @Test
    void protectedApiShouldRequireToken() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }
}
