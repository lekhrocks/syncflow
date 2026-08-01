package com.syncflow.api.snapshot;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.metadata.ConnectorTypeMapper;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.mapping.ColumnMapping;
import com.syncflow.core.registry.ConnectorRegistry;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.snapshot.SnapshotCheckpoint;
import com.syncflow.core.snapshot.SnapshotError;
import com.syncflow.core.snapshot.SnapshotJob;
import com.syncflow.core.snapshot.SnapshotProgress;
import com.syncflow.core.snapshot.SnapshotStatistics;
import com.syncflow.core.snapshot.pipeline.FilterProcessor;
import com.syncflow.core.snapshot.pipeline.ProcessingContext;
import com.syncflow.core.snapshot.pipeline.TransformProcessor;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.SnapshotCapableConnector;
import com.syncflow.core.spi.writer.DestinationWriter;
import com.syncflow.core.spi.writer.WriterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SnapshotExecutor {

    private final PipelineDesignerService pipelineService;
    private final ConnectionService connectionService;
    private final ConnectorRegistry connectorRegistry;
    private final WriterRegistry writerRegistry;
    private final CheckpointStore checkpointStore;
    private final MeterRegistry meterRegistry;
    private final Map<String, SnapshotJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancellations = new ConcurrentHashMap<>();

    public SnapshotExecutor(PipelineDesignerService pipelineService,
            ConnectionService connectionService,
            ConnectorRegistry connectorRegistry,
            WriterRegistry writerRegistry,
            CheckpointStore checkpointStore,
            MeterRegistry meterRegistry) {
        this.pipelineService = pipelineService;
        this.connectionService = connectionService;
        this.connectorRegistry = connectorRegistry;
        this.writerRegistry = writerRegistry;
        this.checkpointStore = checkpointStore;
        this.meterRegistry = meterRegistry;
    }

    public SnapshotJob start(String pipelineId) {
        var pipeline = pipelineService.get(pipelineId);
        var job = new SnapshotJob(pipelineId).withRunning();
        jobs.put(job.getId().value(), job);
        cancellations.put(job.getId().value(), new AtomicBoolean(false));

        Thread.startVirtualThread(() -> execute(job, pipeline));
        return job;
    }

    public SnapshotJob get(String snapshotId) {
        var job = jobs.get(snapshotId);
        if (job == null)
            throw new NoSuchElementException("Snapshot not found: " + snapshotId);
        return job;
    }

    public List<SnapshotJob> list() {
        return List.copyOf(jobs.values());
    }

    public SnapshotJob cancel(String snapshotId) {
        var flag = cancellations.get(snapshotId);
        if (flag != null)
            flag.set(true);
        var job = jobs.get(snapshotId);
        if (job != null) {
            jobs.put(snapshotId, job.withCancelled());
            return job.withCancelled();
        }
        throw new NoSuchElementException("Snapshot not found: " + snapshotId);
    }

    private void execute(SnapshotJob job, PipelineDesign pipeline) {
        var timer = Timer.builder("syncflow.snapshot.duration")
                .tag("pipeline", pipeline.id().value())
                .register(meterRegistry);
        var sample = Timer.start(meterRegistry);
        var rowsProcessed = new AtomicLong(0);
        var batchesDone = new AtomicLong(0);

        try {
            var sourceCtx = buildSourceContext(pipeline);
            var destCfg = buildDestConfig(pipeline);
            var connector = resolveSourceConnector(pipeline);
            var writer = resolveWriter(pipeline);

            writer.connect(destCfg);

            long totalRows = 0;
            long totalBatches = 0;
            if (!pipeline.tableMappings().isEmpty()) {
                var tm = pipeline.tableMappings().getFirst();
                totalRows = connector.estimateRows(sourceCtx, pipeline.source().schema(), tm.sourceTable());
                totalBatches = (totalRows / pipeline.settings().batchSize()) + 1;
            }

            var progress = SnapshotProgress.starting(totalRows);
            jobs.put(job.getId().value(), job.withProgress(progress));

            for (var tm : pipeline.tableMappings()) {
                if (isCancelled(job))
                    break;
                var ctx = new ProcessingContext(pipeline, tm);

                var checkpoint = checkpointStore.get(pipeline.id().value(), tm.sourceTable());
                int startBatch = (checkpoint != null) ? checkpoint.lastBatchNumber() + 1 : 0;

                var batchInfo = new BatchInformation(startBatch, pipeline.settings().batchSize(),
                        tm.sourceTable(), null);
                var page = connector.readBatch(sourceCtx, pipeline.source().schema(),
                        tm.sourceTable(), batchInfo);

                var chain = new FilterProcessor().andThen(new TransformProcessor());

                while (page != null && !page.rows().isEmpty() && !isCancelled(job)) {
                    var batch = page.rows().stream()
                            .map(r -> chain.process(r, ctx))
                            .filter(Objects::nonNull)
                            .toList();

                    if (!batch.isEmpty()) {
                        var destCols = tm.columnMappings().stream()
                                .map(ColumnMapping::destinationColumn)
                                .toList();
                        writer.writeBatch(tm.destinationTable() != null
                                ? tm.destinationTable()
                                : tm.destinationCollection(),
                                batch, destCols);
                    }

                    rowsProcessed.addAndGet(batch.size());
                    batchesDone.incrementAndGet();
                    var pct = totalRows > 0 ? (double) rowsProcessed.get() / totalRows * 100 : 0;
                    jobs.put(job.getId().value(), job.withProgress(
                            new SnapshotProgress((int) batchesDone.get(), (int) totalBatches,
                                    rowsProcessed.get(), totalRows, pct, 0)));

                    meterRegistry.counter("syncflow.snapshot.rows",
                            "pipeline", pipeline.id().value()).increment(batch.size());

                    // checkpoint every 5 batches
                    if (batchesDone.get() % 5 == 0) {
                        checkpointStore.save(new SnapshotCheckpoint(
                                pipeline.id().value(), tm.sourceTable(),
                                (int) batchesDone.get(), rowsProcessed.get(), null));
                    }

                    var nextBatchInfo = new BatchInformation(
                            (int) batchesDone.get(), pipeline.settings().batchSize(),
                            tm.sourceTable(), null);
                    page = connector.readBatch(sourceCtx, pipeline.source().schema(),
                            tm.sourceTable(), nextBatchInfo);
                }
            }

            writer.flush();
            writer.commit();

            var elapsed = sample.stop(timer);
            if (!isCancelled(job)) {
                var stats = new SnapshotStatistics(totalRows, rowsProcessed.get(),
                        batchesDone.get(), totalBatches, 0, 0,
                        job.getCreatedAt(), Instant.now(), elapsed / 1_000_000);
                jobs.put(job.getId().value(), job.withCompleted(stats));
                checkpointStore.deleteAll(pipeline.id().value());
            }
        } catch (Exception e) {
            sample.stop(timer);
            var error = new SnapshotError("SNAPSHOT_FAILED", e.getMessage(),
                    (int) batchesDone.get(), Instant.now());
            jobs.put(job.getId().value(), job.withFailed(List.of(error)));
            meterRegistry.counter("syncflow.snapshot.errors",
                    "pipeline", pipeline.id().value()).increment();
        }
    }

    private boolean isCancelled(SnapshotJob job) {
        var flag = cancellations.get(job.getId().value());
        return flag != null && flag.get();
    }

    private ConnectorContext buildSourceContext(PipelineDesign pipeline) {
        var conn = connectionService.getWithDecryptedCredentials(pipeline.source().connectionId());
        var config = toConfig(conn);
        return new ConnectorContext(config, Map.of());
    }

    private ConnectionConfiguration buildDestConfig(PipelineDesign pipeline) {
        var conn = connectionService.getWithDecryptedCredentials(pipeline.destination().connectionId());
        return toConfig(conn);
    }

    private SnapshotCapableConnector resolveSourceConnector(PipelineDesign pipeline) {
        var conn = connectionService.getWithDecryptedCredentials(pipeline.source().connectionId());
        var ct = ConnectorTypeMapper.toCore(conn.getProperties().type());
        var c = connectorRegistry.get(ct)
                .orElseThrow(() -> new IllegalArgumentException("No connector for type: " + ct));
        if (!(c instanceof SnapshotCapableConnector sc)) {
            throw new IllegalArgumentException("Connector does not support snapshot: " + ct);
        }
        return sc;
    }

    private DestinationWriter resolveWriter(PipelineDesign pipeline) {
        var conn = connectionService.getWithDecryptedCredentials(pipeline.destination().connectionId());
        var ct = ConnectorTypeMapper.toCore(conn.getProperties().type());
        return writerRegistry.get(ct)
                .orElseThrow(() -> new IllegalArgumentException("No writer for type: " + ct));
    }

    private ConnectionConfiguration toConfig(Connection conn) {
        var p = conn.getProperties();
        var c = conn.getCredentials();
        return new ConnectionConfiguration(
                ConnectorTypeMapper.toCore(p.type()),
                p.host(), p.port(), p.database(),
                c.username(), c.password(), p.options());
    }
}
