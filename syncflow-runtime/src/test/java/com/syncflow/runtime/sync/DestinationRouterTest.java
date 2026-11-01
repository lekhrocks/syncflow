package com.syncflow.runtime.sync;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.cdc.EventHeader;
import com.syncflow.core.cdc.EventMetadata;
import com.syncflow.core.cdc.EventPayload;
import com.syncflow.core.cdc.EventSource;
import com.syncflow.core.cdc.OffsetInformation;
import com.syncflow.core.connection.spi.ConnectionLookup;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.writer.DestinationWriter;
import com.syncflow.core.spi.writer.WriterRegistry;
import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantId;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DestinationRouterTest {

    private final DestinationWriter writer = new RecordingWriter();
    private final WriterRegistry registry = new WriterRegistry() {

        @Override
        public void register(ConnectorType type, DestinationWriter destinationWriter) {
        }

        @Override
        public Optional<DestinationWriter> get(ConnectorType type) {
            return Optional.of(writer);
        }
    };
    private final ConnectionConfiguration config = new ConnectionConfiguration(ConnectorType.POSTGRESQL,
            "localhost", 5432, "dest", "u", "p", Map.of());
    private final ConnectionLookup connections = (id, tenant) -> config;
    private final TenantContext tenant = new TenantContext(TenantId.DEFAULT, null, null, null,
            "test", Set.of(), Instant.now());

    @Test
    void routesUpsertThroughCorePorts() {
        var event = event(Map.of("id", 1), Map.of("id", 1));
        var result = new DestinationRouter(registry, connections)
                .writeBatch("connection", List.of(event), List.of("id"), tenant);

        assertTrue(result.success());
        var recording = (RecordingWriter) writer;
        assertTrue(recording.upserted);
    }

    private CDCEvent event(Map<String, Object> row, Map<String, Object> keys) {
        return new CDCEvent(
                new EventHeader("event", "destination", "localhost", 0, 1, Map.of()),
                new EventSource("destination", "", "destination", "postgresql"),
                CDCOperation.INSERT,
                new EventPayload(Map.of(), new HashMap<>(row), keys),
                new EventMetadata(0, Instant.now(), 0), null,
                new OffsetInformation("POSTGRESQL", Map.of(), "", Instant.now()));
    }

    private static final class RecordingWriter implements DestinationWriter {

        private boolean upserted;

        @Override
        public void connect(ConnectionConfiguration config) {
        }

        @Override
        public void writeBatch(String table, List<String> columns, List<Map<String, Object>> rows) {
        }

        @Override
        public void deleteBatch(String table, List<String> columns, List<Map<String, Object>> rows) {
        }

        @Override
        public void upsertBatch(String table, List<String> columns, List<Map<String, Object>> rows,
                List<String> keyColumns) {
            upserted = true;
        }

        @Override
        public void flush() {
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }
    }
}
