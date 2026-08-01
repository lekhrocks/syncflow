package com.syncflow.core.snapshot;

import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
public class SnapshotJob {

    private final SnapshotId id;
    private final String pipelineId;
    private final SnapshotStatus status;
    private final SnapshotStatistics statistics;
    private final SnapshotProgress progress;
    private final List<SnapshotError> errors;
    private final Instant createdAt;

    public SnapshotJob(String pipelineId) {
        this.id = SnapshotId.generate();
        this.pipelineId = pipelineId;
        this.status = SnapshotStatus.PENDING;
        this.statistics = null;
        this.progress = SnapshotProgress.starting(0);
        this.errors = List.of();
        this.createdAt = Instant.now();
    }

    private SnapshotJob(SnapshotId id, String pipelineId, SnapshotStatus status,
            SnapshotStatistics statistics, SnapshotProgress progress,
            List<SnapshotError> errors, Instant createdAt) {
        this.id = id;
        this.pipelineId = pipelineId;
        this.status = status;
        this.statistics = statistics;
        this.progress = progress;
        this.errors = errors;
        this.createdAt = createdAt;
    }

    public SnapshotJob withRunning() {
        return new SnapshotJob(id, pipelineId, SnapshotStatus.RUNNING, statistics, progress, errors, createdAt);
    }

    public SnapshotJob withCompleted(SnapshotStatistics stats) {
        return new SnapshotJob(id, pipelineId, SnapshotStatus.COMPLETED, stats, progress, errors, createdAt);
    }

    public SnapshotJob withFailed(List<SnapshotError> errs) {
        return new SnapshotJob(id, pipelineId, SnapshotStatus.FAILED, statistics, progress, errs, createdAt);
    }

    public SnapshotJob withProgress(SnapshotProgress p) {
        return new SnapshotJob(id, pipelineId, status, statistics, p, errors, createdAt);
    }

    public SnapshotJob withCancelled() {
        return new SnapshotJob(id, pipelineId, SnapshotStatus.CANCELLED, statistics, progress, errors, createdAt);
    }
}
