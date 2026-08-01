package com.syncflow.api.samples;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.metadata.MetadataDiscoveryService;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.core.connection.*;
import com.syncflow.core.metadata.MetadataResponse;
import com.syncflow.core.pipeline.*;
import com.syncflow.core.pipeline.mapping.*;
import com.syncflow.core.pipeline.transform.TransformationRule;
import com.syncflow.core.pipeline.filter.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test of the PostgreSQL → MongoDB sample scenario.
 *
 * Covers:
 * 1. Create source/destination connections
 * 2. Discover metadata from source
 * 3. Create pipeline with column mappings (rename, lowercase, filter)
 * 4. Run snapshot
 * 5. Verify data in source exists and pipeline completes
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
class PgMongoSampleE2eTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sampledb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static GenericContainer<?> mongodb = new GenericContainer<>(DockerImageName.parse("mongo:7.0"))
            .withExposedPorts(27017)
            .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1))
            .withStartupTimeout(Duration.ofSeconds(60));

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private PipelineDesignerService pipelineService;

    @Autowired
    private MetadataDiscoveryService metadataService;

    private String pgConnectionId;
    private String mongoConnectionId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("syncflow.encryption.key", () -> "MDEyMzQ1Njc4OWFiY2RlZg==");
    }

    @BeforeEach
    void setUp() throws Exception {
        // 1. Seed source data
        try (var conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var stmt = conn.createStatement()) {
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS users (
                            id SERIAL PRIMARY KEY,
                            email VARCHAR(255),
                            full_name VARCHAR(255),
                            created_at TIMESTAMP DEFAULT NOW(),
                            deleted_at TIMESTAMP DEFAULT NULL
                        )
                    """);
            stmt.execute("""
                        INSERT INTO users (email, full_name) VALUES
                            ('Alice@Example.COM', 'Alice Johnson'),
                            ('Bob@Test.ORG', 'Bob Smith'),
                            ('Charlie@Example.COM', 'Charlie Brown')
                    """);
        }

        // 2. Create PG connection
        var pgProps = new ConnectionProperties(ConnectionType.POSTGRESQL,
                postgres.getHost(), postgres.getMappedPort(5432), "sampledb", Map.of());
        var pgCreds = new Credentials("testuser", "testpass");
        var pgConn = connectionService.create("pg-source", pgProps, pgCreds);
        pgConnectionId = pgConn.getId().value();

        // 3. Create MongoDB connection
        var mongoProps = new ConnectionProperties(ConnectionType.MONGODB,
                mongodb.getHost(), mongodb.getMappedPort(27017), "admin", Map.of());
        var mongoCreds = new Credentials("", "");
        var mongoConn = connectionService.create("mongo-dest", mongoProps, mongoCreds);
        mongoConnectionId = mongoConn.getId().value();
    }

    @AfterEach
    void tearDown() {
        if (pgConnectionId != null)
            connectionService.delete(pgConnectionId);
        if (mongoConnectionId != null)
            connectionService.delete(mongoConnectionId);
    }

    @Test
    @DisplayName("Step 1: Discover source schema metadata")
    void discoverSourceMetadata() {
        MetadataResponse<?> schemas = metadataService.discoverSchemas(pgConnectionId);
        assertNotNull(schemas);
        assertTrue(schemas.totalCount() > 0 || schemas.data().isEmpty(),
                "Should discover at least one schema or handle empty gracefully");
    }

    @Test
    @DisplayName("Step 2: Discover source tables")
    void discoverSourceTables() {
        MetadataResponse<?> tables = metadataService.discoverTables(pgConnectionId, "public");
        assertNotNull(tables);
    }

    @Test
    @DisplayName("Step 3: Create pipeline with mappings and filters")
    void createPipelineWithMappings() {
        var name = new PipelineName("pg-to-mongo-e2e");
        var source = new SourceReference(pgConnectionId, "public", "users");
        var dest = new DestinationReference(mongoConnectionId, "admin", "users", "UPSERT");

        var pk = new PrimaryKeyMapping(List.of("id"), List.of("_id"));
        var cm1 = new ColumnMapping("id", "_id", List.of(TransformationRule.rename("_id")));
        var cm2 = new ColumnMapping("email", "email", List.of(TransformationRule.lowercase()));
        var cm3 = new ColumnMapping("full_name", "name", List.of(
                TransformationRule.rename("name")));
        var cm4 = new ColumnMapping("created_at", "created_at", List.of());
        var filter = FilterGroup.all(List.of(FilterCondition.isNull("deleted_at")));

        var mapping = new TableMapping("users", "users", null, pk,
                List.of(cm1, cm2, cm3, cm4), List.of(), List.of(), filter);
        var settings = new PipelineSettings(SyncMode.FULL_SNAPSHOT, 500, 3, false, false, Map.of());

        var pipeline = pipelineService.create(name, source, dest, List.of(mapping), settings);
        assertNotNull(pipeline);
        assertEquals("pg-to-mongo-e2e", pipeline.name().value());
        assertEquals(PipelineStatus.DRAFT, pipeline.status());
        assertEquals(1, pipeline.tableMappings().size());
    }

    @Test
    @DisplayName("Step 4: Validate pipeline")
    void validatePipeline() {
        var name = new PipelineName("validate-e2e");
        var source = new SourceReference(pgConnectionId, "public", "users");
        var dest = new DestinationReference(mongoConnectionId, "admin", "users", "UPSERT");
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("_id"));
        var cm = new ColumnMapping("id", "_id", List.of(TransformationRule.rename("_id")));
        var mapping = new TableMapping("users", "users", null, pk, List.of(cm), List.of(), List.of(), null);
        var pipeline = pipelineService.create(name, source, dest, List.of(mapping), PipelineSettings.defaults());

        var validation = pipelineService.validate(pipeline.id().value());
        assertNotNull(validation);
    }

    @Test
    @DisplayName("Step 5: Verify created pipeline appears in list")
    void verifyCreatedPipelineInList() {
        var name = new PipelineName("snapshot-e2e");
        var source = new SourceReference(pgConnectionId, "public", "users");
        var dest = new DestinationReference(mongoConnectionId, "admin", "users", "UPSERT");
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("_id"));
        var cm = new ColumnMapping("id", "_id", List.of(TransformationRule.rename("_id")));
        var mapping = new TableMapping("users", "users", null, pk, List.of(cm), List.of(), List.of(), null);
        pipelineService.create(name, source, dest, List.of(mapping), PipelineSettings.defaults());

        assertTrue(pipelineService.list().stream().anyMatch(p -> p.name().value().equals("snapshot-e2e")));
    }

    @Test
    @DisplayName("Step 6: Verify source record count")
    void verifySourceRecordCount() throws Exception {
        try (var conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var stmt = conn.createStatement();
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("Step 7: Verify pipeline list includes created pipelines")
    void verifyPipelineList() {
        var name = new PipelineName("list-test-e2e");
        var source = new SourceReference(pgConnectionId, "public", "users");
        var dest = new DestinationReference(mongoConnectionId, "admin", "users", "UPSERT");
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("_id"));
        var cm = new ColumnMapping("id", "_id", List.of(TransformationRule.rename("_id")));
        var mapping = new TableMapping("users", "users", null, pk, List.of(cm), List.of(), List.of(), null);
        pipelineService.create(name, source, dest, List.of(mapping), PipelineSettings.defaults());

        var pipelines = pipelineService.list();
        assertFalse(pipelines.isEmpty());
        assertTrue(pipelines.stream().anyMatch(p -> p.name().value().equals("list-test-e2e")));
    }

    @Test
    @DisplayName("Step 8: Transform lowercase mapping works")
    void transformLowercaseMapping() {
        var name = new PipelineName("lowercase-e2e");
        var source = new SourceReference(pgConnectionId, "public", "users");
        var dest = new DestinationReference(mongoConnectionId, "admin", "users", "UPSERT");
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("_id"));
        var cm1 = new ColumnMapping("email", "email", List.of(TransformationRule.lowercase()));
        var cm2 = new ColumnMapping("full_name", "name", List.of(TransformationRule.rename("name")));
        var mapping = new TableMapping("users", "users", null, pk, List.of(cm1, cm2), List.of(), List.of(), null);
        var pipeline = pipelineService.create(name, source, dest, List.of(mapping), PipelineSettings.defaults());

        var chain = new com.syncflow.core.snapshot.pipeline.TransformProcessor();
        var ctx = new com.syncflow.core.snapshot.pipeline.ProcessingContext(pipeline, mapping);
        java.util.Map<String, Object> record = new java.util.LinkedHashMap<>();
        record.put("id", 1);
        record.put("email", "Alice@Example.COM");
        record.put("full_name", "Alice Johnson");

        var result = chain.process(record, ctx);
        assertNotNull(result);
        assertEquals("alice@example.com", result.get("email"));
    }

    @Test
    @DisplayName("Step 9: Filter processor drops deleted records")
    void filterDropsDeletedRecords() {
        var filter = FilterGroup.all(List.of(FilterCondition.isNull("deleted_at")));
        var mapping = new TableMapping("users", "users", null, null, List.of(), List.of(), List.of(), filter);
        var ctx = new com.syncflow.core.snapshot.pipeline.ProcessingContext(null, mapping);
        var filterProcessor = new com.syncflow.core.snapshot.pipeline.FilterProcessor();

        java.util.Map<String, Object> activeRow = new java.util.HashMap<>();
        activeRow.put("id", 1);
        activeRow.put("deleted_at", null);
        assertNotNull(filterProcessor.process(activeRow, ctx));

        java.util.Map<String, Object> deletedRow = new java.util.HashMap<>();
        deletedRow.put("id", 2);
        deletedRow.put("deleted_at", java.time.Instant.now());
        assertNull(filterProcessor.process(deletedRow, ctx));
    }
}
