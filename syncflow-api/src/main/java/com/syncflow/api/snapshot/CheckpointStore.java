package com.syncflow.api.snapshot;

import com.syncflow.persistence.snapshot.entity.SnapshotCheckpointEntity;
import com.syncflow.persistence.snapshot.repository.SnapshotCheckpointRepository;
import com.syncflow.core.snapshot.SnapshotCheckpoint;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resume checkpoints persisted to PostgreSQL; one row per
 * tenant+pipeline+table+chunk.
 *
 * <p>
 * The tenant is passed explicitly rather than read from a ThreadLocal.
 * Snapshot workers run on pool/virtual threads that never set
 * {@code TenantContextHolder}; keying on the ThreadLocal there would resolve
 * to {@code TenantId.DEFAULT} and attribute every tenant's checkpoints to the
 * default tenant (cross-tenant cursor reuse on resume).
 */
@Component
public class CheckpointStore {

    private final SnapshotCheckpointRepository repository;

    public CheckpointStore(SnapshotCheckpointRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(String tenantId, SnapshotCheckpoint checkpoint) {
        var entity = find(tenantId, checkpoint.pipelineId(), checkpoint.sourceTable(), checkpoint.chunkIndex())
                .orElseGet(SnapshotCheckpointEntity::new);
        entity.setTenantId(tenantId);
        entity.setPipelineId(checkpoint.pipelineId());
        entity.setSourceTable(checkpoint.sourceTable());
        entity.setChunkIndex(checkpoint.chunkIndex());
        entity.setLastBatchNumber(checkpoint.lastBatchNumber());
        entity.setRowsProcessed(checkpoint.rowsProcessed());
        entity.setCursorPos(checkpoint.cursor());
        entity.setUpdatedAt(java.time.Instant.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public SnapshotCheckpoint get(String tenantId, String pipelineId, String sourceTable, int chunkIndex) {
        return find(tenantId, pipelineId, sourceTable, chunkIndex)
                .map(this::toDomain)
                .orElse(null);
    }

    @Transactional
    public void deleteAll(String tenantId, String pipelineId) {
        repository.deleteAllForPipeline(tenantId, pipelineId);
    }

    private Optional<SnapshotCheckpointEntity> find(String tenantId, String pipelineId,
            String sourceTable, int chunkIndex) {
        return repository.findByTenantIdAndPipelineIdAndSourceTableAndChunkIndex(
                tenantId, pipelineId, sourceTable, chunkIndex);
    }

    private SnapshotCheckpoint toDomain(SnapshotCheckpointEntity e) {
        return new SnapshotCheckpoint(e.getPipelineId(), e.getSourceTable(), e.getChunkIndex(),
                e.getLastBatchNumber(), e.getRowsProcessed(), e.getCursorPos());
    }
}
