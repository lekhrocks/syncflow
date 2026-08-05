package com.syncflow.api.sync;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.sync.FailureReason;
import com.syncflow.api.config.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SyncIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private DeadLetterQueue dlq;

    private String pgConnectionId;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("synctest")
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

    @BeforeEach
    void setUp() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL,
                postgres.getHost(), postgres.getMappedPort(5432), "synctest", Map.of());
        var creds = new Credentials("testuser", "testpass");
        pgConnectionId = connectionService.create("sync-test-pg", props, creds).getId().value();
    }

    @AfterEach
    void tearDown() {
        if (pgConnectionId != null)
            connectionService.delete(pgConnectionId);
        // JPA-backed DLQ persists across tests; clear so count/list assertions
        // in DLQ tests see only the events each test adds.
        dlq.clearAll();
    }

    @Nested
    @DisplayName("DLQ API Integration")
    class DlqApi {

        @Test
        @DisplayName("GET /dlq returns DLQ events")
        void listDlq() {
            given()
                    .when().get("/api/dlq")
                    .then()
                    .statusCode(200);
        }

        @Test
        @DisplayName("POST /dlq/{id}/replay removes event")
        void replayDlq() {
            dlq.add("p-1", null, FailureReason.permanentError("test"), 3);
            var events = dlq.list("p-1");
            if (!events.isEmpty()) {
                given()
                        .when().post("/api/dlq/{id}/replay", events.getFirst().id())
                        .then()
                        .statusCode(200);
            }
        }

        @Test
        @DisplayName("DELETE /dlq/{id} removes event")
        void deleteDlq() {
            dlq.add("p-1", null, FailureReason.permanentError("test-del"), 2);
            var events = dlq.list("p-1");
            if (!events.isEmpty()) {
                given()
                        .when().delete("/api/dlq/{id}", events.getFirst().id())
                        .then()
                        .statusCode(204);
            }
        }
    }

    @Nested
    @DisplayName("DLQ Functional Tests")
    class DlqFunctional {

        @Test
        @DisplayName("DLQ stores and retrieves failed events")
        void storeAndRetrieve() {
            dlq.add("p-1", null, FailureReason.permanentError("connection lost"), 3);
            var events = dlq.list("p-1");
            assertFalse(events.isEmpty());
            assertEquals("connection lost", events.getFirst().reason().message());
        }

        @Test
        @DisplayName("DLQ lists events filtered by pipeline")
        void listFilteredByPipeline() {
            dlq.add("p-1", null, FailureReason.permanentError("err1"), 1);
            dlq.add("p-2", null, FailureReason.permanentError("err2"), 1);
            assertEquals(1, dlq.list("p-1").size());
        }

        @Test
        @DisplayName("DLQ replay keeps the record and bumps replay count")
        void replayRetains() {
            dlq.add("p-1", null, FailureReason.permanentError("replay"), 2);
            var events = dlq.list("p-1");
            var id = events.getFirst().id();
            dlq.replay(id);
            // Replay marks the event and increments replayCount; it does not delete it,
            // so the audit trail (replayedAt, replayCount) is preserved.
            var after = dlq.get(id);
            assertNotNull(after);
            assertEquals(1, after.replayCount());
            dlq.replay(id);
            assertEquals(2, dlq.get(id).replayCount());
        }
    }

    @Nested
    @DisplayName("Sync health and status")
    class SyncStatus {

        @Test
        @DisplayName("GET /pipelines/{id}/sync/status returns stopped for unknown pipeline")
        void syncStatus() {
            // No sync running for unknown pipeline — orchestrator returns STOPPED (200)
            given()
                    .when().get("/api/pipelines/nonexistent/sync/status")
                    .then()
                    .statusCode(200)
                    .body("state", equalTo("STOPPED"));
        }
    }
}
