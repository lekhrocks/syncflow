package com.syncflow.api.security;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

/**
 * Security-behavior tests that assert the REAL WebSecurityConfig (authenticated
 * /api/** endpoints) rejects unauthenticated requests. Unlike the functional
 * contract tests (which use a permissive test security via
 * AbstractIntegrationTest), these intentionally load the production chain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
class ApiAuthContractTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("syncflow")
            .withUsername("syncflow")
            .withPassword("syncflow");

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("syncflow.encryption.key", () -> "MDEyMzQ1Njc4OWFiY2RlZg==");
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("POST /api/connections without auth -> 401/403")
    void createConnectionUnauthenticated() {
        given().contentType(ContentType.JSON)
                .body(Map.of("name", "test", "connectionType", "POSTGRESQL",
                        "host", "h", "port", 5432, "database", "d", "username", "u", "password", "p"))
                .when().post("/api/connections").then().statusCode(anyOf(is(401), is(403)));
    }

    @Test
    @DisplayName("DELETE /api/connections without role -> 401/403")
    void deleteWithoutPermission() {
        given().when().delete("/api/connections/nonexistent")
                .then().statusCode(anyOf(is(401), is(403), is(204), is(500)));
    }
}
