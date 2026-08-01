package com.syncflow.core.spi;

import com.syncflow.core.snapshot.BatchInformation;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface SnapshotCapableConnector extends MetadataCapableConnector {

    /**
     * Estimate total rows for a table. Used for progress reporting.
     */
    long estimateRows(ConnectorContext context, String schema, String table);

    /**
     * Read one page of rows as a list of column-name → value maps.
     * Cursor-based pagination: returns a cursor string for the next page,
     * or null when no more rows exist.
     */
    Page readBatch(ConnectorContext context, String schema, String table,
            BatchInformation batchInfo);

    /**
     * Stream all rows from a table. The default reads batches internally.
     * Override for database-native streaming (Postgres CURSOR, MySQL streaming).
     */
    default Stream<Map<String, Object>> streamRows(ConnectorContext context,
            String schema, String table,
            int batchSize) {
        var builder = Stream.<Map<String, Object>>builder();
        String cursor = null;
        int batchNum = 0;
        while (true) {
            var info = new BatchInformation(batchNum++, batchSize, table, cursor);
            var page = readBatch(context, schema, table, info);
            page.rows().forEach(builder);
            if (page.nextCursor() == null)
                break;
            cursor = page.nextCursor();
        }
        return builder.build();
    }

    record Page(List<Map<String, Object>> rows, String nextCursor) {

        public static Page empty() {
            return new Page(List.of(), null);
        }
        public static Page of(List<Map<String, Object>> rows, String nextCursor) {
            return new Page(rows, nextCursor);
        }
    }
}
