package com.syncflow.persistence.cdc.repository;

import com.syncflow.persistence.cdc.entity.ActiveCaptureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActiveCaptureRepository extends JpaRepository<ActiveCaptureEntity, String> {

    Optional<ActiveCaptureEntity> findByTenantIdAndPipelineId(String tenantId, String pipelineId);

    java.util.List<ActiveCaptureEntity> findByTenantId(String tenantId);
}
