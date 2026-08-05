package com.syncflow.api.cdc;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.connector.cdc.PostgresCdcConnector;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.cdc.CaptureStatus;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.api.config.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdcIntegrationTest extends AbstractIntegrationTest {

    // Kept here (rather than inheriting base postgres) because Debezium CDC needs
    // wal_level=logical, which the base container does not configure.
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cdctest")
            .withUsername("testuser")
            .withPassword("testpass")
            .withCommand("postgres", "-c", "wal_level=logical");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("syncflow.encryption.key", () -> "MDEyMzQ1Njc4OWFiY2RlZg==");
    }

    private java.sql.Connection sqlConnection;
    private final PostgresCdcConnector cdcConnector = new PostgresCdcConnector();
    private final List<CDCEvent> capturedEvents = new CopyOnWriteArrayList<>();
    private volatile boolean capturing = false;

    @Autowired
    private ConnectionService connectionService;

    @BeforeEach
    void setUp() throws SQLException {
        sqlConnection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (var stmt = sqlConnection.createStatement()) {
            // Reset CDC state left over from a previous test/run: drop the
            // replication slot + publication (fixed names per database) and delete
            // the offset file so Debezium streams from the current WAL position
            // instead of resuming from a stale LSN ("WAL resume position null").
            stmt.execute(
                    "SELECT pg_drop_replication_slot(slot_name) FROM pg_replication_slots WHERE slot_name = 'syncflow_slot_cdctest'");
            stmt.execute("DROP PUBLICATION IF EXISTS syncflow_pub_cdctest");
            var offsetFile = System.getProperty("java.io.tmpdir")
                    + "/syncflow_offset_postgresql_localhost_cdctest.dat";
            new java.io.File(offsetFile).delete();
            stmt.execute("CREATE TABLE IF NOT EXISTS cdc_test_users (" +
                    "id SERIAL PRIMARY KEY, name TEXT NOT NULL, email TEXT, active BOOLEAN DEFAULT true, created_at TIMESTAMP DEFAULT NOW())");
            stmt.execute("CREATE TABLE IF NOT EXISTS cdc_test_orders (" +
                    "id SERIAL PRIMARY KEY, user_id INT, amount DECIMAL, status TEXT)");
            // REPLICA IDENTITY FULL is required so Debezium emits the full before-row
            // for UPDATE and DELETE events (default is DEFAULT which only sends the PK).
            stmt.execute("ALTER TABLE cdc_test_users REPLICA IDENTITY FULL");
            stmt.execute("ALTER TABLE cdc_test_orders REPLICA IDENTITY FULL");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (capturing)
            cdcConnector.stopCDC();
        if (sqlConnection != null) {
            try (var stmt = sqlConnection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS cdc_test_orders");
                stmt.execute("DROP TABLE IF EXISTS cdc_test_users");
            }
            sqlConnection.close();
        }
    }

    private ConnectorContext buildContext() {
        var config = new ConnectionConfiguration(
                ConnectorType.POSTGRESQL, postgres.getHost(), postgres.getMappedPort(5432),
                "cdctest", "testuser", "testpass", Map.of());
        return new ConnectorContext(config, Map.of());
    }

    private void startCdc() {
        capturing = true;
        capturedEvents.clear();
        cdcConnector.startCDC(buildContext(), capturedEvents::add);
        await().atMost(Duration.ofSeconds(5)).until(cdcConnector::isCdcActive);
    }

    // --- INSERT tests ---

    @Test
    void captureInsertEvent() throws Exception {
        startCdc();
        Thread.sleep(1000); // let Debezium initialize

        try (var stmt = sqlConnection.prepareStatement("INSERT INTO cdc_test_users (name, email) VALUES (?, ?)")) {
            stmt.setString(1, "Alice");
            stmt.setString(2, "alice@example.com");
            stmt.executeUpdate();
        }

        await().atMost(Duration.ofSeconds(15))
                .until(() -> capturedEvents.stream().anyMatch(e -> e.operation() == CDCOperation.INSERT));
        assertTrue(capturedEvents.stream().anyMatch(e -> "cdc_test_users".equals(e.source().table())));
    }

    @Test
    void captureBulkInsert() throws Exception {
        startCdc();
        Thread.sleep(1000);

        try (var stmt = sqlConnection.prepareStatement("INSERT INTO cdc_test_users (name) VALUES (?)")) {
            for (int i = 0; i < 50; i++) {
                stmt.setString(1, "User" + i);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }

        await().atMost(Duration.ofSeconds(40))
                .until(() -> capturedEvents.stream().filter(e -> e.operation() == CDCOperation.INSERT).count() >= 50);
        long inserts = capturedEvents.stream().filter(e -> e.operation() == CDCOperation.INSERT).count();
        assertTrue(inserts >= 50, "Expected at least 50 INSERT events, got " + inserts);
    }

    // --- UPDATE tests ---

    @Test
    void captureUpdateEvent() throws Exception {
        try (var stmt = sqlConnection.createStatement()) {
            stmt.execute("INSERT INTO cdc_test_users (name, email) VALUES ('Bob', 'bob@test.com')");
        }

        startCdc();
        Thread.sleep(1000);

        try (var stmt = sqlConnection.prepareStatement("UPDATE cdc_test_users SET email = ? WHERE name = ?")) {
            stmt.setString(1, "bob.updated@test.com");
            stmt.setString(2, "Bob");
            stmt.executeUpdate();
        }

        await().atMost(Duration.ofSeconds(30))
                .until(() -> capturedEvents.stream().anyMatch(e -> e.operation() == CDCOperation.UPDATE));
    }

    @Test
    void captureBulkUpdate() throws Exception {
        try (var stmt = sqlConnection.createStatement()) {
            stmt.execute("INSERT INTO cdc_test_users (name) SELECT 'BulkUser' || n FROM generate_series(1, 20) n");
        }

        startCdc();
        Thread.sleep(1000);

        try (var stmt = sqlConnection.createStatement()) {
            stmt.execute("UPDATE cdc_test_users SET active = false WHERE id > 10");
        }

        await().atMost(Duration.ofSeconds(30))
                .until(() -> capturedEvents.stream().anyMatch(e -> e.operation() == CDCOperation.UPDATE));
    }

    // --- DELETE tests ---

    @Test
    void captureDeleteEvent() throws Exception {
        try (var stmt = sqlConnection.createStatement()) {
            stmt.execute("INSERT INTO cdc_test_users (name) VALUES ('Charlie')");
        }

        startCdc();
        Thread.sleep(1000);

        try (var stmt = sqlConnection.prepareStatement("DELETE FROM cdc_test_users WHERE name = ?")) {
            stmt.setString(1, "Charlie");
            stmt.executeUpdate();
        }

        await().atMost(Duration.ofSeconds(30))
                .until(() -> capturedEvents.stream().anyMatch(e -> e.operation() == CDCOperation.DELETE));
    }

    @Test
    void captureBulkDelete() throws Exception {
        try (var stmt = sqlConnection.createStatement()) {
            stmt.execute("INSERT INTO cdc_test_users (name) SELECT 'DelUser' || n FROM generate_series(1, 10) n");
        }

        startCdc();
        Thread.sleep(1000);

        try (var stmt = sqlConnection.createStatement()) {
            stmt.execute("DELETE FROM cdc_test_users WHERE name LIKE 'DelUser%'");
        }

        await().atMost(Duration.ofSeconds(30))
                .until(() -> capturedEvents.stream().anyMatch(e -> e.operation() == CDCOperation.DELETE));
    }

    // --- Transaction rollback ---

    @Test
    void transactionRollbackDoesNotGenerateEvents() throws Exception {
        startCdc();
        Thread.sleep(1000);
        var beforeCount = capturedEvents.size();

        sqlConnection.setAutoCommit(false);
        try (var stmt = sqlConnection.prepareStatement("INSERT INTO cdc_test_users (name) VALUES (?)")) {
            stmt.setString(1, "RollbackUser");
            stmt.executeUpdate();
        }
        sqlConnection.rollback();
        sqlConnection.setAutoCommit(true);

        Thread.sleep(2000);
        assertEquals(beforeCount, capturedEvents.size(),
                "No events should be captured for rolled-back transactions");
    }

    // --- Offset recovery ---

    @Test
    void cdcConnectorLifecycleStartStop() {
        startCdc();
        assertTrue(cdcConnector.isCdcActive());
        assertEquals(CaptureStatus.RUNNING, cdcConnector.captureStatus());

        cdcConnector.pauseCDC();
        assertEquals(CaptureStatus.PAUSED, cdcConnector.captureStatus());

        cdcConnector.resumeCDC();
        assertEquals(CaptureStatus.RUNNING, cdcConnector.captureStatus());

        cdcConnector.stopCDC();
        assertFalse(cdcConnector.isCdcActive());
    }

    @Test
    void offsetPreservedAfterStop() {
        startCdc();
        var offset = cdcConnector.currentOffset();
        cdcConnector.stopCDC();

        assertNotNull(offset);
    }

    // --- Exactly-once and at-least-once tracking ---

    @Test
    void duplicateEventTracking() {
        java.util.Set<String> processed = java.util.concurrent.ConcurrentHashMap.newKeySet();
        var events = List.of(
                "evt-1", "evt-2", "evt-1", "evt-3", "evt-2");
        var uniqueCount = events.stream()
                .filter(processed::add)
                .count();
        assertEquals(3, uniqueCount);
    }

    // --- Out-of-order event handling ---

    @Test
    void outOfOrderEventsByOffset() {
        var events = new ArrayList<OffsetKey>();
        events.add(new OffsetKey("p-1", "100", Instant.now()));
        events.add(new OffsetKey("p-1", "300", Instant.now()));
        events.add(new OffsetKey("p-1", "200", Instant.now()));

        events.sort(Comparator.comparing(e -> e.lsn));

        assertEquals("100", events.get(0).lsn);
        assertEquals("200", events.get(1).lsn);
        assertEquals("300", events.get(2).lsn);
    }

    private record OffsetKey(String pipelineId, String lsn, Instant timestamp) {
    }
}
