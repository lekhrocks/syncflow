package com.syncflow.persistence.pipeline.repository;

import com.syncflow.persistence.pipeline.entity.PipelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineJpaRepository extends JpaRepository<PipelineEntity, String> {

    List<PipelineEntity> findByStatus(String status);
}
