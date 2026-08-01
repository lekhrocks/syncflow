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
import io.debezium.engine.ChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

@Component
public class MySqlCdcConnector extends DebeziumCdcConnector {

    private static final Logger log = LoggerFactory.getLogger(MySqlCdcConnector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> ROW_TYPE = new TypeReference<>() {
    };

    @Override
    protected ConnectorType connectorType() {
        return ConnectorType.MYSQL;
    }

    @Override
    protected String connectorClassName() {
        return "io.debezium.connector.mysql.MySqlConnector";
    }

    @Override
    protected Properties specificProperties(ConnectionConfiguration config) {
        var props = new Properties();
        props.setProperty("database.server.name", "syncflow_mysql_" + sanitize(config.database()));
        props.setProperty("heartbeat.interval.ms", "5000");
        props.setProperty("include.schema.changes", "false");
        return props;
    }

    @Override
    protected CDCEvent buildEvent(ChangeEvent<String, String> event, ConnectorContext ctx) {
        var value = event.value();
        if (value == null || value.isBlank())
            return null;
        return parseDebeziumEvent(event, ctx);
    }

    private CDCEvent parseDebeziumEvent(ChangeEvent<String, String> event, ConnectorContext ctx) {
        try {
            var root = MAPPER.readTree(event.value());
            var op = root.has("op") ? root.get("op").asText() : "";
            var source = root.has("source") ? root.get("source") : null;

            var operation = switch (op) {
                case "c", "r" -> CDCOperation.INSERT;
                case "u" -> CDCOperation.UPDATE;
                case "d" -> CDCOperation.DELETE;
                default -> null;
            };
            // log unmapped ops instead of silently returning null
            if (operation == null) {
                log.debug("Skipping unsupported Debezium op='{}' for connector=MYSQL", op);
                return null;
            }

            var db = source != null && source.has("db") ? source.get("db").asText() : "";
            var table = source != null && source.has("table") ? source.get("table").asText() : "";
            var gtid = source != null && source.has("gtid") ? source.get("gtid").asText() : "";
            var file = source != null && source.has("file") ? source.get("file").asText() : "";
            var pos = source != null && source.has("pos") ? source.get("pos").asLong() : 0L;

            Map<String, Object> before = root.has("before") && !root.get("before").isNull()
                    ? MAPPER.convertValue(root.get("before"), ROW_TYPE)
                    : null;
            Map<String, Object> after = root.has("after") && !root.get("after").isNull()
                    ? MAPPER.convertValue(root.get("after"), ROW_TYPE)
                    : null;

            // extract PK from key envelope, not full row
            var pk = extractPk(event.key(), before, after, operation);

            // update offset so currentOffset() returns the real binlog position
            updateOffset(Map.of(
                    "file", file,
                    "pos", String.valueOf(pos),
                    "gtid", gtid,
                    "connectorType", "MYSQL"));

            var offset = new OffsetInformation("MYSQL",
                    Map.of("file", file, "pos", String.valueOf(pos), "gtid", gtid), "", Instant.now());

            return new CDCEvent(
                    new EventHeader(UUID.randomUUID().toString(), ctx.config().database(),
                            ctx.config().host(), System.currentTimeMillis(), 1, Map.of()),
                    new EventSource(db, db, table, "mysql"),
                    operation,
                    new EventPayload(before, after, pk),
                    new EventMetadata(0, Instant.now(), 0),
                    null,
                    offset);

        } catch (Exception e) {
            // log with context instead of silently swallowing
            log.error("Failed to parse Debezium MySQL event: {}", event.value(), e);
            return null;
        }
    }

    /**
     * Extract PK from the Debezium key envelope; fall back to common column names.
     */
    private Map<String, Object> extractPk(String keyJson,
            Map<String, Object> before,
            Map<String, Object> after,
            CDCOperation op) {
        if (keyJson != null && !keyJson.isBlank()) {
            try {
                var keyNode = MAPPER.readTree(keyJson);
                if (keyNode.isObject() && !keyNode.isEmpty()) {
                    return MAPPER.convertValue(keyNode, ROW_TYPE);
                }
            } catch (Exception e) {
                log.debug("Could not parse Debezium key envelope: {}", keyJson);
            }
        }
        var row = (op == CDCOperation.DELETE && before != null) ? before : after;
        if (row == null)
            return Map.of();
        for (var candidate : List.of("id", "uuid", "_id", "pk")) {
            if (row.containsKey(candidate))
                return Map.of(candidate, row.get(candidate));
        }
        return Map.of();
    }

    private String sanitize(String value) {
        return value == null ? "default" : value.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }
}
