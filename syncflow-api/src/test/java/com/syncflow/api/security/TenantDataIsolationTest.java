package com.syncflow.api.security;

import com.syncflow.api.config.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import org.junit.jupiter.api.AfterEach;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the P0 data-isolation fix end-to-end: data created by one tenant is
 * invisible to another tenant across the real HTTP + scoped-repository path.
 */
class TenantDataIsolationTest extends AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tenantiso")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("syncflow.encryption.key", () -> "MDEyMzQ1Njc4OWFiY2RlZg==");
    }

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @AfterEach
    void tearDown() {
        // Each test asserts exact counts; remove connections created by prior tests
        // under both tenants so counts don't accumulate across the shared DB.
        for (var tenant : java.util.List.of(TENANT_A, TENANT_B)) {
            given().header("X-Tenant-Id", tenant)
                    .when().get("/api/connections")
                    .then().statusCode(200)
                    .extract().jsonPath().getList("$", Map.class)
                    .forEach(row -> given().header("X-Tenant-Id", tenant)
                            .when().delete("/api/connections/{id}", row.get("id"))
                            .then().statusCode(204));
        }
    }

    private void createConnection(String tenantId, String name) {
        given().header("X-Tenant-Id", tenantId)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", name,
                        "connectionType", "POSTGRESQL",
                        "host", "localhost", "port", 5432, "database", "db",
                        "username", "u", "password", "p"))
                .when().post("/api/connections")
                .then().statusCode(201);
    }

    @Test
    @DisplayName("tenant A's connections are invisible to tenant B")
    void connectionListIsTenantScoped() {
        createConnection(TENANT_A, "a-conn");

        // Tenant B sees nothing created by tenant A.
        int tenantB = given().header("X-Tenant-Id", TENANT_B)
                .when().get("/api/connections")
                .then().statusCode(200)
                .extract().jsonPath().getList("$").size();
        assertEquals(0, tenantB, "tenant B must not see tenant A's connections");

        // Tenant A sees its own.
        int tenantA = given().header("X-Tenant-Id", TENANT_A)
                .when().get("/api/connections")
                .then().statusCode(200)
                .extract().jsonPath().getList("$").size();
        assertEquals(1, tenantA, "tenant A must see its own connection");
    }

    @Test
    @DisplayName("tenant B cannot fetch tenant A's connection by id")
    void connectionGetIsTenantScoped() {
        var id = given().header("X-Tenant-Id", TENANT_A)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "a-conn-2",
                        "connectionType", "POSTGRESQL",
                        "host", "localhost", "port", 5432, "database", "db",
                        "username", "u", "password", "p"))
                .when().post("/api/connections")
                .then().statusCode(201)
                .extract().path("id");

        // Tenant B gets a 404 for tenant A's connection.
        given().header("X-Tenant-Id", TENANT_B)
                .when().get("/api/connections/{id}", id)
                .then().statusCode(404);

        // Tenant A can fetch it.
        given().header("X-Tenant-Id", TENANT_A)
                .when().get("/api/connections/{id}", id)
                .then().statusCode(200);
    }
}
