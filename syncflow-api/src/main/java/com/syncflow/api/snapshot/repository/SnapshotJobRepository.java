package com.syncflow.api.snapshot.repository;

import com.syncflow.api.snapshot.entity.SnapshotJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SnapshotJobRepository extends JpaRepository<SnapshotJobEntity, String> {

    List<SnapshotJobEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<SnapshotJobEntity> findByTenantIdAndPipelineIdOrderByCreatedAtDesc(String tenantId, String pipelineId);
}
