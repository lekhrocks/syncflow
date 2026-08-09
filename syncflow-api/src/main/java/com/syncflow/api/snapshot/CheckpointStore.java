package com.syncflow.api.snapshot;

import com.syncflow.api.snapshot.entity.SnapshotCheckpointEntity;
import com.syncflow.api.snapshot.repository.SnapshotCheckpointRepository;
import com.syncflow.core.snapshot.SnapshotCheckpoint;
import com.syncflow.tenant.TenantSupport;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resume checkpoints persisted to PostgreSQL; one row per
 * tenant+pipeline+table.
 */
@Component
public class CheckpointStore {

    private final SnapshotCheckpointRepository repository;

    public CheckpointStore(SnapshotCheckpointRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(SnapshotCheckpoint checkpoint) {
        var entity = repository
                .findByTenantIdAndPipelineIdAndSourceTable(
                        tenantId(), checkpoint.pipelineId(), checkpoint.sourceTable())
                .orElseGet(SnapshotCheckpointEntity::new);
        entity.setTenantId(tenantId());
        entity.setPipelineId(checkpoint.pipelineId());
        entity.setSourceTable(checkpoint.sourceTable());
        entity.setLastBatchNumber(checkpoint.lastBatchNumber());
        entity.setRowsProcessed(checkpoint.rowsProcessed());
        entity.setCursorPos(checkpoint.cursor());
        entity.setUpdatedAt(java.time.Instant.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public SnapshotCheckpoint get(String pipelineId, String sourceTable) {
        return repository
                .findByTenantIdAndPipelineIdAndSourceTable(tenantId(), pipelineId, sourceTable)
                .map(this::toDomain)
                .orElse(null);
    }

    @Transactional
    public void delete(String pipelineId, String sourceTable) {
        repository
                .findByTenantIdAndPipelineIdAndSourceTable(tenantId(), pipelineId, sourceTable)
                .ifPresent(repository::delete);
    }

    @Transactional
    public void deleteAll(String pipelineId) {
        repository.deleteAllForPipeline(tenantId(), pipelineId);
    }

    private SnapshotCheckpoint toDomain(SnapshotCheckpointEntity e) {
        return new SnapshotCheckpoint(e.getPipelineId(), e.getSourceTable(),
                e.getLastBatchNumber(), e.getRowsProcessed(), e.getCursorPos());
    }

    private String tenantId() {
        return TenantSupport.tenantId();
    }
}
