package com.syncflow.persistence.snapshot.repository;

import com.syncflow.persistence.snapshot.entity.SnapshotCheckpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SnapshotCheckpointRepository extends JpaRepository<SnapshotCheckpointEntity, Long> {

    Optional<SnapshotCheckpointEntity> findByTenantIdAndPipelineIdAndSourceTableAndChunkIndex(
            String tenantId, String pipelineId, String sourceTable, int chunkIndex);

    @Modifying
    @Query(value = """
            DELETE FROM snapshot_checkpoints WHERE tenant_id = :tenantId AND pipeline_id = :pipelineId
            """, nativeQuery = true)
    void deleteAllForPipeline(@Param("tenantId") String tenantId, @Param("pipelineId") String pipelineId);
}
