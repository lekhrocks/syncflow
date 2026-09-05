package com.syncflow.connector.cdc;

import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.storage.OffsetBackingStore;
import org.apache.kafka.connect.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Postgres-backed {@link OffsetBackingStore} for Debezium.
 * <p>
 * Persists connector offsets in the {@code debezium_offsets} table instead of
 * the ephemeral {@code /tmp} file used by {@code FileOffsetBackingStore}.
 * Offsets therefore survive pod restarts — no re-processing or missed events.
 * <p>
 * As of migration V16 the table is partitioned by {@code tenant_id}. This store
 * reads the {@code offset.storage.jdbc.tenant_id} property (set per pipeline in
 * {@link DebeziumCdcConnector}) and stamps every row with that UUID so writes
 * land in the correct partition. The ON CONFLICT target has been updated to the
 * composite key {@code (tenant_id, offset_key)}.
 * <p>
 * Configured by the properties prefixed {@code offset.storage.jdbc.*} set in
 * {@link DebeziumCdcConnector#startCDC}.
 */
public class JdbcOffsetBackingStore implements OffsetBackingStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcOffsetBackingStore.class);

    /**
     * System-tenant UUID — used for legacy rows and when no tenant is configured.
     */
    private static final String SYSTEM_TENANT = "00000000-0000-0000-0000-000000000000";

    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private String tableName = "cdc_offsets";
    private String tenantId = SYSTEM_TENANT;

    // In-memory cache of offsets read at start() so get() never hits the DB for
    // already-loaded partitions; writes are batched into set() then flushed.
    private final Map<ByteBuffer, ByteBuffer> cache = new HashMap<>();

    @Override
    public void configure(WorkerConfig config) {
        var originals = config.originalsWithPrefix("offset.storage.jdbc.");
        jdbcUrl = stringValue(originals, "url", null);
        jdbcUser = stringValue(originals, "user", "");
        jdbcPassword = stringValue(originals, "password", "");
        var table = stringValue(originals, "table.name", null);
        if (table != null) {
            tableName = table;
        }
        // tenant_id: NEW (as of V16 migration). Per-pipeline tenant isolation.
        // Defaults to system tenant if not set (for backwards compatibility with
        // pre-V16 deployments that have not yet configured per-pipeline tenant IDs).
        var tenant = stringValue(originals, "tenant_id", SYSTEM_TENANT);
        if (tenant != null) {
            tenantId = tenant;
        }
        if (jdbcUrl == null) {
            throw new IllegalStateException(
                    "offset.storage.jdbc.url is required for JdbcOffsetBackingStore");
        }
    }

    @Override
    public void start() {
        // Load all persisted offsets into memory so get() resolves without a DB
        // round trip on the CDC hot path.
        try (var conn = connection();
                var stmt = conn.createStatement();
                var rs = stmt.executeQuery(
                        "SELECT offset_key, offset_data FROM " + tableName)) {
            while (rs.next()) {
                cache.put(fromDbBytes(rs.getBytes("offset_key")), fromDbBytes(rs.getBytes("offset_data")));
            }
            log.info("Loaded {} persisted CDC offsets from {}", cache.size(), tableName);
        } catch (SQLException e) {
            // Table may not exist on a fresh database before Flyway migrates; the
            // CDC engine treats a missing store as a cold start.
            log.warn("Could not load persisted CDC offsets from {}: {}", tableName, e.getMessage());
        }
    }

    @Override
    public void stop() {
        cache.clear();
    }

    @Override
    public Future<Map<ByteBuffer, ByteBuffer>> get(Collection<ByteBuffer> keys) {
        var result = new HashMap<ByteBuffer, ByteBuffer>();
        for (var key : keys) {
            var value = cache.get(key);
            if (value != null) {
                result.put(key.duplicate(), value.duplicate());
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public Future<Void> set(Map<ByteBuffer, ByteBuffer> values, Callback<Void> callback) {
        try {
            try (var conn = connection();
                    var upsert = conn.prepareStatement(
                            "INSERT INTO " + tableName
                                    + " (tenant_id, offset_key, offset_data) VALUES (?, ?, ?) "
                                    + "ON CONFLICT (tenant_id, offset_key) DO UPDATE SET offset_data = EXCLUDED.offset_data")) {
                for (var entry : values.entrySet()) {
                    var key = entry.getKey().duplicate();
                    var value = entry.getValue() != null ? entry.getValue().duplicate() : null;
                    cache.put(key, value);
                    upsert.setObject(1, java.util.UUID.fromString(tenantId));
                    upsert.setBytes(2, toDbBytes(key));
                    upsert.setBytes(3, value != null ? toDbBytes(value) : new byte[0]);
                    upsert.addBatch();
                }
                upsert.executeBatch();
            }
            if (callback != null) {
                callback.onCompletion(null, null);
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to persist CDC offsets", e);
            if (callback != null) {
                callback.onCompletion(e, null);
            }
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public Set<Map<String, Object>> connectorPartitions(String connectorName) {
        return Set.of();
    }

    // ---- helpers ----

    private Connection connection() throws SQLException {
        var props = new Properties();
        if (jdbcUser != null) {
            props.setProperty("user", jdbcUser);
        }
        if (jdbcPassword != null) {
            props.setProperty("password", jdbcPassword);
        }
        return DriverManager.getConnection(jdbcUrl, props);
    }

    private static String stringValue(Map<String, Object> m, String key, String def) {
        var v = m.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    private static ByteBuffer fromDbBytes(byte[] bytes) {
        return bytes == null ? null : ByteBuffer.wrap(bytes);
    }

    private static byte[] toDbBytes(ByteBuffer buf) {
        var copy = buf.duplicate();
        var bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
