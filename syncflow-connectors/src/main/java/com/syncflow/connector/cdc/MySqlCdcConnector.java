package com.syncflow.connector.cdc;

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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

@Component
public class MySqlCdcConnector extends DebeziumCdcConnector {

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
        props.setProperty("database.server.name", "syncflow_mysql");
        props.setProperty("heartbeat.interval.ms", "5000");
        props.setProperty("include.schema.changes", "false");
        return props;
    }

    @Override
    protected CDCEvent buildEvent(ChangeEvent<String, String> event, ConnectorContext ctx) {
        var value = event.value();
        if (value == null || value.isBlank())
            return null;
        return parseDebeziumEvent(value, ctx);
    }

    private CDCEvent parseDebeziumEvent(String json, ConnectorContext ctx) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(json);
            var op = root.has("op") ? root.get("op").asText() : "";
            var source = root.has("source") ? root.get("source") : null;

            var operation = switch (op) {
                case "c", "r" -> CDCOperation.INSERT;
                case "u" -> CDCOperation.UPDATE;
                case "d" -> CDCOperation.DELETE;
                default -> null;
            };
            if (operation == null)
                return null;

            var db = source != null && source.has("db") ? source.get("db").asText() : "";
            var table = source != null && source.has("table") ? source.get("table").asText() : "";
            var gtid = source != null && source.has("gtid") ? source.get("gtid").asText() : "";
            var file = source != null && source.has("file") ? source.get("file").asText() : "";
            var pos = source != null && source.has("pos") ? source.get("pos").asLong() : 0L;

            var before = root.has("before") && !root.get("before").isNull()
                    ? mapper.convertValue(root.get("before"), Map.class)
                    : null;
            var after = root.has("after") && !root.get("after").isNull()
                    ? mapper.convertValue(root.get("after"), Map.class)
                    : null;
            var pk = extractKeys(after != null ? after : before);

            var offset = new OffsetInformation("MYSQL",
                    Map.of("file", file, "pos", String.valueOf(pos), "gtid", gtid), "", Instant.now());

            return new CDCEvent(
                    new EventHeader(UUID.randomUUID().toString(), ctx.config().database(),
                            ctx.config().host(), System.currentTimeMillis(), 1, Map.of()),
                    new EventSource(db, db, table, "mysql"),
                    operation, new EventPayload(before, after, pk),
                    new EventMetadata(0, Instant.now(), 0), null, offset);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> extractKeys(Map<String, Object> record) {
        return record != null ? record : Map.of();
    }
}
