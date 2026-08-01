package com.syncflow.core.snapshot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotDomainTest {

    @Test
    void createSnapshotJob() {
        var job = new SnapshotJob("pipeline-1");
        assertNotNull(job.getId());
        assertEquals("pipeline-1", job.getPipelineId());
        assertEquals(SnapshotStatus.PENDING, job.getStatus());
    }

    @Test
    void lifecycleRunningToCompleted() {
        var job = new SnapshotJob("p-1").withRunning();
        assertEquals(SnapshotStatus.RUNNING, job.getStatus());

        var stats = new SnapshotStatistics(100, 100, 5, 5, 0, 0,
                job.getCreatedAt(), java.time.Instant.now(), 1000);
        var done = job.withCompleted(stats);
        assertEquals(SnapshotStatus.COMPLETED, done.getStatus());
        assertEquals(100, done.getStatistics().rowsProcessed());
    }

    @Test
    void lifecycleRunningToFailed() {
        var err = List.of(new SnapshotError("ERR", "connection lost", 3, java.time.Instant.now()));
        var failed = new SnapshotJob("p-1").withRunning().withFailed(err);
        assertEquals(SnapshotStatus.FAILED, failed.getStatus());
        assertEquals("connection lost", failed.getErrors().getFirst().message());
    }

    @Test
    void lifecycleRunningToCancelled() {
        var cancelled = new SnapshotJob("p-1").withRunning().withCancelled();
        assertEquals(SnapshotStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void progressReporting() {
        var p = SnapshotProgress.starting(1000);
        assertEquals(1000, p.estimatedTotalRows());
        assertEquals(0, p.rowsProcessed());
    }

    @Test
    void statisticsRowsPerSecond() {
        var stats = new SnapshotStatistics(1000, 500, 5, 10, 0, 0,
                java.time.Instant.now().minusSeconds(10), java.time.Instant.now(), 10000);
        assertTrue(stats.rowsPerSecond() > 0);
    }

    @Test
    void skipStatistics() {
        var stats = new SnapshotStatistics(0, 0, 0, 0, 0, 0, null, null, 0);
        assertEquals(0, stats.rowsPerSecond());
    }

    @Test
    void checkpointRoundTrip() {
        var cp = new SnapshotCheckpoint("p-1", "users", 5, 500, "cursor-abc");
        assertEquals("users", cp.sourceTable());
        assertEquals(500, cp.rowsProcessed());
    }

    @Test
    void batchInfoRoundTrip() {
        var bi = new BatchInformation(1, 1000, "users", "cursor-1");
        assertEquals(1, bi.batchNumber());
        assertEquals(1000, bi.batchSize());
    }
}
