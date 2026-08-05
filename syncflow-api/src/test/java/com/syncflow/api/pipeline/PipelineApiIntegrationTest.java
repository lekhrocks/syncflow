package com.syncflow.api.pipeline;

import com.syncflow.api.config.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;

class PipelineApiIntegrationTest extends AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pipelinestest")
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

    private String createdPipelineId;

    @Test
    @Order(1)
    @DisplayName("POST /pipelines -> 201 creates pipeline")
    void createPipeline() {
        createdPipelineId = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "integration-pipeline",
                        "sourceConnectionId", "conn-1",
                        "sourceSchema", "public",
                        "sourceTable", "users",
                        "destConnectionId", "conn-2",
                        "destSchema", "public",
                        "destTable", "users_copy"))
                .when().post("/api/pipelines")
                .then()
                .statusCode(201)
                .body("name", equalTo("integration-pipeline"))
                .body("status", equalTo("DRAFT"))
                .body("$", hasKey("id"))
                .extract().path("id");
    }

    @Test
    @Order(2)
    @DisplayName("GET /pipelines -> 200 returns array")
    void listPipelines() {
        given()
                .when().get("/api/pipelines")
                .then()
                .statusCode(200)
                .body("$", isA(java.util.List.class));
    }

    @Test
    @Order(3)
    @DisplayName("GET /pipelines/{id} -> 200 returns pipeline")
    void getPipeline() {
        // Create first
        var id = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "get-test",
                        "sourceConnectionId", "c1", "sourceSchema", "s",
                        "sourceTable", "t", "destConnectionId", "c2",
                        "destSchema", "s", "destTable", "t"))
                .post("/api/pipelines")
                .path("id");

        given()
                .when().get("/api/pipelines/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id));
    }

    @Test
    @Order(4)
    @DisplayName("PUT /pipelines/{id} -> 200 updates pipeline")
    void updatePipeline() {
        var id = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "update-test",
                        "sourceConnectionId", "c1", "sourceSchema", "s",
                        "sourceTable", "t", "destConnectionId", "c2",
                        "destSchema", "s", "destTable", "t"))
                .post("/api/pipelines")
                .path("id");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "updated-name",
                        "sourceConnectionId", "c1", "sourceSchema", "s",
                        "sourceTable", "t", "destConnectionId", "c2",
                        "destSchema", "s", "destTable", "t"))
                .when().put("/api/pipelines/{id}", id)
                .then()
                .statusCode(200)
                .body("name", equalTo("updated-name"));
    }

    @Test
    @Order(5)
    @DisplayName("PUT /pipelines/{id} applies syncMode and batchSize")
    void updatePipelineSettings() {
        var id = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "settings-test",
                        "sourceConnectionId", "c1", "sourceSchema", "s",
                        "sourceTable", "t", "destConnectionId", "c2",
                        "destSchema", "s", "destTable", "t"))
                .post("/api/pipelines")
                .path("id");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("syncMode", "CDC_INCREMENTAL", "batchSize", 250))
                .when().put("/api/pipelines/{id}", id)
                .then()
                .statusCode(200)
                .body("settings.syncMode", equalTo("CDC_INCREMENTAL"))
                .body("settings.batchSize", equalTo(250));
    }

    @Test
    @Order(5)
    @DisplayName("DELETE /pipelines/{id} -> 204 deletes pipeline")
    void deletePipeline() {
        var id = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "delete-test",
                        "sourceConnectionId", "c1", "sourceSchema", "s",
                        "sourceTable", "t", "destConnectionId", "c2",
                        "destSchema", "s", "destTable", "t"))
                .post("/api/pipelines")
                .path("id");

        given()
                .when().delete("/api/pipelines/{id}", id)
                .then()
                .statusCode(204);
    }

    @Test
    @Order(5)
    @DisplayName("PUT /pipelines/{id} rejects invalid syncMode with 400")
    void updatePipelineInvalidSyncMode() {
        var id = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "bad-mode-test",
                        "sourceConnectionId", "c1", "sourceSchema", "s",
                        "sourceTable", "t", "destConnectionId", "c2",
                        "destSchema", "s", "destTable", "t"))
                .post("/api/pipelines")
                .path("id");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("syncMode", "NOT_A_MODE"))
                .when().put("/api/pipelines/{id}", id)
                .then()
                .statusCode(400);
    }

    @Test
    @Order(6)
    @DisplayName("POST /pipelines/{id}/validate -> 200 returns result")
    void validatePipeline() {
        var id = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "validate-test",
                        "sourceConnectionId", "c1", "sourceSchema", "s",
                        "sourceTable", "t", "destConnectionId", "c2",
                        "destSchema", "s", "destTable", "t"))
                .post("/api/pipelines")
                .path("id");

        given()
                .when().post("/api/pipelines/{id}/validate", id)
                .then()
                .statusCode(200)
                .body("$", hasKey("valid"))
                .body("$", hasKey("issues"));
    }

    @Test
    @Order(7)
    @DisplayName("POST /pipelines -> 400 on missing required fields")
    void createPipelineMissingFields() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "incomplete"))
                .when().post("/api/pipelines")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(8)
    @DisplayName("GET /pipelines/{id}/versions -> 200 returns version history")
    void pipelineVersions() {
        var id = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "versions-test",
                        "sourceConnectionId", "c1", "sourceSchema", "s",
                        "sourceTable", "t", "destConnectionId", "c2",
                        "destSchema", "s", "destTable", "t"))
                .post("/api/pipelines")
                .path("id");

        given()
                .when().get("/api/pipelines/{id}/versions", id)
                .then()
                .statusCode(200)
                .body("$", isA(java.util.List.class));
    }

    @Test
    @Order(9)
    @DisplayName("GET /pipelines/{id}/preview -> 200 returns preview")
    void pipelinePreview() {
        var id = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "preview-test",
                        "sourceConnectionId", "c1", "sourceSchema", "s",
                        "sourceTable", "t", "destConnectionId", "c2",
                        "destSchema", "s", "destTable", "t"))
                .post("/api/pipelines")
                .path("id");

        given()
                .when().get("/api/pipelines/{id}/preview", id)
                .then()
                .statusCode(200);
    }
}
