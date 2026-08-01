package com.syncflow.plugin.spi;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface SnapshotProvider {

    long estimateRowCount(PluginContext context, String schema, String table);

    PageResult readBatch(PluginContext context, String schema, String table,
            int batchNumber, int batchSize);

    default Stream<Map<String, Object>> streamAll(PluginContext context,
            String schema, String table,
            int batchSize) {
        var builder = Stream.<Map<String, Object>>builder();
        String cursor = null;
        int batch = 0;
        while (true) {
            var page = readBatch(context, schema, table, batch++, batchSize);
            page.rows().forEach(builder);
            if (page.nextCursor() == null)
                break;
            cursor = page.nextCursor();
        }
        return builder.build();
    }

    record PageResult(List<Map<String, Object>> rows, String nextCursor) {

        public static PageResult empty() {
            return new PageResult(List.of(), null);
        }
        public static PageResult of(List<Map<String, Object>> rows, String cursor) {
            return new PageResult(rows, cursor);
        }
    }
}
