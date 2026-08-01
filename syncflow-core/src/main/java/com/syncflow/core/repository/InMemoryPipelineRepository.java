package com.syncflow.core.repository;

import com.syncflow.core.model.Pipeline;
import com.syncflow.core.model.PipelineStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryPipelineRepository implements PipelineRepository {

    private final Map<String, Pipeline> store = new ConcurrentHashMap<>();

    @Override
    public Pipeline save(Pipeline pipeline) {
        store.put(pipeline.getId(), pipeline);
        return pipeline;
    }

    @Override
    public Optional<Pipeline> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Pipeline> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public List<Pipeline> findByStatus(PipelineStatus status) {
        return store.values().stream()
                .filter(p -> p.getStatus() == status)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }

    @Override
    public boolean existsById(String id) {
        return store.containsKey(id);
    }
}
