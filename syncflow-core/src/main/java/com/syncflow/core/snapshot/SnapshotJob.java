package com.syncflow.core.snapshot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    private SnapshotJob(
            @JsonProperty("id") SnapshotId id,
            @JsonProperty("pipelineId") String pipelineId,
            @JsonProperty("status") SnapshotStatus status,
            @JsonProperty("statistics") SnapshotStatistics statistics,
            @JsonProperty("progress") SnapshotProgress progress,
            @JsonProperty("errors") List<SnapshotError> errors,
            @JsonProperty("createdAt") Instant createdAt) {
        this.id = id;
        this.pipelineId = pipelineId;
        this.status = status;
        this.statistics = statistics;
        this.progress = progress != null ? progress : SnapshotProgress.starting(0);
        this.errors = errors != null ? List.copyOf(errors) : List.of();
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
