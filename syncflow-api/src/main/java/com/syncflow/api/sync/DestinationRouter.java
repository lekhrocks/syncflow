package com.syncflow.api.sync;

import com.syncflow.api.connection.ConnectionMapper;
import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.metadata.ConnectorTypeMapper;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.spi.writer.WriterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes CDC events to the right destination writer.
 *
 * Two write paths:
 * - {@link #write(CDCEvent, List)} — legacy per-event path. Kept for
 * callers that already have a write in flight.
 * - {@link #writeBatch(List, List)} — batched: groups events by operation
 * and table, emits a single writeBatch / deleteBatch call per group,
 * then flushes + commits once. This is the hot path used by
 * {@code SyncOrchestrator}.
 *
 * Per-event DB connect / flush / commit is the P1 bottleneck the analysis
 * flagged; the batched path amortises those costs across N events.
 */
@Component
public class DestinationRouter {

    private static final Logger log = LoggerFactory.getLogger(DestinationRouter.class);

    private final WriterRegistry writerRegistry;
    private final ConnectionService connectionService;

    public DestinationRouter(WriterRegistry writerRegistry,
            ConnectionService connectionService) {
        this.writerRegistry = writerRegistry;
        this.connectionService = connectionService;
    }

    /** Batched write: groups by table, sorts by operation, single flush+commit. */
    public WriteResult writeBatch(String connectionId, List<CDCEvent> events,
            List<String> destColumns) {
        if (events.isEmpty())
            return new WriteResult(true, null);
        var conn = connectionService.getWithDecryptedCredentials(connectionId);
        var ct = ConnectorTypeMapper.toCore(conn.getProperties().type());
        var writer = writerRegistry.get(ct)
                .orElseThrow(() -> new IllegalArgumentException("No writer for: " + ct));
        writer.connect(ConnectionMapper.toConfig(conn));
        try {
            // Group rows for INSERT/UPDATE by table; group PK maps for DELETE by table.
            Map<String, List<Map<String, Object>>> upserts = new HashMap<>();
            Map<String, List<String>> pkColumnsByTable = new HashMap<>();
            Map<String, List<Map<String, Object>>> deletes = new HashMap<>();
            for (var event : events) {
                var tableName = event.source().table();
                if (tableName == null || tableName.isBlank())
                    continue;
                switch (event.operation()) {
                    case INSERT, UPDATE -> {
                        if (event.payload().after() != null) {
                            upserts.computeIfAbsent(tableName, t -> new ArrayList<>())
                                    .add(event.payload().after());
                            // Key columns for the upsert conflict target come from
                            // the event's primary keys (both INSERT and UPDATE carry them).
                            var pk = event.payload().primaryKeys();
                            if (pk != null && !pk.isEmpty()) {
                                pkColumnsByTable.putIfAbsent(tableName, new ArrayList<>(pk.keySet()));
                            }
                        }
                    }
                    case DELETE -> {
                        var pkMap = event.payload().primaryKeys();
                        if (pkMap != null && !pkMap.isEmpty()) {
                            deletes.computeIfAbsent(tableName, t -> new ArrayList<>())
                                    .add(pkMap);
                            pkColumnsByTable.putIfAbsent(tableName, List.copyOf(pkMap.keySet()));
                        }
                    }
                }
            }
            for (var entry : upserts.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    var keyCols = pkColumnsByTable.get(entry.getKey());
                    // use UPSERT (ON CONFLICT) when the key columns are known, so a
                    // re-insert after an UPDATE on an existing PK doesn't fail with a
                    // duplicate-key error. Without a key, fall back to a plain insert.
                    if (keyCols != null && !keyCols.isEmpty()) {
                        writer.upsertBatch(entry.getKey(), destColumns, entry.getValue(), keyCols);
                    } else {
                        writer.writeBatch(entry.getKey(), destColumns, entry.getValue());
                    }
                }
            }
            for (var entry : deletes.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    writer.deleteBatch(entry.getKey(), pkColumnsByTable.get(entry.getKey()),
                            entry.getValue());
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
            }
            return new WriteResult(false, e.getMessage());
        } finally {
            writer.close();
        }
    }

    /**
     * Legacy single-event path. Kept for callers that already have a single event.
     */
    public WriteResult write(String connectionId, CDCEvent event, List<String> destColumns) {
        return writeBatch(connectionId, List.of(event), destColumns);
    }

    public record WriteResult(boolean success, String error) {
    }
}
