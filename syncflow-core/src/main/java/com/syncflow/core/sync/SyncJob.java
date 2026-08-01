package com.syncflow.core.sync;

import com.syncflow.core.cdc.CDCEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public class SyncJob {

    private final String id;
    private final String pipelineId;
    private final SyncState state;
    private final SyncStatistics statistics;
    private final List<CDCEvent> processed;
    private final Instant createdAt;

    public SyncJob(String pipelineId) {
        this.id = UUID.randomUUID().toString();
        this.pipelineId = pipelineId;
        this.state = SyncState.INITIALIZING;
        this.statistics = new SyncStatistics(0, 0, 0, 0, 0, 0, 0);
        this.processed = List.of();
        this.createdAt = Instant.now();
    }

    private SyncJob(String id, String pipelineId, SyncState state,
            SyncStatistics stats, List<CDCEvent> processed, Instant createdAt) {
        this.id = id;
        this.pipelineId = pipelineId;
        this.state = state;
        this.statistics = stats;
        this.processed = processed;
        this.createdAt = createdAt;
    }

    public SyncJob withRunning() {
        return new SyncJob(id, pipelineId, SyncState.RUNNING, statistics, processed, createdAt);
    }

    public SyncJob withStopped() {
        return new SyncJob(id, pipelineId, SyncState.STOPPED, statistics, processed, createdAt);
    }

    public SyncJob withStatistics(SyncStatistics stats) {
        return new SyncJob(id, pipelineId, state, stats, processed, createdAt);
    }

    public SyncJob withCompleted() {
        return new SyncJob(id, pipelineId, SyncState.COMPLETED, statistics, processed, createdAt);
    }

    public SyncJob withFailed() {
        return new SyncJob(id, pipelineId, SyncState.FAILED, statistics, processed, createdAt);
    }
}
