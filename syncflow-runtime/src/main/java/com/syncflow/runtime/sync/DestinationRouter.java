package com.syncflow.runtime.sync;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.connection.spi.ConnectionLookup;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.spi.writer.WriterRegistry;
import com.syncflow.tenant.TenantContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes CDC batches to destination writers through core ports.
 * No Spring, persistence, Kafka, or API dependencies are allowed here.
 */
public final class DestinationRouter {

    private static final Logger log = LoggerFactory.getLogger(DestinationRouter.class);

    private final WriterRegistry writerRegistry;
    private final ConnectionLookup connectionLookup;

    public DestinationRouter(WriterRegistry writerRegistry, ConnectionLookup connectionLookup) {
        this.writerRegistry = writerRegistry;
        this.connectionLookup = connectionLookup;
    }

    public WriteResult writeBatch(String connectionId, List<CDCEvent> events,
            List<String> destColumns, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        if (events.isEmpty()) {
            return new WriteResult(true, null);
        }
        ConnectionConfiguration connection = connectionLookup
                .getWithDecryptedCredentials(connectionId, tenantContext);
        var writer = writerRegistry.get(connection.connectorType())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No writer for: " + connection.connectorType()));
        writer.connect(connection);
        try {
            Map<String, List<Map<String, Object>>> upserts = new HashMap<>();
            Map<String, List<String>> pkColumnsByTable = new HashMap<>();
            Map<String, List<Map<String, Object>>> deletes = new HashMap<>();
            for (var event : events) {
                var tableName = event.source().table();
                if (tableName == null || tableName.isBlank()) {
                    continue;
                }
                switch (event.operation()) {
                    case INSERT, UPDATE -> {
                        if (event.payload().after() != null) {
                            upserts.computeIfAbsent(tableName, t -> new ArrayList<>())
                                    .add(event.payload().after());
                            var pk = event.payload().primaryKeys();
                            if (pk != null && !pk.isEmpty()) {
                                pkColumnsByTable.putIfAbsent(tableName, new ArrayList<>(pk.keySet()));
                            }
                        }
                    }
                    case DELETE -> {
                        var pk = event.payload().primaryKeys();
                        if (pk != null && !pk.isEmpty()) {
                            deletes.computeIfAbsent(tableName, t -> new ArrayList<>()).add(pk);
                            pkColumnsByTable.putIfAbsent(tableName, List.copyOf(pk.keySet()));
                        }
                    }
                }
            }
            for (var entry : upserts.entrySet()) {
                var rows = entry.getValue();
                if (!rows.isEmpty()) {
                    var keyColumns = pkColumnsByTable.get(entry.getKey());
                    if (keyColumns != null && !keyColumns.isEmpty()) {
                        writer.upsertBatch(entry.getKey(), destColumns, rows, keyColumns);
                    } else {
                        writer.writeBatch(entry.getKey(), destColumns, rows);
                    }
                }
            }
            for (var entry : deletes.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    writer.deleteBatch(entry.getKey(), pkColumnsByTable.get(entry.getKey()), entry.getValue());
                }
            }
            writer.flush();
            writer.commit();
            return new WriteResult(true, null);
        } catch (Exception e) {
            log.error("Batch write failed for {} events on connection={}", events.size(), connectionId, e);
            try {
                writer.rollback();
            } catch (Exception ignored) {
                // Preserve the original write failure.
            }
            return new WriteResult(false, e.getMessage());
        } finally {
            writer.close();
        }
    }

    public record WriteResult(boolean success, String error) {
    }
}
