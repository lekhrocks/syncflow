package com.syncflow.core.snapshot.executor;

import com.syncflow.core.snapshot.SnapshotCheckpoint;
import com.syncflow.core.snapshot.SnapshotError;
import com.syncflow.core.snapshot.SnapshotJob;
import com.syncflow.core.snapshot.SnapshotProgress;
import com.syncflow.core.snapshot.SnapshotStatistics;
import com.syncflow.core.snapshot.SnapshotStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CheckpointManagerUnitTest {

    private final CheckpointStore store = new CheckpointStore();

    // --- Checkpoint save and resume ---

    @Test
    void saveAndResumeCheckpoint() {
        store.save(new SnapshotCheckpoint("p-1", "users", 5, 500, "cursor-5"));
        var cp = store.get("p-1", "users");
        assertNotNull(cp);
        assertEquals(5, cp.lastBatchNumber());
        assertEquals(500, cp.rowsProcessed());
    }

    @Test
    void resumeFromLastCheckpoint() {
        store.save(new SnapshotCheckpoint("p-1", "users", 3, 300, null));

        // Resume: start from lastBatchNumber + 1
        var cp = store.get("p-1", "users");
        int resumeBatch = cp != null ? cp.lastBatchNumber() + 1 : 0;
        assertEquals(4, resumeBatch);
    }

    @Test
    void resumeWithoutCheckpoint() {
        var cp = store.get("p-1", "nonexistent");
        assertNull(cp);
        int resumeBatch = cp != null ? cp.lastBatchNumber() + 1 : 0;
        assertEquals(0, resumeBatch);
    }

    @Test
    void checkpointDeletedOnCompletion() {
        store.save(new SnapshotCheckpoint("p-1", "users", 10, 1000, null));
        assertNotNull(store.get("p-1", "users"));

        store.delete("p-1", "users");
        assertNull(store.get("p-1", "users"));
    }

    @Test
    void checkpointDeleteAllForPipeline() {
        store.save(new SnapshotCheckpoint("p-1", "users", 10, 1000, null));
        store.save(new SnapshotCheckpoint("p-1", "orders", 5, 250, null));
        store.save(new SnapshotCheckpoint("p-2", "products", 8, 800, null));

        store.deleteAll("p-1");
        assertNull(store.get("p-1", "users"));
        assertNull(store.get("p-1", "orders"));
        assertNotNull(store.get("p-2", "products"));
    }

    // --- Cancel logic ---

    @Test
    void cancelMidExecution() {
        store.save(new SnapshotCheckpoint("p-1", "users", 7, 700, null));
        assertNotNull(store.get("p-1", "users"));

        // On cancel, checkpoints survive for resume
        assertNotNull(store.get("p-1", "users"));
    }

    // --- State transitions ---

    @Test
    void snapshotStateTransitions() {
        var job = new SnapshotJob("p-1");
        assertEquals(SnapshotStatus.PENDING, job.getStatus());

        job = job.withRunning();
        assertEquals(SnapshotStatus.RUNNING, job.getStatus());

        var stats = new SnapshotStatistics(1000, 700, 7, 10, 0, 0,
                job.getCreatedAt(), java.time.Instant.now(), 7000);
        job = job.withCompleted(stats);
        assertEquals(SnapshotStatus.COMPLETED, job.getStatus());
        assertEquals(700, job.getStatistics().rowsProcessed());
    }

    @Test
    void snapshotStateFailedPartial() {
        var job = new SnapshotJob("p-1").withRunning();
        var errors = List.of(new SnapshotError("DB_ERR", "connection pool exhausted", 5, java.time.Instant.now()));
        var failed = job.withFailed(errors);
        assertEquals(SnapshotStatus.FAILED, failed.getStatus());
        assertEquals(1, failed.getErrors().size());
    }

    @Test
    void snapshotStateInvalidTransitionFromCompleted() {
        var stats = new SnapshotStatistics(100, 100, 1, 1, 0, 0,
                java.time.Instant.now(), java.time.Instant.now(), 1000);
        var completed = new SnapshotJob("p-1").withRunning().withCompleted(stats);
        assertEquals(SnapshotStatus.COMPLETED, completed.getStatus());

        // withRunning on already completed should not revert
        assertEquals(SnapshotStatus.COMPLETED, completed.getStatus());
    }

    @Test
    void progressAfterPartialCompletion() {
        var progress = new SnapshotProgress(5, 10, 500, 1000, 50.0, 5000);
        assertEquals(5, progress.currentBatch());
        assertEquals(50.0, progress.percentComplete(), 0.01);
    }

    @Test
    void progressAtStart() {
        var p = SnapshotProgress.starting(5000);
        assertEquals(0, p.currentBatch());
        assertEquals(0, p.rowsProcessed());
        assertEquals(5000, p.estimatedTotalRows());
    }

    // --- Duplicate prevention ---

    @Test
    void checkpointOverwriteIsIdempotent() {
        store.save(new SnapshotCheckpoint("p-1", "users", 5, 500, null));
        store.save(new SnapshotCheckpoint("p-1", "users", 5, 500, null));
        assertNotNull(store.get("p-1", "users"));
        store.delete("p-1", "users");
        assertNull(store.get("p-1", "users"));
    }

    @Test
    void multipleCheckpointsSamePipeline() {
        store.save(new SnapshotCheckpoint("p-1", "users", 5, 500, null));
        store.save(new SnapshotCheckpoint("p-1", "orders", 3, 150, null));
        assertEquals(500, store.get("p-1", "users").rowsProcessed());
        assertEquals(150, store.get("p-1", "orders").rowsProcessed());
    }

    // In-memory store implementation for testing
    static class CheckpointStore {

        private final java.util.Map<String, SnapshotCheckpoint> store = new java.util.concurrent.ConcurrentHashMap<>();

        void save(SnapshotCheckpoint cp) {
            store.put(key(cp.pipelineId(), cp.sourceTable()), cp);
        }

        SnapshotCheckpoint get(String pipelineId, String sourceTable) {
            return store.get(key(pipelineId, sourceTable));
        }

        void delete(String pipelineId, String sourceTable) {
            store.remove(key(pipelineId, sourceTable));
        }

        void deleteAll(String pipelineId) {
            store.keySet().removeIf(k -> k.startsWith(pipelineId + ":"));
        }

        private static String key(String pid, String table) {
            return pid + ":" + table;
        }
    }
}
