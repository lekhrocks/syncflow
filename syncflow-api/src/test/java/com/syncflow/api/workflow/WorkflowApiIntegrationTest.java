package com.syncflow.api.workflow;

import com.syncflow.api.config.AbstractIntegrationTest;
import com.syncflow.core.workflow.WorkflowInstance;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkflowApiIntegrationTest extends AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("workflowtest")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("syncflow.encryption.key", () -> "MDEyMzQ1Njc4OWFiY2RlZg==");
        registry.add("syncflow.jwt.secret",
                () -> "c3luY2Zsb3ctaHMyNTYtand0LXNlY3JldC1rZXktMjAyNi1jaGFuZ2UtaW4tcHJvZA==");
    }

    // --- Sequential workflow ---

    @Test
    void createAndStartSequentialWorkflow() {
        var wf = given()
                .header("Authorization", "Bearer " + adminToken("default"))
                .contentType(ContentType.JSON)
                .body(Map.of("pipelineId", "p-1"))
                .when().post("/api/workflows")
                .then()
                .statusCode(201)
                .body("$", hasKey("id"))
                .body("$", hasKey("pipelineId"))
                .body("status", equalTo("PENDING"))
                .extract().as(WorkflowInstance.class);

        assertNotNull(wf.id());
        assertEquals("p-1", wf.pipelineId());

        var started = given()
                .header("Authorization", "Bearer " + adminToken("default"))
                .when().post("/api/workflows/{id}/start", wf.id().value())
                .then()
                .statusCode(200)
                .body("status", equalTo("RUNNING"))
                .extract().as(WorkflowInstance.class);

        assertNotNull(started);
    }

    @Test
    void sequentialWorkflowStatusTransitions() {
        var wf = given()
                .header("Authorization", "Bearer " + adminToken("default"))
                .contentType(ContentType.JSON)
                .body(Map.of("pipelineId", "p-seq"))
                .post("/api/workflows")
                .path("id");

        given()
                .when().get("/api/workflows/{id}", wf)
                .then()
                .statusCode(200)
                .body("status", anyOf(equalTo("PENDING"), equalTo("RUNNING"), equalTo("COMPLETED")));
    }

    // --- Workflow cancellation ---

    @Test
    void cancelWorkflow() {
        var wf = given()
                .header("Authorization", "Bearer " + adminToken("default"))
                .contentType(ContentType.JSON)
                .body(Map.of("pipelineId", "p-cancel"))
                .post("/api/workflows")
                .path("id");

        given()
                .header("Authorization", "Bearer " + adminToken("default"))
                .when().post("/api/workflows/{id}/start", wf)
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + adminToken("default"))
                .when().post("/api/workflows/{id}/cancel", wf)
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    // --- Workflow list ---

    @Test
    void listWorkflows() {
        given()
                .when().get("/api/workflows")
                .then()
                .statusCode(200)
                .body("$", isA(java.util.List.class));
    }

    // --- Workflow graph ---

    @Test
    void getWorkflowGraph() {
        var wf = given()
                .header("Authorization", "Bearer " + adminToken("default"))
                .contentType(ContentType.JSON)
                .body(Map.of("pipelineId", "p-graph"))
                .post("/api/workflows")
                .path("id");

        given()
                .when().get("/api/workflows/{id}/graph", wf)
                .then()
                .statusCode(200)
                .body("$", isA(java.util.List.class));
    }

    // --- Pause and resume ---

    @Test
    void pauseAndResumeWorkflow() {
        var wf = given()
                .header("Authorization", "Bearer " + adminToken("default"))
                .contentType(ContentType.JSON)
                .body(Map.of("pipelineId", "p-pr"))
                .post("/api/workflows")
                .path("id");

        given()
                .header("Authorization", "Bearer " + adminToken("default"))
                .when().post("/api/workflows/{id}/pause", wf)
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + adminToken("default"))
                .when().post("/api/workflows/{id}/resume", wf)
                .then()
                .statusCode(200);
    }

    // --- Error handling ---

    @Test
    void getNonExistentWorkflow() {
        given()
                .when().get("/api/workflows/nonexistent")
                .then()
                .statusCode(404); // NoSuchElementException → 404
    }

    @Test
    void cancelNonExistentWorkflow() {
        given()
                .header("Authorization", "Bearer " + adminToken("default"))
                .when().post("/api/workflows/nonexistent/cancel")
                .then()
                .statusCode(404);
    }
}
