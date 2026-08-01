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
public class PostgresCdcConnector extends DebeziumCdcConnector {

    @Override
    protected ConnectorType connectorType() {
        return ConnectorType.POSTGRESQL;
    }

    @Override
    protected String connectorClassName() {
        return "io.debezium.connector.postgresql.PostgresConnector";
    }

    @Override
    protected Properties specificProperties(ConnectionConfiguration config) {
        var props = new Properties();
        props.setProperty("database.server.name", "syncflow_pg");
        props.setProperty("plugin.name", "pgoutput");
        props.setProperty("publication.name", "syncflow_pub");
        props.setProperty("slot.name", "syncflow_slot");
        props.setProperty("slot.drop.on.stop", "false");
        props.setProperty("heartbeat.interval.ms", "5000");
        return props;
    }

    @Override
    protected CDCEvent buildEvent(ChangeEvent<String, String> event, ConnectorContext ctx) {
        var value = event.value();
        if (value == null || value.isBlank())
            return null;
        return parseDebeziumEvent("postgresql", value, ctx);
    }

    private CDCEvent parseDebeziumEvent(String dbType, String json, ConnectorContext ctx) {
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

            var schema = source != null && source.has("schema") ? source.get("schema").asText() : "";
            var table = source != null && source.has("table") ? source.get("table").asText() : "";
            var lsn = source != null && source.has("lsn") ? source.get("lsn").asText() : "";
            var tsMs = source != null && source.has("ts_ms") ? source.get("ts_ms").asLong() : 0L;

            var before = root.has("before") && !root.get("before").isNull()
                    ? mapper.convertValue(root.get("before"), Map.class)
                    : null;
            var after = root.has("after") && !root.get("after").isNull()
                    ? mapper.convertValue(root.get("after"), Map.class)
                    : null;
            var pk = extractPk(before, after, operation);

            var eventId = UUID.randomUUID().toString();
            var now = Instant.now();
            var offset = new OffsetInformation("POSTGRESQL",
                    Map.of("lsn", lsn, "ts_ms", String.valueOf(tsMs)), "", now);

            return new CDCEvent(
                    new EventHeader(eventId, ctx.config().database(), ctx.config().host(),
                            System.currentTimeMillis(), 1, Map.of()),
                    new EventSource(ctx.config().database(), schema, table, dbType),
                    operation,
                    new EventPayload(before, after, pk),
                    new EventMetadata(0, now, 0),
                    null,
                    offset);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> extractPk(Map<String, Object> before, Map<String, Object> after, CDCOperation op) {
        if (op == CDCOperation.DELETE && before != null)
            return before;
        if (after != null)
            return after;
        return Map.of();
    }
}
