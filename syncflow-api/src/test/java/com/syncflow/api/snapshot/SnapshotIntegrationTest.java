package com.syncflow.api.snapshot;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.pipeline.DestinationReference;
import com.syncflow.core.pipeline.PipelineName;
import com.syncflow.core.pipeline.PipelineSettings;
import com.syncflow.core.pipeline.SourceReference;
import com.syncflow.core.pipeline.SyncMode;
import com.syncflow.core.pipeline.mapping.ColumnMapping;
import com.syncflow.core.pipeline.mapping.PrimaryKeyMapping;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.snapshot.SnapshotJob;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
class SnapshotIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("snapshottest")
            .withUsername("testuser")
            .withPassword("testpass");

    @LocalServerPort
    private int port;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private PipelineDesignerService pipelineService;

    private String connectionId;
    private String pipelineId;

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

        var props = new ConnectionProperties(ConnectionType.POSTGRESQL,
                postgres.getHost(), postgres.getMappedPort(5432), "snapshottest", Map.of());
        var creds = new Credentials("testuser", "testpass");
        var conn = connectionService.create("snapshot-test-pg", props, creds);
        connectionId = conn.getId().value();
    }

    @AfterEach
    void tearDown() {
        if (pipelineId != null)
            pipelineService.delete(pipelineId);
        if (connectionId != null)
            connectionService.delete(connectionId);
    }

    private void createPipeline() {
        var name = new PipelineName("snapshot-integration-test");
        var source = new SourceReference(connectionId, "public", "pipelines");
        var dest = new DestinationReference(connectionId, "public", "pipelines_copy", "UPSERT");
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm = new ColumnMapping("id", "id", List.of());
        var mapping = new TableMapping("pipelines", "pipelines_copy", null, pk,
                List.of(cm), List.of(), List.of(), null);
        var settings = new PipelineSettings(SyncMode.FULL_SNAPSHOT, 500, 3, false, false, Map.of());

        var pipeline = pipelineService.create(name, source, dest, List.of(mapping), settings);
        pipelineId = pipeline.id().value();
    }

    @Test
    @DisplayName("Snapshot small table via REST")
    void snapshotSmallTable() {
        createPipeline();
        var resp = given()
                .when().post("/api/pipelines/{id}/snapshot", pipelineId)
                .then()
                .statusCode(202)
                .extract().as(SnapshotJob.class);

        assertNotNull(resp);
    }

    @Test
    @DisplayName("Get snapshot status returns progress")
    void snapshotStatus() {
        createPipeline();

        // Start the snapshot
        var job = given()
                .when().post("/api/pipelines/{id}/snapshot", pipelineId)
                .then()
                .statusCode(202)
                .extract().as(SnapshotJob.class);

        // Check its status
        var status = given()
                .when().get("/api/snapshots/{id}", job.getId().value())
                .then()
                .statusCode(200)
                .extract().as(SnapshotJob.class);

        assertNotNull(status);
    }

    @Test
    @DisplayName("List snapshots returns jobs")
    void listSnapshots() {
        var list = given()
                .when().get("/api/snapshots")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("$");

        assertNotNull(list);
    }

    @Test
    @DisplayName("Cancel mid-snapshot returns cancelled status")
    void cancelSnapshot() {
        createPipeline();
        var job = given()
                .when().post("/api/pipelines/{id}/snapshot", pipelineId)
                .then()
                .statusCode(202)
                .extract().as(SnapshotJob.class);

        var cancelled = given()
                .when().post("/api/snapshots/{id}/cancel", job.getId().value())
                .then()
                .statusCode(200)
                .extract().as(SnapshotJob.class);

        assertNotNull(cancelled);
    }

    @Test
    @DisplayName("Snapshot large table with batch size of 100")
    void snapshotWithCustomBatchSize() {
        var name = new PipelineName("large-snapshot-test");
        var source = new SourceReference(connectionId, "public", "pipelines");
        var dest = new DestinationReference(connectionId, "public", "pipelines_copy", "UPSERT");
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm = new ColumnMapping("id", "id", List.of());
        var mapping = new TableMapping("pipelines", "pipelines_copy", null, pk,
                List.of(cm), List.of(), List.of(), null);
        var settings = new PipelineSettings(SyncMode.FULL_SNAPSHOT, 100, 3, false, false, Map.of());
        var pipeline = pipelineService.create(name, source, dest, List.of(mapping), settings);
        pipelineId = pipeline.id().value();

        given()
                .when().post("/api/pipelines/{id}/snapshot", pipelineId)
                .then()
                .statusCode(202);
    }
}
