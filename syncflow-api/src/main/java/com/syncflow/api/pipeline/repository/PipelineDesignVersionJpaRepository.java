package com.syncflow.api.pipeline.repository;

import com.syncflow.api.pipeline.entity.PipelineDesignVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineDesignVersionJpaRepository extends JpaRepository<PipelineDesignVersionEntity, Long> {

    List<PipelineDesignVersionEntity> findByPipelineIdOrderByVersionAsc(String pipelineId);

    Optional<PipelineDesignVersionEntity> findByPipelineIdAndVersion(String pipelineId, int version);

    void deleteByPipelineId(String pipelineId);
}
