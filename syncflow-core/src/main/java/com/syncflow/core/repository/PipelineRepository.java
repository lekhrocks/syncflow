package com.syncflow.core.repository;

import com.syncflow.core.model.Pipeline;
import com.syncflow.core.model.PipelineStatus;
import java.util.List;
import java.util.Optional;

public interface PipelineRepository {

    Pipeline save(Pipeline pipeline);

    Optional<Pipeline> findById(String id);

    List<Pipeline> findAll();

    List<Pipeline> findByStatus(PipelineStatus status);

    void deleteById(String id);

    boolean existsById(String id);
}
