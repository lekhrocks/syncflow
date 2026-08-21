package com.syncflow.persistence.sync.repository;

import com.syncflow.persistence.sync.entity.SyncJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncJobRepository extends JpaRepository<SyncJobEntity, String> {

    Optional<SyncJobEntity> findByTenantIdAndPipelineId(String tenantId, String pipelineId);

    List<SyncJobEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
