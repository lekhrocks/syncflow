package com.syncflow.api;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
class IntegrationTest {

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("syncflow.encryption.key", () -> "MDEyMzQ1Njc4OWFiY2RlZg==");
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Nested
    @DisplayName("Health API Contract")
    class HealthContract {

        @Test
        void healthEndpointReturns200() {
            given()
                    .when().get("/api/health")
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("UP"))
                    .body("$", hasKey("connectors"))
                    .body("$", hasKey("timestamp"));
        }

        @Test
        void actuatorHealthReturnsProbes() {
            given()
                    .when().get("/actuator/health")
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("UP"));
        }
    }

    @Nested
    @DisplayName("Connection API Contract")
    class ConnectionContract {

        private String createdId;

        @Test
        @DisplayName("POST /connections -> 201 with body")
        void createConnectionReturns201() {
            createdId = given()
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "name", "integration-test-conn",
                            "connectionType", "POSTGRESQL",
                            "host", "localhost",
                            "port", 5432,
                            "database", "testdb",
                            "username", "testuser",
                            "password", "testpass"))
                    .when().post("/api/connections")
                    .then()
                    .statusCode(201)
                    .body("name", equalTo("integration-test-conn"))
                    .body("connectionType", equalTo("POSTGRESQL"))
                    .body("$", hasKey("id"))
                    .body("$", hasKey("createdAt"))
                    .extract().path("id");
        }

        @Test
        @DisplayName("GET /connections -> 200 with array")
        void listConnectionsReturns200() {
            given()
                    .when().get("/api/connections")
                    .then()
                    .statusCode(200)
                    .body("$", isA(List.class));
        }

        @Test
        @DisplayName("POST /connections/test -> 200 with result")
        void testConnectionReturns200() {
            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "connectionType", "POSTGRESQL",
                            "host", "localhost",
                            "port", 5432,
                            "database", "testdb",
                            "username", "testuser",
                            "password", "testpass"))
                    .when().post("/api/connections/test")
                    .then()
                    .statusCode(200)
                    .body("$", hasKey("success"));
        }

        @Test
        @DisplayName("POST /connections -> 400 on missing required fields")
        void createConnectionValidatesRequiredFields() {
            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("name", "incomplete"))
                    .when().post("/api/connections")
                    .then()
                    .statusCode(400)
                    .body("code", equalTo("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("Pipeline API Contract")
    class PipelineContract {

        @Test
        @DisplayName("GET /pipelines -> 200 with array")
        void listPipelines() {
            given()
                    .when().get("/api/pipelines")
                    .then()
                    .statusCode(200)
                    .body("$", isA(List.class));
        }

        @Test
        @DisplayName("POST /pipelines -> 201")
        void createPipeline() {
            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "name", "integration-pipeline",
                            "sourceConnectionId", "source-1",
                            "sourceSchema", "public",
                            "sourceTable", "users",
                            "destConnectionId", "dest-1",
                            "destSchema", "public",
                            "destTable", "users_copy"))
                    .when().post("/api/pipelines")
                    .then()
                    .statusCode(201)
                    .body("name", equalTo("integration-pipeline"));
        }
    }

    @Nested
    @DisplayName("Dashboard API Contract")
    class DashboardContract {

        @Test
        @DisplayName("GET /dashboard/overview -> 200 with structure")
        void overview() {
            given()
                    .when().get("/api/dashboard/overview")
                    .then()
                    .statusCode(200)
                    .body("$", hasKey("pipelines"))
                    .body("$", hasKey("connections"))
                    .body("$", hasKey("connectors"))
                    .body("$", hasKey("snapshots"))
                    .body("$", hasKey("syncJobs"))
                    .body("$", hasKey("alerts"))
                    .body("$", hasKey("dlq"));
        }
    }

    @Nested
    @DisplayName("Error Response Contract")
    class ErrorContract {

        @Test
        @DisplayName("Unknown endpoint returns 404")
        void unknownEndpoint() {
            given()
                    .when().get("/api/nonexistent")
                    .then()
                    .statusCode(404);
        }

        @Test
        @DisplayName("Invalid body returns 400 with validation error")
        void invalidBody() {
            given()
                    .contentType(ContentType.JSON)
                    .body("not-json")
                    .when().post("/api/connections")
                    .then()
                    .statusCode(400);
        }
    }

    @Nested
    @DisplayName("Diagnostics API Contract")
    class DiagnosticsContract {

        @Test
        @DisplayName("GET /diagnostics/system -> 200 with JVM info")
        void systemInfo() {
            given()
                    .when().get("/api/diagnostics/system")
                    .then()
                    .statusCode(200)
                    .body("$", hasKey("jvm"))
                    .body("$", hasKey("os"))
                    .body("$", hasKey("java"))
                    .body("jvm", hasKey("availableProcessors"))
                    .body("jvm", hasKey("threadCount"))
                    .body("jvm", hasKey("virtualThreadCount"));
        }
    }
}
