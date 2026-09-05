package com.syncflow.api.snapshot;

import com.syncflow.api.config.RuntimeProperties;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.mapping.ColumnMapping;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.snapshot.ChunkRange;
import com.syncflow.core.snapshot.SnapshotCheckpoint;
import com.syncflow.core.snapshot.SnapshotJob;
import com.syncflow.core.snapshot.pipeline.FilterProcessor;
import com.syncflow.core.snapshot.pipeline.ProcessingContext;
import com.syncflow.core.snapshot.pipeline.TransformProcessor;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.SnapshotCapableConnector;
import com.syncflow.core.spi.writer.DestinationWriter;
import com.syncflow.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Stateless worker that snapshots one PK-range chunk of one table.
 * Extracted from {@link SnapshotExecutor} to keep the executor focused on
 * lifecycle, persistence, and thread management.
 */
final class SnapshotWorker {

    private SnapshotWorker() {
    }

    @FunctionalInterface
    interface TriConsumer<A, B, C> {

        void accept(A a, B b, C c);
    }

    /**
     * Snapshot one PK-range chunk: keyset-paginate, filter/transform,
     * batch-write to the destination. Writes serialize on {@code writerLock}.
     */
    static void snapshotRange(SnapshotJob job, PipelineDesign pipeline, TenantContext tenantContext,
            SnapshotCapableConnector connector, DestinationWriter writer, Object writerLock,
            TableMapping tm, ChunkRange range, ConnectorContext sourceCtx,
            AtomicLong rowsProcessed, AtomicLong batchesDone, long totalRows, long totalBatches,
            CheckpointStore checkpointStore, RuntimeProperties runtime,
            Object progressLock, BooleanSupplier isCancelled,
            java.util.function.BiConsumer<SnapshotJob, TenantContext> persist,
            TriConsumer<String, SnapshotJob, TenantContext> emit,
            MeterRegistry meterRegistry) {
        var ctx = new ProcessingContext(pipeline, tm);
        var checkpoint = checkpointStore.get(
                tenantContext.tenantId().value(), pipeline.id().value(), tm.sourceTable(), range.index());
        // Resume only from a checkpoint cursor that still lies inside this
        // chunk's bounds.
        String cursor = (checkpoint != null && cursorWithinRange(checkpoint.cursor(), range))
                ? checkpoint.cursor()
                : null;
        int batchNumber = (checkpoint != null) ? checkpoint.lastBatchNumber() + 1 : 0;
        var chunkBatchCounter = new AtomicLong(batchNumber - 1);

        var destCols = tm.columnMappings().stream()
                .map(ColumnMapping::destinationColumn)
                .toList();
        var destTable = tm.destinationTable() != null
                ? tm.destinationTable()
                : tm.destinationCollection();
        var keyCols = tm.primaryKey() != null ? tm.primaryKey().destinationColumns() : null;
        var useUpsert = keyCols != null && !keyCols.isEmpty();
        var chain = new FilterProcessor().andThen(new TransformProcessor());

        if (isCancelled.getAsBoolean()) {
            return;
        }
        var batchInfo = new BatchInformation(batchNumber, pipeline.settings().batchSize(),
                tm.sourceTable(), cursor, range);
        var page = connector.readBatch(sourceCtx, pipeline.source().schema(),
                tm.sourceTable(), batchInfo);

        while (page != null && !page.rows().isEmpty() && !isCancelled.getAsBoolean()) {
            var batch = page.rows().stream()
                    .map(r -> chain.process(r, ctx))
                    .filter(Objects::nonNull)
                    .toList();

            if (!batch.isEmpty()) {
                synchronized (writerLock) {
                    if (useUpsert) {
                        writer.upsertBatch(destTable, destCols, batch, keyCols);
                    } else {
                        writer.writeBatch(destTable, destCols, batch);
                    }
                }
            }

            rowsProcessed.addAndGet(batch.size());
            batchesDone.incrementAndGet();
            var chunkBatch = chunkBatchCounter.incrementAndGet();
            com.syncflow.api.config.MetricsHelper.increment(meterRegistry, "syncflow.snapshot.rows", batch.size(),
                    "pipeline", pipeline.id().value());

            if (chunkBatch % runtime.getSnapshot().getCheckpointIntervalBatches() == 0) {
                checkpointStore.save(tenantContext.tenantId().value(), new SnapshotCheckpoint(
                        pipeline.id().value(), tm.sourceTable(), range.index(),
                        (int) chunkBatch, rowsProcessed.get(), page.nextCursor()));
            }

            synchronized (progressLock) {
                if (!isCancelled.getAsBoolean()
                        && chunkBatch % runtime.getSnapshot().getProgressPublishIntervalBatches() == 0) {
                    var pct = totalRows > 0 ? (double) rowsProcessed.get() / totalRows * 100 : 0;
                    var updated = job.withProgress(new com.syncflow.core.snapshot.SnapshotProgress(
                            (int) batchesDone.get(), (int) totalBatches,
                            rowsProcessed.get(), totalRows, pct, 0));
                    persist.accept(updated, tenantContext);
                    emit.accept(job.getId().value(), updated, tenantContext);
                }
            }

            var nextBatchInfo = new BatchInformation(
                    (int) chunkBatch + 1, pipeline.settings().batchSize(),
                    tm.sourceTable(), page.nextCursor(), range);
            page = connector.readBatch(sourceCtx, pipeline.source().schema(),
                    tm.sourceTable(), nextBatchInfo);
        }
    }

    /**
     * A resume cursor is meaningful for a chunk only if it lies within
     * {@code [start, end)}. Numeric cursors/bounds compare by value; anything
     * non-numeric (uuid/text) or out of range is not a valid resume point.
     */
    static boolean cursorWithinRange(String cursor, ChunkRange range) {
        if (cursor == null || range == null) {
            return false;
        }
        if (range.start() instanceof Number start) {
            try {
                BigDecimal c = new BigDecimal(cursor);
                BigDecimal lo = new BigDecimal(start.toString());
                if (c.compareTo(lo) < 0) {
                    return false;
                }
                return range.end() == null
                        || !(range.end() instanceof Number end)
                        || c.compareTo(new BigDecimal(end.toString())) < 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return range.start() == null;
    }
}
