package com.syncflow.api.pipeline.repository;

import com.syncflow.api.pipeline.mapper.JsonMapper;
import com.syncflow.api.pipeline.mapper.PipelineEntityMapper;
import com.syncflow.core.model.Pipeline;
import com.syncflow.core.model.PipelineStatus;
import com.syncflow.core.repository.PipelineRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link PipelineRepository}.
 * Replaces {@code InMemoryPipelineRepository}.
 * Annotated {@code @Primary} so Spring injects this bean over the in-memory
 * one.
 */
@Primary
@Repository
public class PipelineRepositoryAdapter implements PipelineRepository {

    private final PipelineJpaRepository jpa;
    private final PipelineEntityMapper mapper;
    private final JsonMapper jsonMapper;

    public PipelineRepositoryAdapter(PipelineJpaRepository jpa,
            PipelineEntityMapper mapper,
            JsonMapper jsonMapper) {
        this.jpa = jpa;
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Pipeline save(Pipeline pipeline) {
        jpa.save(mapper.toEntity(pipeline, jsonMapper));
        return pipeline;
    }

    @Override
    public Optional<Pipeline> findById(String id) {
        return jpa.findById(id).map(e -> mapper.toDomain(e, jsonMapper));
    }

    @Override
    public List<Pipeline> findAll() {
        return jpa.findAll().stream().map(e -> mapper.toDomain(e, jsonMapper)).toList();
    }

    @Override
    public List<Pipeline> findByStatus(PipelineStatus status) {
        return jpa.findByStatus(status.name()).stream()
                .map(e -> mapper.toDomain(e, jsonMapper))
                .toList();
    }

    @Override
    public void deleteById(String id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean existsById(String id) {
        return jpa.existsById(id);
    }
}
