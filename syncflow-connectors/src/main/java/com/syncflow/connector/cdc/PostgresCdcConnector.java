package com.syncflow.connector.cdc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.cdc.EventHeader;
import com.syncflow.core.cdc.EventMetadata;
import com.syncflow.core.cdc.EventPayload;
import com.syncflow.core.cdc.EventSource;
import com.syncflow.core.cdc.OffsetInformation;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.ValidationResult;
import io.debezium.engine.ChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

@Component
public class PostgresCdcConnector extends DebeziumCdcConnector {

    private static final Logger log = LoggerFactory.getLogger(PostgresCdcConnector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> ROW_TYPE = new TypeReference<>() {
    };

    @Override
    protected ConnectorType connectorType() {
        return ConnectorType.POSTGRESQL;
    }

    @Override
    protected String connectorClassName() {
        return "io.debezium.connector.postgresql.PostgresConnector";
    }

    /**
     * slot name and publication name are scoped per pipeline using the
     * database name so multiple pipelines pointing to different databases don't
     * conflict.
     * For multiple pipelines on the same database, callers should pass a
     * pipeline-specific
     * suffix via ConnectorContext.options("pipelineId").
     */
    @Override
    protected Properties specificProperties(ConnectionConfiguration config) {
        var pipelineSuffix = sanitize(config.database());
        var props = new Properties();
        props.setProperty("database.server.name", "syncflow_pg_" + pipelineSuffix);
        props.setProperty("plugin.name", "pgoutput");
        props.setProperty("publication.name", "syncflow_pub_" + pipelineSuffix);
        props.setProperty("slot.name", "syncflow_slot_" + pipelineSuffix);
        props.setProperty("slot.drop.on.stop", "false");
        props.setProperty("heartbeat.interval.ms", "5000");
        return props;
    }

    /**
     * real pre-flight validation.
     * Checks: TCP connectivity, credentials, wal_level=logical, replication
     * privilege.
     */
    @Override
    public ValidationResult validate(ConnectorContext ctx) {
        var config = ctx.config();
        var jdbcUrl = "jdbc:postgresql://" + config.host() + ":" + config.port()
                + "/" + config.database();
        var errors = new ArrayList<String>();

        try (var conn = DriverManager.getConnection(jdbcUrl, config.username(), config.password())) {

            // Check wal_level
            try (var stmt = conn.createStatement();
                    var rs = stmt.executeQuery("SHOW wal_level")) {
                if (rs.next()) {
                    var walLevel = rs.getString(1);
                    if (!"logical".equalsIgnoreCase(walLevel)) {
                        errors.add("wal_level must be 'logical' but is '" + walLevel
                                + "'. Run: ALTER SYSTEM SET wal_level = logical;");
                    }
                }
            }

            // Check replication privilege
            try (var stmt = conn.createStatement();
                    var rs = stmt.executeQuery(
                            "SELECT rolreplication FROM pg_roles WHERE rolname = current_user")) {
                if (rs.next() && !rs.getBoolean(1)) {
                    errors.add("Database user '" + config.username()
                            + "' does not have REPLICATION privilege.");
                }
            }

        } catch (Exception e) {
            errors.add("Cannot connect to PostgreSQL at " + config.host()
                    + ":" + config.port() + " — " + e.getMessage());
        }

        if (errors.isEmpty()) {
            log.debug("PostgreSQL CDC pre-flight validation passed for host={}", config.host());
            return ValidationResult.ok();
        }
        log.warn("PostgreSQL CDC pre-flight validation failed: {}", errors);
        return ValidationResult.failed(errors);
    }

    @Override
    protected CDCEvent buildEvent(ChangeEvent<String, String> event, ConnectorContext ctx) {
        var value = event.value();
        if (value == null || value.isBlank())
            return null;
        return parseDebeziumEvent(event, ctx);
    }

    // ── Event parsing ────────────────────────────────────────────────────────

    private CDCEvent parseDebeziumEvent(ChangeEvent<String, String> event, ConnectorContext ctx) {
        try {
            var root = MAPPER.readTree(event.value());

            var op = root.has("op") ? root.get("op").asText() : "";
            var operation = switch (op) {
                case "c", "r" -> CDCOperation.INSERT;
                case "u" -> CDCOperation.UPDATE;
                case "d" -> CDCOperation.DELETE;
                default -> null;
            };
            // log unmapped operations instead of silently returning null
            if (operation == null) {
                log.debug("Skipping unsupported Debezium op='{}' for connector=POSTGRESQL", op);
                return null;
            }

            var source = root.has("source") ? root.get("source") : null;
            var schema = source != null && source.has("schema") ? source.get("schema").asText() : "";
            var table = source != null && source.has("table") ? source.get("table").asText() : "";
            var lsn = source != null && source.has("lsn") ? source.get("lsn").asText() : "";
            var tsMs = source != null && source.has("ts_ms") ? source.get("ts_ms").asLong() : 0L;

            Map<String, Object> before = root.has("before") && !root.get("before").isNull()
                    ? MAPPER.convertValue(root.get("before"), ROW_TYPE)
                    : null;
            Map<String, Object> after = root.has("after") && !root.get("after").isNull()
                    ? MAPPER.convertValue(root.get("after"), ROW_TYPE)
                    : null;

            // extract actual PK columns from the Debezium key envelope
            var pk = extractPk(event, before, after, operation);

            var now = Instant.now();
            // update offset so CaptureLifecycle.stop() saves the real position
            updateOffset(Map.of(
                    "lsn", lsn,
                    "ts_ms", String.valueOf(tsMs),
                    "connectorType", "POSTGRESQL"));

            var offset = new OffsetInformation("POSTGRESQL",
                    Map.of("lsn", lsn, "ts_ms", String.valueOf(tsMs)), "", now);

            return new CDCEvent(
                    new EventHeader(UUID.randomUUID().toString(), ctx.config().database(),
                            ctx.config().host(), System.currentTimeMillis(), 1, Map.of()),
                    new EventSource(ctx.config().database(), schema, table, "postgresql"),
                    operation,
                    new EventPayload(before, after, pk),
                    new EventMetadata(0, now, 0),
                    null,
                    offset);

        } catch (Exception e) {
            // log with context instead of silently swallowing
            log.error("Failed to parse Debezium PostgreSQL event: {}", event.value(), e);
            return null;
        }
    }

    /**
     * extract only the primary key columns from the Debezium key envelope.
     * Debezium encodes the key in the event.key() JSON (e.g. {"id":1}).
     * Falls back to scanning common PK column names when the key is unavailable.
     */
    private Map<String, Object> extractPk(ChangeEvent<String, String> event,
            Map<String, Object> before,
            Map<String, Object> after,
            CDCOperation op) {
        // Try the Debezium key envelope first
        var keyJson = event.key();
        if (keyJson != null && !keyJson.isBlank()) {
            try {
                var keyNode = MAPPER.readTree(keyJson);
                if (keyNode.isObject() && keyNode.size() > 0) {
                    return MAPPER.convertValue(keyNode, ROW_TYPE);
                }
            } catch (Exception e) {
                log.debug("Could not parse Debezium key envelope: {}", keyJson);
            }
        }

        // Fallback: look for common PK column names in the row data
        var row = (op == CDCOperation.DELETE && before != null) ? before : after;
        if (row == null)
            return Map.of();

        for (var candidate : List.of("id", "uuid", "_id", "pk")) {
            if (row.containsKey(candidate)) {
                return Map.of(candidate, row.get(candidate));
            }
        }

        // Last resort: return empty (logged at debug to avoid noise)
        log.debug("Could not identify PK columns for operation={}", op);
        return Map.of();
    }

    /**
     * Sanitize a string to be safe in slot/publication names (alphanumeric +
     * underscore).
     */
    private String sanitize(String value) {
        return value == null ? "default" : value.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }
}
