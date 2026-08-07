package com.syncflow.api.pipeline.repository;

import com.syncflow.api.pipeline.entity.PipelineDesignEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineDesignJpaRepository extends JpaRepository<PipelineDesignEntity, String> {

    List<PipelineDesignEntity> findByTenantId(String tenantId);

    Optional<PipelineDesignEntity> findByIdAndTenantId(String id, String tenantId);
}
