package com.syncflow.api.sync;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.cdc.EventHeader;
import com.syncflow.core.cdc.EventMetadata;
import com.syncflow.core.cdc.EventPayload;
import com.syncflow.core.cdc.EventSource;
import com.syncflow.core.cdc.OffsetInformation;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.connection.ConnectionId;
import com.syncflow.core.connection.ConnectionMetadata;
import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionStatus;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.writer.DestinationWriter;
import com.syncflow.core.spi.writer.WriterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the batched write path (R2/R3):
 * - rows are written to the DESTINATION table name, not the source name
 * - upserts go through {@link DestinationWriter#upsertBatch} (ON CONFLICT)
 * when the event carries primary-key columns
 * - plain inserts only when no key columns are known
 */
class DestinationRouterTest {

    private final ConnectionService connectionService = mock(ConnectionService.class);
    private final WriterRegistry writerRegistry = mock(WriterRegistry.class);
    private final DestinationWriter writer = mock(DestinationWriter.class);
    private final DestinationRouter router = new DestinationRouter(writerRegistry, connectionService);

    private Connection pg() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL,
                "localhost", 5432, "dest", Map.of());
        return Connection.restore(ConnectionId.generate(), "dest-conn", props,
                new Credentials("u", "p"), ConnectionStatus.VALID,
                ConnectionMetadata.unknown(), Instant.now(), Instant.now());
    }

    @Test
    void rowsRoutedToDestinationTableNotSource() {
        when(connectionService.getWithDecryptedCredentials("dest-conn")).thenReturn(pg());
        when(writerRegistry.get(ConnectorType.POSTGRESQL)).thenReturn(Optional.of(writer));
        when(connectionService.getWithDecryptedCredentials(any())).thenReturn(pg());

        var event = event("src_orders", "dest_orders", CDCOperation.INSERT,
                Map.of("id", 1, "name", "a"), Map.of("id", 1));

        router.writeBatch("dest-conn", List.of(event), List.of("id", "name"));

        // R2: the router must pass the DESTINATION table name to the writer.
        verify(writer).upsertBatch(org.mockito.ArgumentMatchers.eq("dest_orders"),
                anyList(), anyList(), anyList());
    }

    @Test
    void updateWithPrimaryKeyUsesUpsert() {
        when(connectionService.getWithDecryptedCredentials(any())).thenReturn(pg());
        when(writerRegistry.get(ConnectorType.POSTGRESQL)).thenReturn(Optional.of(writer));

        var event = event("src_orders", "dest_orders", CDCOperation.UPDATE,
                Map.of("id", 1, "name", "b"), Map.of("id", 1));

        router.writeBatch("dest-conn", List.of(event), List.of("id", "name"));

        // R3: an UPDATE carrying PK columns must use upsert (ON CONFLICT), not a
        // plain insert that would fail on a duplicate key for an existing row.
        verify(writer).upsertBatch(any(), anyList(), anyList(), org.mockito.ArgumentMatchers.argThat(
                keys -> keys.equals(List.of("id"))));
        verify(writer, never()).writeBatch(any(), anyList(), anyList());
    }

    @Test
    void insertWithoutKeyColumnsFallsBackToPlainInsert() {
        when(connectionService.getWithDecryptedCredentials(any())).thenReturn(pg());
        when(writerRegistry.get(ConnectorType.POSTGRESQL)).thenReturn(Optional.of(writer));

        var event = event("src_orders", "dest_orders", CDCOperation.INSERT,
                Map.of("id", 1, "name", "a"), Map.of());

        router.writeBatch("dest-conn", List.of(event), List.of("id", "name"));

        verify(writer).writeBatch(any(), anyList(), anyList());
        verify(writer, never()).upsertBatch(any(), anyList(), anyList(), anyList());
    }

    /** Minimal CDC event with a source/dest table and optional PK columns. */
    private CDCEvent event(String src, String dest, CDCOperation op,
            Map<String, Object> after, Map<String, Object> pk) {
        return new CDCEvent(
                new EventHeader("evt-" + (++counter), dest, "localhost", 0, 1, Map.of()),
                new EventSource(dest, "", dest, "postgresql"),
                op,
                new EventPayload(Map.of(), after == null ? null : new HashMap<>(after), pk),
                new EventMetadata(0, Instant.now(), 0),
                null,
                new OffsetInformation("POSTGRESQL", Map.of(), "", Instant.now()));
    }

    private static int counter = 0;
}
