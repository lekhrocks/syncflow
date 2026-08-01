package com.syncflow.api.pipeline.repository;

import com.syncflow.api.pipeline.entity.PipelineDesignEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineDesignJpaRepository extends JpaRepository<PipelineDesignEntity, String> {
}
