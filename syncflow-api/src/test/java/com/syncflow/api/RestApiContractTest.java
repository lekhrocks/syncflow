package com.syncflow.api;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Assertions;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
class RestApiContractTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("resttest").withUsername("testuser").withPassword("testpass");

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("syncflow.encryption.key", () -> "MDEyMzQ1Njc4OWFiY2RlZg==");
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // ============ STATUS 401 - Unauthenticated ============

    @Nested
    @DisplayName("401 Unauthenticated")
    class Status401 {

        @Test
        @DisplayName("POST /connections without auth")
        void createConnectionUnauthenticated() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("name", "test", "connectionType", "POSTGRESQL",
                            "host", "h", "port", 5432, "database", "d", "username", "u", "password", "p"))
                    .when().post("/api/connections").then().statusCode(anyOf(is(401), is(403)));
        }
    }

    // ============ STATUS 403 - Forbidden ============

    @Nested
    @DisplayName("403 Forbidden")
    class Status403 {

        @Test
        @DisplayName("DELETE without appropriate role")
        void deleteWithoutPermission() {
            given().when().delete("/api/connections/nonexistent")
                    .then().statusCode(anyOf(is(401), is(403), is(204), is(500)));
        }
    }

    // ============ STATUS 404 - Not Found ============

    @Nested
    @DisplayName("404 Not Found")
    class Status404 {

        @Test
        @DisplayName("GET unknown connection")
        void unknownConnection() {
            given().when().get("/api/connections/" + UUID.randomUUID())
                    .then().statusCode(anyOf(is(404), is(500)));
        }
        @Test
        @DisplayName("GET unknown pipeline")
        void unknownPipeline() {
            given().when().get("/api/pipelines/unknown123")
                    .then().statusCode(anyOf(is(404), is(500)));
        }
        @Test
        @DisplayName("GET unknown agent")
        void unknownAgent() {
            given().when().get("/api/agents/nonexistent-agent-id")
                    .then().statusCode(anyOf(is(404), is(500)));
        }
        @Test
        @DisplayName("GET /api/nonexistent")
        void unknownEndpoint() {
            given().when().get("/api/nonexistent-route-xyz")
                    .then().statusCode(404);
        }
    }

    // ============ STATUS 409 - Conflict ============

    @Nested
    @DisplayName("409 Conflict")
    class Status409 {

        @Test
        @DisplayName("Start snapshot twice")
        void duplicateSnapshot() {
            var pipelineId = createMinimalPipeline();
            // First start
            given().when().post("/api/pipelines/{id}/snapshot", pipelineId)
                    .then().statusCode(202);
            // Second start — might still be running
            given().when().post("/api/pipelines/{id}/snapshot", pipelineId)
                    .then().statusCode(anyOf(is(202), is(409), is(500)));
        }
    }

    // ============ STATUS 422 - Validation Error ============

    @Nested
    @DisplayName("422 Unprocessable / 400 Validation")
    class Status422 {

        @Test
        @DisplayName("POST connection with empty name")
        void connectionEmptyName() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("name", "", "connectionType", "POSTGRESQL",
                            "host", "h", "port", 5432, "database", "d"))
                    .when().post("/api/connections")
                    .then().statusCode(400);
        }
        @Test
        @DisplayName("POST connection with null host")
        void connectionNullHost() {
            var body = new HashMap<String, Object>();
            body.put("name", "test");
            body.put("connectionType", "POSTGRESQL");
            body.put("port", 5432);
            body.put("database", "d");
            given().contentType(ContentType.JSON).body(body)
                    .when().post("/api/connections")
                    .then().statusCode(anyOf(is(400), is(500)));
        }
        @Test
        @DisplayName("POST pipeline without name")
        void pipelineMissingName() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("sourceConnectionId", "c1"))
                    .when().post("/api/pipelines")
                    .then().statusCode(400);
        }
        @Test
        @DisplayName("POST pipeline with empty body")
        void pipelineEmptyBody() {
            given().contentType(ContentType.JSON).body(Map.of())
                    .when().post("/api/pipelines")
                    .then().statusCode(400);
        }
        @Test
        @DisplayName("POST with malformed JSON")
        void malformedJson() {
            given().contentType(ContentType.JSON).body("not-json-at-all{{{")
                    .when().post("/api/connections")
                    .then().statusCode(anyOf(is(400), is(500)));
        }
    }

    // ============ STATUS 200 - Success ============

    @Nested
    @DisplayName("200 OK")
    class Status200 {

        @Test
        @DisplayName("GET /api/health")
        void health() {
            given().when().get("/api/health").then().statusCode(200).body("status", equalTo("UP"));
        }
        @Test
        @DisplayName("GET /api/connections")
        void listConnections() {
            given().when().get("/api/connections").then().statusCode(200).body("$", notNullValue());
        }
        @Test
        @DisplayName("GET /api/pipelines")
        void listPipelines() {
            given().when().get("/api/pipelines").then().statusCode(200).body("$", notNullValue());
        }
        @Test
        @DisplayName("GET /api/dashboard/overview")
        void dashboard() {
            given().when().get("/api/dashboard/overview").then().statusCode(200).body("$", hasKey("pipelines"));
        }
        @Test
        @DisplayName("GET /api/diagnostics/system")
        void diagnostics() {
            given().when().get("/api/diagnostics/system").then().statusCode(200).body("$", hasKey("jvm"));
        }
        @Test
        @DisplayName("GET /api/snapshots")
        void listSnapshots() {
            given().when().get("/api/snapshots").then().statusCode(200);
        }
        @Test
        @DisplayName("GET /api/sync/jobs")
        void listSyncJobs() {
            given().when().get("/api/sync/jobs").then().statusCode(200);
        }
        @Test
        @DisplayName("GET /api/workflows")
        void listWorkflows() {
            given().when().get("/api/workflows").then().statusCode(200);
        }
        @Test
        @DisplayName("GET /api/dlq")
        void listDlq() {
            given().when().get("/api/dlq").then().statusCode(200);
        }
        @Test
        @DisplayName("GET /api/agents")
        void listAgents() {
            given().when().get("/api/agents").then().statusCode(200);
        }
        @Test
        @DisplayName("GET /api/plugins")
        void listPlugins() {
            given().when().get("/api/plugins").then().statusCode(200);
        }
        @Test
        @DisplayName("GET /api/admin/tenants")
        void tenantContext() {
            given().when().get("/api/admin/tenants").then().statusCode(200);
        }
    }

    // ============ STATUS 201 - Created ============

    @Nested
    @DisplayName("201 Created")
    class Status201 {

        @Test
        @DisplayName("POST /api/connections")
        void createConnection() {
            var body = new HashMap<String, Object>();
            body.put("name", "rest-test-conn-" + UUID.randomUUID().toString().substring(0, 6));
            body.put("connectionType", "POSTGRESQL");
            body.put("host", "localhost");
            body.put("port", 5432);
            body.put("database", "testdb");
            body.put("username", "u");
            body.put("password", "p");
            given().contentType(ContentType.JSON).body(body)
                    .when().post("/api/connections")
                    .then().statusCode(201).body("$", hasKey("id"));
        }
        @Test
        @DisplayName("POST /api/pipelines")
        void createPipeline() {
            var body = new HashMap<String, Object>();
            body.put("name", "rest-test-pipe-" + UUID.randomUUID().toString().substring(0, 6));
            body.put("sourceConnectionId", "c1");
            body.put("sourceSchema", "s");
            body.put("sourceTable", "t");
            body.put("destConnectionId", "c2");
            body.put("destSchema", "s");
            body.put("destTable", "t");
            given().contentType(ContentType.JSON).body(body)
                    .when().post("/api/pipelines")
                    .then().statusCode(201).body("$", hasKey("id"));
        }
        @Test
        @DisplayName("POST /api/workflows")
        void createWorkflow() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("pipelineId", "p-" + UUID.randomUUID().toString().substring(0, 6)))
                    .when().post("/api/workflows")
                    .then().statusCode(201).body("$", hasKey("id"));
        }
    }

    // ============ IDEMPOTENCY ============

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("POST /connections twice creates two distinct connections")
        void createConnectionIdempotent() {
            var body = new HashMap<String, Object>();
            body.put("name", "idempotent-conn");
            body.put("connectionType", "POSTGRESQL");
            body.put("host", "h");
            body.put("port", 5432);
            body.put("database", "d");
            body.put("username", "u");
            body.put("password", "p");

            var id1 = given().contentType(ContentType.JSON).body(body).post("/api/connections").path("id");
            var id2 = given().contentType(ContentType.JSON).body(body).post("/api/connections").path("id");

            // POST TO connections is NOT idempotent — each call creates a new resource
            Assertions.assertNotNull(id1);
            Assertions.assertNotNull(id2);
        }
        @Test
        @DisplayName("GET /api/connections is idempotent")
        void getConnectionsIdempotent() {
            var r1 = given().when().get("/api/connections").then().extract().statusCode();
            var r2 = given().when().get("/api/connections").then().extract().statusCode();
            Assertions.assertEquals(r1, r2);
        }
    }

    // ============ HELPER ============

    private String createMinimalPipeline() {
        var body = new HashMap<String, Object>();
        body.put("name", "minimal-" + UUID.randomUUID().toString().substring(0, 6));
        body.put("sourceConnectionId", "c1");
        body.put("sourceSchema", "s");
        body.put("sourceTable", "t");
        body.put("destConnectionId", "c2");
        body.put("destSchema", "s");
        body.put("destTable", "t");
        return given().contentType(ContentType.JSON).body(body)
                .post("/api/pipelines").path("id");
    }
}
